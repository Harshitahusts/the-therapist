from fastapi import APIRouter, Depends, HTTPException, Response
from sqlalchemy.orm import Session

from ..ai import AIProvider
from ..auth import current_user, get_db
from ..config import Settings
from ..deps import get_ai, get_settings_dep
from ..memory import MemoryRejected, MemoryService
from ..models import Memory, User
from ..schemas import MemoryIn, MemoryOut

router = APIRouter(prefix="/v1/memories", tags=["memories"])


def _out(m: Memory) -> MemoryOut:
    return MemoryOut(
        id=m.id, category=m.category, content=m.content, confidence=m.confidence,
        source=m.source, created_at=m.created_at, updated_at=m.updated_at,
    )


def _svc(db: Session = Depends(get_db), ai: AIProvider = Depends(get_ai),
         settings: Settings = Depends(get_settings_dep)) -> MemoryService:
    return MemoryService(db, ai, settings)


@router.get("", response_model=list[MemoryOut])
def list_memories(user: User = Depends(current_user), svc: MemoryService = Depends(_svc)):
    return [_out(m) for m in svc.list(user.id)]


@router.post("", response_model=MemoryOut, status_code=201)
def add_memory(body: MemoryIn, user: User = Depends(current_user), svc: MemoryService = Depends(_svc)):
    if not user.settings.memory_enabled:
        raise HTTPException(409, "Memory is turned off")
    try:
        memory, _ = svc.save(user.id, body.content, category=body.category, confidence=1.0, source="user")
    except MemoryRejected as exc:
        raise HTTPException(422, f"Memory rejected: {exc}")
    return _out(memory)


@router.put("/{memory_id}", response_model=MemoryOut)
def edit_memory(memory_id: str, body: MemoryIn, user: User = Depends(current_user),
                svc: MemoryService = Depends(_svc)):
    try:
        memory = svc.update(user.id, memory_id, body.content, body.category)
    except MemoryRejected as exc:
        raise HTTPException(422, f"Memory rejected: {exc}")
    if memory is None:
        raise HTTPException(404, "Not found")
    return _out(memory)


@router.delete("/{memory_id}", status_code=204)
def delete_memory(memory_id: str, user: User = Depends(current_user), svc: MemoryService = Depends(_svc)):
    if not svc.delete(user.id, memory_id):
        raise HTTPException(404, "Not found")
    return Response(status_code=204)


@router.delete("", status_code=204)
def delete_all_memories(user: User = Depends(current_user), svc: MemoryService = Depends(_svc)):
    """'Forget everything about me': memories and conversation summaries."""
    svc.delete_all(user.id)
    return Response(status_code=204)

import logging
import threading
import time
from collections import defaultdict, deque
from datetime import datetime, timezone

from fastapi import APIRouter, BackgroundTasks, Depends, HTTPException, Request
from sqlalchemy.orm import Session

from .. import tools
from ..ai import AIProvider, AIProviderError
from ..auth import current_user, get_db
from ..config import Settings
from ..deps import get_ai, get_settings_dep
from ..memory import MemoryService
from ..models import Conversation, SafetyEvent, User
from ..realtime import build_instructions, build_session_config
from ..safety import RiskLevel, classify, guidance_for, resources_for
from ..schemas import EndIn, EndOut, RealtimeSessionOut, ToolCallIn, TurnIn, TurnOut

log = logging.getLogger(__name__)
router = APIRouter(prefix="/v1", tags=["conversations"])


class RateLimiter:
    """Per-user sliding window, in memory. Enough for a single-instance personal deployment."""

    def __init__(self):
        self._hits: dict[str, deque] = defaultdict(deque)
        self._lock = threading.Lock()

    def allow(self, key: str, limit: int, window: float = 3600.0) -> bool:
        now = time.monotonic()
        with self._lock:
            q = self._hits[key]
            while q and now - q[0] > window:
                q.popleft()
            if len(q) >= limit:
                return False
            q.append(now)
            return True


def _conversation(db: Session, user: User, conversation_id: str) -> Conversation:
    conv = db.get(Conversation, conversation_id)
    # Same response for "missing" and "someone else's" so ids can't be probed.
    if conv is None or conv.user_id != user.id:
        raise HTTPException(404, "Conversation not found")
    return conv


@router.post("/realtime/session", response_model=RealtimeSessionOut)
def create_realtime_session(
    request: Request,
    user: User = Depends(current_user),
    db: Session = Depends(get_db),
    ai: AIProvider = Depends(get_ai),
    settings: Settings = Depends(get_settings_dep),
):
    if not request.app.state.limiter.allow(user.id, settings.realtime_sessions_per_hour):
        raise HTTPException(429, "Too many conversations started in the last hour. Please take a short break.")
    instructions = build_instructions(user, MemoryService(db, ai, settings), settings)
    session_config = build_session_config(instructions, settings)
    try:
        secret = ai.create_realtime_secret(session_config)
    except AIProviderError as exc:
        log.error("realtime session failed: %s", exc)
        raise HTTPException(503, "The voice service is unavailable right now.")
    conv = Conversation(user_id=user.id)
    db.add(conv)
    db.commit()
    return RealtimeSessionOut(
        conversation_id=conv.id,
        client_secret=secret.value,
        expires_at=secret.expires_at,
        model=secret.model,
        calls_url=f"{settings.openai_base_url.rstrip('/')}/realtime/calls",
    )


@router.post("/conversations/{conversation_id}/turns", response_model=TurnOut)
def check_turn(
    conversation_id: str,
    body: TurnIn,
    user: User = Depends(current_user),
    db: Session = Depends(get_db),
    ai: AIProvider = Depends(get_ai),
    settings: Settings = Depends(get_settings_dep),
):
    """Safety check on one user utterance. The text is classified and discarded."""
    conv = _conversation(db, user, conversation_id)
    assessment = classify(body.text)
    if assessment.level < RiskLevel.HIGH:
        assessment = classify(body.text, ai.moderate(body.text))
    if assessment.level >= RiskLevel.MODERATE:
        db.add(SafetyEvent(user_id=user.id, conversation_id=conv.id,
                           level=assessment.level.name, categories=assessment.categories))
        db.commit()
    region = user.settings.crisis_region or settings.default_crisis_region
    return TurnOut(
        level=assessment.level.name,
        categories=assessment.categories,
        guidance=guidance_for(assessment, region),
        # For HIGH/IMMEDIATE the app cancels whatever the model started saying
        # and re-asks it with the safety guidance in place.
        interrupt=assessment.level >= RiskLevel.HIGH,
        resources=resources_for(region) if assessment.show_resources else None,
    )


@router.post("/conversations/{conversation_id}/tools/{tool_name}")
def call_tool(
    conversation_id: str,
    tool_name: str,
    body: ToolCallIn,
    user: User = Depends(current_user),
    db: Session = Depends(get_db),
    ai: AIProvider = Depends(get_ai),
    settings: Settings = Depends(get_settings_dep),
):
    conv = _conversation(db, user, conversation_id)
    if conv.ended_at is not None:
        raise HTTPException(409, "Conversation has ended")
    try:
        return tools.execute(tool_name, body.arguments, user=user, conversation=conv, db=db, ai=ai, settings=settings)
    except tools.UnknownTool:
        raise HTTPException(404, "Unknown tool")


def _summarize_in_background(app_state, conversation_id: str, transcript: list[dict]) -> None:
    db = app_state.db.SessionLocal()
    try:
        conv = db.get(Conversation, conversation_id)
        if conv is not None:
            MemoryService(db, app_state.ai, app_state.settings).summarize_conversation(conv, transcript)
    except Exception:  # never let a background failure surface; nothing to retry against
        log.exception("conversation post-processing failed")
    finally:
        db.close()


@router.post("/conversations/{conversation_id}/end", response_model=EndOut)
def end_conversation(
    conversation_id: str,
    body: EndIn,
    request: Request,
    background: BackgroundTasks,
    user: User = Depends(current_user),
    db: Session = Depends(get_db),
):
    conv = _conversation(db, user, conversation_id)
    if conv.ended_at is not None:
        return EndOut(conversation_id=conv.id, duration_seconds=conv.duration_seconds or 0, processing=False)
    now = datetime.now(timezone.utc)
    started = conv.started_at if conv.started_at.tzinfo else conv.started_at.replace(tzinfo=timezone.utc)
    conv.ended_at = now
    conv.duration_seconds = max(0, int((now - started).total_seconds()))
    db.commit()
    processing = bool(user.settings.memory_enabled and body.transcript)
    if processing:
        transcript = [t.model_dump() for t in body.transcript]
        background.add_task(_summarize_in_background, request.app.state, conv.id, transcript)
    return EndOut(conversation_id=conv.id, duration_seconds=conv.duration_seconds, processing=processing)

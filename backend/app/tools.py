"""Server-side execution of the voice agent's function tools."""

from __future__ import annotations

import logging
from typing import Any

from sqlalchemy.orm import Session

from . import knowledge
from .ai import AIProvider, AIProviderError
from .config import Settings
from .memory import MemoryRejected, MemoryService
from .models import Conversation, User

log = logging.getLogger(__name__)

# How similar a stored memory must be to "what the user wants forgotten/updated"
# before we act on it. Kept conservative so we never delete the wrong thing.
MATCH_THRESHOLD = 0.45


class UnknownTool(KeyError):
    pass


def _arg(args: dict, key: str, max_len: int = 500) -> str:
    return str(args.get(key) or "").strip()[:max_len]


def execute(
    name: str,
    args: dict[str, Any],
    *,
    user: User,
    conversation: Conversation,
    db: Session,
    ai: AIProvider,
    settings: Settings,
) -> dict[str, Any]:
    mem = MemoryService(db, ai, settings)
    memory_on = bool(user.settings and user.settings.memory_enabled)

    if name == "get_user_profile":
        p = user.profile
        return {
            "name": p.name,
            "preferred_name": p.preferred_name,
            "timezone": p.timezone,
            "language": p.language,
            "preferences": p.preferences or {},
        }

    if name == "get_relevant_memories":
        if not memory_on:
            return {"memories": [], "note": "Memory is turned off by the user."}
        hits = mem.relevant(user.id, _arg(args, "query"), limit=6)
        return {
            "memories": [
                {"category": h.memory.category, "content": h.memory.content,
                 "remembered_on": h.memory.created_at.date().isoformat()}
                for h in hits
            ]
        }

    if name == "get_recent_conversation_summaries":
        if not memory_on:
            return {"summaries": [], "note": "Memory is turned off by the user."}
        return {
            "summaries": [
                {"date": s.created_at.date().isoformat(), "summary": s.summary,
                 "emotional_context": s.emotional_context, "follow_up": s.follow_up}
                for s in mem.recent_summaries(user.id, 5)
            ]
        }

    if name == "save_memory":
        if not memory_on:
            return {"saved": False, "reason": "Memory is turned off. The user can turn it on in Settings."}
        try:
            memory, created = mem.save(
                user.id, _arg(args, "content"), category=_arg(args, "category", 32),
                source="voice_tool", conversation_id=conversation.id,
            )
        except MemoryRejected as exc:
            reason = "That looks like sensitive information, which is never stored." if str(exc) == "sensitive" else "Nothing to save."
            return {"saved": False, "reason": reason}
        return {"saved": True, "updated_existing": not created}

    if name in ("update_memory", "delete_memory"):
        about = _arg(args, "about")
        if not about:
            return {"ok": False, "reason": "Say what the memory is about."}
        hits = [h for h in mem.relevant(user.id, about, limit=1) if h.score is not None and h.score >= MATCH_THRESHOLD]
        if not hits:
            return {"ok": False, "reason": "No matching memory found."}
        target = hits[0].memory
        if name == "delete_memory":
            mem.delete(user.id, target.id)
            return {"ok": True, "forgotten": target.content}
        if not memory_on:
            return {"ok": False, "reason": "Memory is turned off."}
        try:
            mem.update(user.id, target.id, _arg(args, "new_content"))
        except MemoryRejected:
            return {"ok": False, "reason": "That can't be stored."}
        return {"ok": True}

    if name == "retrieve_knowledge":
        query = _arg(args, "query")
        if not query:
            return {"passages": []}
        try:
            passages = knowledge.retrieve(db, ai, query, k=3)
        except AIProviderError:
            return {"passages": [], "note": "Library unavailable right now."}
        return {"passages": passages}

    raise UnknownTool(name)

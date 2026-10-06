"""Long-term memory: what is worth keeping between conversations, per user."""

from __future__ import annotations

import logging
import re
from dataclasses import dataclass

from sqlalchemy import Float, bindparam, delete, select
from sqlalchemy.orm import Session

from .ai import AIProvider, AIProviderError, cosine
from .config import Settings
from .db import Embedding
from .models import (
    MEMORY_CATEGORIES,
    Conversation,
    ConversationSummary,
    Memory,
    MemoryEmbedding,
    UserSettings,
)
from .prompts import SUMMARY_SYSTEM_PROMPT

log = logging.getLogger(__name__)

MAX_MEMORY_CHARS = 500

_SENSITIVE = [
    re.compile(r"\b(password|passcode|passwd|pin code|pin number|my pin|otp|one[- ]time code)\b", re.I),
    re.compile(r"\b(api[ _-]?key|secret key|access token|bearer token|private key)\b", re.I),
    re.compile(r"\bsk-[A-Za-z0-9_-]{10,}|\bgh[pousr]_[A-Za-z0-9]{20,}|\bAKIA[0-9A-Z]{16}\b"),
    re.compile(r"\b(?:\d[ -]?){13,19}\b"),  # card-like / long account numbers
    re.compile(r"\b(cvv|cvc|card number|iban|routing number|account number|sort code)\b", re.I),
    re.compile(r"\b(ssn|social security number|aadhaar|passport number)\b", re.I),
]


def looks_sensitive(text: str) -> bool:
    return any(p.search(text) for p in _SENSITIVE)


def normalize_category(category: str | None) -> str:
    c = (category or "other").strip().lower().replace(" ", "_")
    return c if c in MEMORY_CATEGORIES else "other"


class MemoryRejected(ValueError):
    pass


@dataclass
class ScoredMemory:
    memory: Memory
    score: float | None


class MemoryService:
    def __init__(self, db: Session, ai: AIProvider, settings: Settings):
        self.db = db
        self.ai = ai
        self.settings = settings

    # --- settings ------------------------------------------------------------
    def memory_enabled(self, user_id: str) -> bool:
        s = self.db.get(UserSettings, user_id)
        return bool(s and s.memory_enabled)

    # --- CRUD ----------------------------------------------------------------
    def list(self, user_id: str) -> list[Memory]:
        return list(
            self.db.scalars(
                select(Memory).where(Memory.user_id == user_id).order_by(Memory.updated_at.desc())
            )
        )

    def get(self, user_id: str, memory_id: str) -> Memory | None:
        m = self.db.get(Memory, memory_id)
        return m if m is not None and m.user_id == user_id else None

    def _embed_one(self, text: str) -> list[float] | None:
        try:
            return self.ai.embed([text])[0]
        except AIProviderError:
            log.warning("embedding failed; memory stored without vector")
            return None

    def save(
        self,
        user_id: str,
        content: str,
        category: str | None = None,
        confidence: float = 0.8,
        source: str = "user",
        conversation_id: str | None = None,
    ) -> tuple[Memory, bool]:
        """Returns (memory, created). Near-duplicates update the existing memory."""
        content = " ".join(content.split())[:MAX_MEMORY_CHARS]
        if not content:
            raise MemoryRejected("empty")
        if looks_sensitive(content):
            raise MemoryRejected("sensitive")
        vector = self._embed_one(content)

        if vector is not None:
            nearest = self._nearest(user_id, vector, limit=1)
            if nearest and nearest[0].score is not None and nearest[0].score >= self.settings.memory_dedup_similarity:
                existing = nearest[0].memory
                existing.content = content
                existing.confidence = max(existing.confidence, confidence)
                existing.category = normalize_category(category or existing.category)
                existing.embedding.embedding = vector
                self.db.commit()
                return existing, False

        memory = Memory(
            user_id=user_id,
            content=content,
            category=normalize_category(category),
            confidence=max(0.0, min(1.0, confidence)),
            source=source,
            conversation_id=conversation_id,
        )
        self.db.add(memory)
        self.db.flush()
        if vector is not None:
            self.db.add(
                MemoryEmbedding(
                    memory_id=memory.id, user_id=user_id, model=self.ai.embedding_model, embedding=vector
                )
            )
        self.db.commit()
        return memory, True

    def update(self, user_id: str, memory_id: str, content: str, category: str | None = None) -> Memory | None:
        memory = self.get(user_id, memory_id)
        if memory is None:
            return None
        content = " ".join(content.split())[:MAX_MEMORY_CHARS]
        if not content:
            raise MemoryRejected("empty")
        if looks_sensitive(content):
            raise MemoryRejected("sensitive")
        memory.content = content
        if category:
            memory.category = normalize_category(category)
        vector = self._embed_one(content)
        if vector is not None:
            if memory.embedding is None:
                self.db.add(
                    MemoryEmbedding(
                        memory_id=memory.id, user_id=user_id, model=self.ai.embedding_model, embedding=vector
                    )
                )
            else:
                memory.embedding.embedding = vector
        self.db.commit()
        return memory

    def delete(self, user_id: str, memory_id: str) -> bool:
        memory = self.get(user_id, memory_id)
        if memory is None:
            return False
        self.db.delete(memory)
        self.db.commit()
        return True

    def delete_all(self, user_id: str) -> int:
        n = self.db.execute(delete(Memory).where(Memory.user_id == user_id)).rowcount
        self.db.execute(delete(ConversationSummary).where(ConversationSummary.user_id == user_id))
        self.db.commit()
        return n or 0

    # --- retrieval -------------------------------------------------------------
    def _nearest(self, user_id: str, vector: list[float], limit: int) -> list[ScoredMemory]:
        dialect = self.db.get_bind().dialect.name
        if dialect == "postgresql":
            q = bindparam("q", vector, type_=Embedding(len(vector)))
            distance = MemoryEmbedding.embedding.op("<=>", return_type=Float)(q)
            rows = self.db.execute(
                select(Memory, distance)
                .join(MemoryEmbedding, MemoryEmbedding.memory_id == Memory.id)
                .where(MemoryEmbedding.user_id == user_id, Memory.user_id == user_id)
                .order_by(distance)
                .limit(limit)
            ).all()
            return [ScoredMemory(m, 1.0 - float(d)) for m, d in rows]

        rows = self.db.execute(
            select(Memory, MemoryEmbedding.embedding)
            .join(MemoryEmbedding, MemoryEmbedding.memory_id == Memory.id)
            .where(MemoryEmbedding.user_id == user_id, Memory.user_id == user_id)
        ).all()
        scored = [ScoredMemory(m, cosine(vector, emb)) for m, emb in rows]
        scored.sort(key=lambda s: s.score or 0.0, reverse=True)
        return scored[:limit]

    def relevant(self, user_id: str, query: str | None, limit: int = 6, min_score: float = 0.2) -> list[ScoredMemory]:
        if query:
            vector = self._embed_one(query)
            if vector is not None:
                hits = [s for s in self._nearest(user_id, vector, limit) if (s.score or 0) >= min_score]
                if hits:
                    return hits
        recent = self.db.scalars(
            select(Memory).where(Memory.user_id == user_id).order_by(Memory.updated_at.desc()).limit(limit)
        )
        return [ScoredMemory(m, None) for m in recent]

    def recent_summaries(self, user_id: str, limit: int = 3) -> list[ConversationSummary]:
        return list(
            self.db.scalars(
                select(ConversationSummary)
                .where(ConversationSummary.user_id == user_id)
                .order_by(ConversationSummary.created_at.desc())
                .limit(limit)
            )
        )

    # --- end-of-conversation processing ------------------------------------------
    def summarize_conversation(self, conversation: Conversation, transcript: list[dict]) -> dict:
        """Creates the summary and extracts memories. The transcript is not stored."""
        user_id = conversation.user_id
        result = {"summary_saved": False, "memories_saved": 0}
        if not self.memory_enabled(user_id):
            return result
        lines = []
        for turn in transcript:
            role = "USER" if turn.get("role") == "user" else "COMPANION"
            text = " ".join(str(turn.get("text", "")).split())
            if text:
                lines.append(f"{role}: {text}")
        if not any(l.startswith("USER: ") for l in lines):
            return result
        body = "\n".join(lines)[-self.settings.max_transcript_chars:]
        existing = "\n".join(f"- {m.content}" for m in self.list(user_id)[:40]) or "(none)"
        try:
            data = self.ai.complete_json(
                SUMMARY_SYSTEM_PROMPT,
                f"EXISTING MEMORIES:\n{existing}\n\nCONVERSATION:\n{body}",
            )
        except AIProviderError:
            log.warning("summary generation failed for a conversation")
            return result

        summary_text = str(data.get("summary") or "").strip()
        if summary_text and not looks_sensitive(summary_text):
            points = [str(p)[:300] for p in (data.get("important_points") or []) if p][:5]
            points = [p for p in points if not looks_sensitive(p)]
            row = self.db.scalar(
                select(ConversationSummary).where(ConversationSummary.conversation_id == conversation.id)
            )
            if row is None:
                row = ConversationSummary(conversation_id=conversation.id, user_id=user_id, summary=summary_text)
                self.db.add(row)
            row.summary = summary_text[:2000]
            row.important_points = points
            row.emotional_context = (str(data["emotional_context"])[:200] if data.get("emotional_context") else None)
            row.follow_up = (str(data["follow_up"])[:500] if data.get("follow_up") else None)
            self.db.commit()
            result["summary_saved"] = True

        for item in (data.get("memories") or [])[:5]:
            if not isinstance(item, dict) or not item.get("content"):
                continue
            try:
                confidence = float(item.get("confidence", 0.7))
            except (TypeError, ValueError):
                confidence = 0.7
            if confidence < 0.5:
                continue
            try:
                self.save(
                    user_id,
                    str(item["content"]),
                    category=item.get("category"),
                    confidence=confidence,
                    source="summary_extraction",
                    conversation_id=conversation.id,
                )
                result["memories_saved"] += 1
            except MemoryRejected:
                continue
        return result

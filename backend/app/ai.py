"""Thin, swappable wrappers around the AI services the backend needs.

The OpenAI implementation talks to the REST API with httpx (no SDK) so every
request that leaves the server is visible here. `OfflineProvider` is a
deterministic stand-in for local development and tests: it needs no key and
makes no network calls, but cannot do voice.
"""

from __future__ import annotations

import hashlib
import json
import math
import re
from dataclasses import dataclass, field
from typing import Any, Protocol

import httpx

from .config import Settings


class AIProviderError(RuntimeError):
    pass


@dataclass
class ModerationResult:
    flagged: bool = False
    categories: dict[str, bool] = field(default_factory=dict)
    scores: dict[str, float] = field(default_factory=dict)


@dataclass
class RealtimeSecret:
    value: str
    expires_at: int | None
    model: str


class AIProvider(Protocol):
    embedding_model: str

    def embed(self, texts: list[str]) -> list[list[float]]: ...

    def complete_json(self, system: str, user: str) -> dict[str, Any]: ...

    def moderate(self, text: str) -> ModerationResult | None: ...

    def create_realtime_secret(self, session: dict[str, Any]) -> RealtimeSecret: ...


class OpenAIProvider:
    def __init__(self, settings: Settings, client: httpx.Client | None = None):
        if not settings.openai_api_key:
            raise AIProviderError("OPENAI_API_KEY is not set")
        self.settings = settings
        self.embedding_model = settings.openai_embedding_model
        self.client = client or httpx.Client(
            base_url=settings.openai_base_url,
            headers={"Authorization": f"Bearer {settings.openai_api_key}"},
            timeout=httpx.Timeout(30.0, connect=10.0),
        )

    def _post(self, path: str, payload: dict[str, Any]) -> dict[str, Any]:
        try:
            resp = self.client.post(path, json=payload)
        except httpx.HTTPError as exc:
            raise AIProviderError(f"OpenAI request failed: {exc.__class__.__name__}") from exc
        if resp.status_code >= 400:
            # Never echo request bodies (they contain user content) into logs.
            raise AIProviderError(f"OpenAI {path} returned {resp.status_code}")
        return resp.json()

    def embed(self, texts: list[str]) -> list[list[float]]:
        if not texts:
            return []
        data = self._post(
            "/embeddings",
            {
                "model": self.embedding_model,
                "input": texts,
                "dimensions": self.settings.embedding_dim,
            },
        )
        rows = sorted(data["data"], key=lambda r: r["index"])
        return [r["embedding"] for r in rows]

    def complete_json(self, system: str, user: str) -> dict[str, Any]:
        data = self._post(
            "/chat/completions",
            {
                "model": self.settings.openai_text_model,
                "response_format": {"type": "json_object"},
                "temperature": 0.2,
                "messages": [
                    {"role": "system", "content": system},
                    {"role": "user", "content": user},
                ],
            },
        )
        try:
            return json.loads(data["choices"][0]["message"]["content"])
        except (KeyError, IndexError, json.JSONDecodeError) as exc:
            raise AIProviderError("Model did not return valid JSON") from exc

    def moderate(self, text: str) -> ModerationResult | None:
        if not self.settings.use_moderation:
            return None
        try:
            data = self._post(
                "/moderations", {"model": self.settings.openai_moderation_model, "input": text}
            )
        except AIProviderError:
            return None  # rules-based safety still applies
        result = data["results"][0]
        return ModerationResult(
            flagged=bool(result.get("flagged")),
            categories=result.get("categories", {}),
            scores=result.get("category_scores", {}),
        )

    def create_realtime_secret(self, session: dict[str, Any]) -> RealtimeSecret:
        data = self._post(
            "/realtime/client_secrets",
            {"expires_after": {"anchor": "created_at", "seconds": 120}, "session": session},
        )
        return RealtimeSecret(
            value=data["value"], expires_at=data.get("expires_at"), model=session["model"]
        )


_WORD = re.compile(r"[a-z']+")


def hash_embedding(text: str, dim: int) -> list[float]:
    """Bag-of-words feature hashing. Crude, but deterministic and offline."""
    vec = [0.0] * dim
    words = _WORD.findall(text.lower())
    grams = words + [f"{a} {b}" for a, b in zip(words, words[1:])]
    for g in grams:
        h = int.from_bytes(hashlib.blake2b(g.encode(), digest_size=8).digest(), "big")
        vec[h % dim] += 1.0 if (h >> 32) & 1 else -1.0
    norm = math.sqrt(sum(v * v for v in vec)) or 1.0
    return [v / norm for v in vec]


class OfflineProvider:
    embedding_model = "offline-hash"

    def __init__(self, settings: Settings):
        self.dim = settings.embedding_dim

    def embed(self, texts: list[str]) -> list[list[float]]:
        return [hash_embedding(t, self.dim) for t in texts]

    def complete_json(self, system: str, user: str) -> dict[str, Any]:
        # Extractive fallback: keep the first few things the user said.
        user_lines = [l[6:].strip() for l in user.splitlines() if l.startswith("USER: ")]
        summary = " ".join(user_lines[:3])[:400]
        return {
            "summary": summary or "Short conversation.",
            "important_points": user_lines[:3],
            "emotional_context": None,
            "follow_up": None,
            "memories": [],
        }

    def moderate(self, text: str) -> ModerationResult | None:
        return None

    def create_realtime_secret(self, session: dict[str, Any]) -> RealtimeSecret:
        raise AIProviderError("Voice needs AI_PROVIDER=openai and OPENAI_API_KEY")


def build_provider(settings: Settings) -> AIProvider:
    if settings.ai_provider == "offline":
        return OfflineProvider(settings)
    return OpenAIProvider(settings)


def cosine(a: list[float], b: list[float]) -> float:
    dot = sum(x * y for x, y in zip(a, b))
    na = math.sqrt(sum(x * x for x in a))
    nb = math.sqrt(sum(y * y for y in b))
    if na == 0 or nb == 0:
        return 0.0
    return dot / (na * nb)

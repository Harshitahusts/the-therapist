import os
import time
from typing import Any

import jwt
import pytest
from fastapi.testclient import TestClient
from sqlalchemy import text

from app.ai import AIProviderError, ModerationResult, RealtimeSecret, hash_embedding
from app.config import Settings
from app.db import Base
from app.main import create_app

JWT_SECRET = "test-secret-which-is-long-enough-for-hs256"
DIM = 1536


class FakeAI:
    embedding_model = "fake-hash"

    def __init__(self):
        self.summary_response: dict[str, Any] = {
            "summary": "User talked about feeling anxious about a new job.",
            "important_points": ["New job starting soon"],
            "emotional_context": "anxious",
            "follow_up": "Ask how the first week went.",
            "memories": [
                {"category": "life_event", "content": "User is starting a new product management role.", "confidence": 0.9}
            ],
        }
        self.moderation: ModerationResult | None = None
        self.fail = False
        self.sessions: list[dict] = []
        self.summary_calls: list[str] = []

    def embed(self, texts):
        if self.fail:
            raise AIProviderError("down")
        return [hash_embedding(t, DIM) for t in texts]

    def complete_json(self, system, user):
        if self.fail:
            raise AIProviderError("down")
        self.summary_calls.append(user)
        return self.summary_response

    def moderate(self, text):
        return self.moderation

    def create_realtime_secret(self, session):
        if self.fail:
            raise AIProviderError("down")
        self.sessions.append(session)
        return RealtimeSecret(value="ek_test", expires_at=int(time.time()) + 60, model=session["model"])


def make_token(sub: str, email: str | None = None, exp_in: int = 3600, secret: str = JWT_SECRET, **extra) -> str:
    claims = {"sub": sub, "aud": "authenticated", "role": "authenticated", "exp": int(time.time()) + exp_in}
    if email:
        claims["email"] = email
    claims.update(extra)
    return jwt.encode(claims, secret, algorithm="HS256")


def auth(sub: str = "user-a", email: str | None = "a@example.com") -> dict:
    return {"Authorization": f"Bearer {make_token(sub, email)}"}


@pytest.fixture
def fake_ai():
    return FakeAI()


@pytest.fixture
def settings(tmp_path):
    url = os.environ.get("TEST_DATABASE_URL") or f"sqlite:///{tmp_path / 'test.db'}"
    return Settings(
        environment="test",
        database_url=url,
        supabase_jwt_secret=JWT_SECRET,
        ai_provider="offline",
        embedding_dim=DIM,
        realtime_sessions_per_hour=5,
    )


@pytest.fixture
def app(settings, fake_ai):
    app = create_app(settings=settings, ai=fake_ai)
    engine = app.state.db.engine
    if engine.dialect.name == "postgresql":
        # Schema is created by `alembic upgrade head` in CI; just clean it.
        with engine.begin() as conn:
            conn.execute(text("TRUNCATE users, knowledge_documents CASCADE"))
    else:
        Base.metadata.create_all(engine)
    yield app
    engine.dispose()


@pytest.fixture
def client(app):
    with TestClient(app) as c:
        yield c

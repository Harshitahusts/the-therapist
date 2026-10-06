import logging

from fastapi import FastAPI
from fastapi.responses import JSONResponse

from .ai import AIProvider, build_provider
from .auth import TokenVerifier
from .config import Settings, get_settings
from .db import Database
from .routers import conversations, me, memories


def create_app(settings: Settings | None = None, ai: AIProvider | None = None,
               verifier: TokenVerifier | None = None) -> FastAPI:
    settings = settings or get_settings()
    logging.basicConfig(level=logging.INFO)
    app = FastAPI(
        title="Haven backend",
        description="Backend for an AI wellbeing companion. Not a medical device.",
        docs_url=None if settings.environment == "production" else "/docs",
        redoc_url=None,
    )
    app.state.settings = settings
    app.state.db = Database(settings.database_url)
    app.state.ai = ai or build_provider(settings)
    app.state.verifier = verifier or TokenVerifier(settings)
    app.state.limiter = conversations.RateLimiter()

    @app.get("/health")
    def health():
        return {"ok": True}

    @app.exception_handler(Exception)
    async def unhandled(_, exc: Exception):  # don't leak internals (or user content) in errors
        logging.getLogger("app").exception("unhandled error")
        return JSONResponse(status_code=500, content={"detail": "Something went wrong."})

    app.include_router(me.router)
    app.include_router(memories.router)
    app.include_router(conversations.router)
    return app


def app_factory() -> FastAPI:
    return create_app()

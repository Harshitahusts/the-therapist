from functools import lru_cache
from typing import Literal

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """All configuration comes from environment variables (see .env.example)."""

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    environment: Literal["development", "test", "production"] = "development"
    database_url: str = "sqlite:///./dev.db"

    # --- Auth (Supabase) ---------------------------------------------------
    supabase_url: str = ""
    # Legacy HS256 secret. If empty, tokens are verified with the project's JWKS.
    supabase_jwt_secret: str = ""
    # Only used to delete the auth user when the user deletes their account.
    supabase_service_role_key: str = ""
    jwt_audience: str = "authenticated"

    # --- AI provider -------------------------------------------------------
    # "openai" talks to the OpenAI API. "offline" uses local deterministic
    # stand-ins (no voice) so the backend can be developed without a key.
    ai_provider: Literal["openai", "offline"] = "openai"
    openai_api_key: str = ""
    openai_base_url: str = "https://api.openai.com/v1"
    openai_realtime_model: str = "gpt-realtime"
    openai_realtime_voice: str = "marin"
    openai_transcribe_model: str = "gpt-4o-mini-transcribe"
    openai_text_model: str = "gpt-4.1-mini"
    openai_embedding_model: str = "text-embedding-3-small"
    openai_moderation_model: str = "omni-moderation-latest"
    use_moderation: bool = True
    embedding_dim: int = 1536

    # --- Limits (cost + abuse control) -------------------------------------
    realtime_sessions_per_hour: int = 20
    max_transcript_chars: int = 60_000
    max_memories_in_prompt: int = 12
    max_summaries_in_prompt: int = 3
    memory_dedup_similarity: float = 0.9

    # Default crisis region when the user has not chosen one (ISO country code
    # or "INTL").
    default_crisis_region: str = "INTL"


@lru_cache
def get_settings() -> Settings:
    return Settings()

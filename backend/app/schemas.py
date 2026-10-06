from datetime import datetime
from typing import Literal

from pydantic import BaseModel, Field


class ProfileIn(BaseModel):
    name: str | None = Field(default=None, max_length=120)
    preferred_name: str | None = Field(default=None, max_length=120)
    timezone: str | None = Field(default=None, max_length=64)
    language: str | None = Field(default=None, max_length=16)
    preferences: dict | None = None


class SettingsIn(BaseModel):
    memory_enabled: bool | None = None
    crisis_region: str | None = Field(default=None, max_length=8)


class OnboardingIn(BaseModel):
    preferred_name: str = Field(min_length=1, max_length=120)
    memory_enabled: bool = True
    timezone: str | None = Field(default=None, max_length=64)
    language: str | None = Field(default=None, max_length=16)
    crisis_region: str | None = Field(default=None, max_length=8)


class ProfileOut(BaseModel):
    name: str | None
    preferred_name: str | None
    timezone: str
    language: str
    preferences: dict


class SettingsOut(BaseModel):
    memory_enabled: bool
    crisis_region: str | None
    onboarding_completed: bool


class MeOut(BaseModel):
    id: str
    email: str | None
    profile: ProfileOut
    settings: SettingsOut


class MemoryIn(BaseModel):
    content: str = Field(min_length=1, max_length=500)
    category: str | None = None


class MemoryOut(BaseModel):
    id: str
    category: str
    content: str
    confidence: float
    source: str
    created_at: datetime
    updated_at: datetime


class RealtimeSessionOut(BaseModel):
    conversation_id: str
    client_secret: str
    expires_at: int | None
    model: str
    calls_url: str


class TurnIn(BaseModel):
    text: str = Field(min_length=1, max_length=4000)


class TurnOut(BaseModel):
    level: Literal["LOW", "MODERATE", "HIGH", "IMMEDIATE"]
    categories: list[str]
    guidance: str | None
    interrupt: bool
    resources: dict | None


class TranscriptTurn(BaseModel):
    role: Literal["user", "assistant"]
    text: str = Field(max_length=4000)


class EndIn(BaseModel):
    transcript: list[TranscriptTurn] = Field(default_factory=list, max_length=500)


class EndOut(BaseModel):
    conversation_id: str
    duration_seconds: int
    processing: bool


class ToolCallIn(BaseModel):
    arguments: dict = Field(default_factory=dict)

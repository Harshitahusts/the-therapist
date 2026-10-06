"""Builds the realtime voice session: instructions with the user's context, and tools."""

from __future__ import annotations

from datetime import datetime, timezone
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

from .config import Settings
from .memory import MemoryService
from .models import MEMORY_CATEGORIES, User
from .prompts import VOICE_AGENT_PROMPT

TOOLS = [
    {
        "type": "function",
        "name": "get_relevant_memories",
        "description": "Look up things the user has shared in earlier conversations that relate to a topic.",
        "parameters": {
            "type": "object",
            "properties": {"query": {"type": "string", "description": "Topic to look up, e.g. 'new job'"}},
            "required": ["query"],
        },
    },
    {
        "type": "function",
        "name": "save_memory",
        "description": (
            "Remember a durable, useful fact about the user for future conversations (goal, project, recurring "
            "worry, preference, life event, plan). Short third-person sentence. Never secrets or financial details."
        ),
        "parameters": {
            "type": "object",
            "properties": {
                "category": {"type": "string", "enum": list(MEMORY_CATEGORIES)},
                "content": {"type": "string", "description": "e.g. 'User is preparing for a new product management role.'"},
            },
            "required": ["category", "content"],
        },
    },
    {
        "type": "function",
        "name": "update_memory",
        "description": "Correct or update something remembered about the user.",
        "parameters": {
            "type": "object",
            "properties": {
                "about": {"type": "string", "description": "What the existing memory is about"},
                "new_content": {"type": "string", "description": "The corrected fact"},
            },
            "required": ["about", "new_content"],
        },
    },
    {
        "type": "function",
        "name": "delete_memory",
        "description": "Forget something the user asked you to forget.",
        "parameters": {
            "type": "object",
            "properties": {"about": {"type": "string", "description": "What should be forgotten"}},
            "required": ["about"],
        },
    },
    {
        "type": "function",
        "name": "get_recent_conversation_summaries",
        "description": "Brief notes from the user's last few conversations.",
        "parameters": {"type": "object", "properties": {}},
    },
    {
        "type": "function",
        "name": "get_user_profile",
        "description": "The user's name, preferred name, timezone, language and communication preferences.",
        "parameters": {"type": "object", "properties": {}},
    },
    {
        "type": "function",
        "name": "retrieve_knowledge",
        "description": (
            "Search the curated psychoeducation library (CBT, ACT, behavioural activation, mindfulness, grounding, "
            "sleep, motivation, etc.) for grounded material on a topic."
        ),
        "parameters": {
            "type": "object",
            "properties": {"query": {"type": "string"}},
            "required": ["query"],
        },
    },
]


def _local_now(tz_name: str | None) -> datetime:
    try:
        return datetime.now(ZoneInfo(tz_name or "UTC"))
    except (ZoneInfoNotFoundError, ValueError):
        return datetime.now(timezone.utc)


def part_of_day(hour: int) -> str:
    if 5 <= hour < 12:
        return "morning"
    if 12 <= hour < 17:
        return "afternoon"
    if 17 <= hour < 22:
        return "evening"
    return "night"


def build_instructions(user: User, memories: MemoryService, settings: Settings) -> str:
    profile = user.profile
    prefs = user.settings
    name = (profile.preferred_name or profile.name or "").strip()
    now = _local_now(profile.timezone)
    lines = [
        "",
        "# Context for this conversation (background only; never read it out)",
        f"- User's preferred name: {name or 'unknown (you can ask what they like to be called)'}",
        f"- User's local time: {now.strftime('%A %H:%M')} ({part_of_day(now.hour)})",
        f"- Preferred language: {profile.language or 'en'}",
    ]
    if profile.preferences:
        pretty = ", ".join(f"{k}: {v}" for k, v in profile.preferences.items())
        lines.append(f"- Communication preferences: {pretty}")
    if prefs.memory_enabled:
        lines.append("- Memory is ON.")
        mems = memories.list(user.id)
        mems.sort(key=lambda m: (m.confidence, m.updated_at), reverse=True)
        if mems:
            lines.append("- Things you know about the user:")
            for m in mems[: settings.max_memories_in_prompt]:
                lines.append(f"  - ({m.category}) {m.content}")
        summaries = memories.recent_summaries(user.id, settings.max_summaries_in_prompt)
        if summaries:
            lines.append("- Recent conversations (newest first):")
            for s in summaries:
                when = s.created_at.strftime("%Y-%m-%d") if s.created_at else ""
                extra = f" Feeling: {s.emotional_context}." if s.emotional_context else ""
                follow = f" Possible follow-up: {s.follow_up}" if s.follow_up else ""
                lines.append(f"  - {when}: {s.summary}{extra}{follow}")
    else:
        lines.append(
            "- Memory is OFF: you will not remember this conversation later. Do not call save_memory or update_memory."
        )
    return VOICE_AGENT_PROMPT + "\n".join(lines) + "\n"


def build_session_config(instructions: str, settings: Settings) -> dict:
    return {
        "type": "realtime",
        "model": settings.openai_realtime_model,
        "instructions": instructions,
        "output_modalities": ["audio"],
        "audio": {
            "input": {
                "transcription": {"model": settings.openai_transcribe_model},
                "noise_reduction": {"type": "far_field"},
                "turn_detection": {
                    "type": "semantic_vad",
                    # Give people room to pause and think before the companion replies.
                    "eagerness": "low",
                    "create_response": True,
                    "interrupt_response": True,
                },
            },
            "output": {"voice": settings.openai_realtime_voice},
        },
        "tools": TOOLS,
        "tool_choice": "auto",
        "max_output_tokens": 1024,
    }

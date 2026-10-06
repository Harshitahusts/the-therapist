import logging
from datetime import datetime, timezone
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

import httpx
from fastapi import APIRouter, Depends, HTTPException, Response
from sqlalchemy import delete, select
from sqlalchemy.orm import Session

from ..auth import current_user, get_db
from ..config import Settings
from ..deps import get_settings_dep
from ..models import (
    Conversation,
    ConversationSummary,
    Memory,
    SafetyEvent,
    User,
    UserProfile,
    UserSettings,
)
from ..safety import CRISIS_RESOURCES, resources_for
from ..schemas import MeOut, OnboardingIn, ProfileIn, ProfileOut, SettingsIn, SettingsOut

log = logging.getLogger(__name__)
router = APIRouter(prefix="/v1/me", tags=["me"])


def _valid_tz(tz: str) -> str:
    try:
        ZoneInfo(tz)
    except (ZoneInfoNotFoundError, ValueError):
        raise HTTPException(422, "Unknown timezone")
    return tz


def _valid_region(region: str) -> str:
    region = region.upper()
    if region not in CRISIS_RESOURCES:
        raise HTTPException(422, f"Unsupported region. Use one of {sorted(CRISIS_RESOURCES)}")
    return region


def _me(user: User) -> MeOut:
    p, s = user.profile, user.settings
    return MeOut(
        id=user.id,
        email=user.email,
        profile=ProfileOut(
            name=p.name, preferred_name=p.preferred_name, timezone=p.timezone,
            language=p.language, preferences=p.preferences or {},
        ),
        settings=SettingsOut(
            memory_enabled=s.memory_enabled, crisis_region=s.crisis_region,
            onboarding_completed=s.onboarding_completed_at is not None,
        ),
    )


@router.get("", response_model=MeOut)
def get_me(user: User = Depends(current_user)):
    return _me(user)


@router.put("/profile", response_model=MeOut)
def update_profile(body: ProfileIn, user: User = Depends(current_user), db: Session = Depends(get_db)):
    p: UserProfile = user.profile
    if body.name is not None:
        p.name = body.name.strip() or None
    if body.preferred_name is not None:
        p.preferred_name = body.preferred_name.strip() or None
    if body.timezone is not None:
        p.timezone = _valid_tz(body.timezone)
    if body.language is not None:
        p.language = body.language
    if body.preferences is not None:
        p.preferences = {str(k)[:40]: str(v)[:200] for k, v in list(body.preferences.items())[:20]}
    db.commit()
    return _me(user)


@router.put("/settings", response_model=MeOut)
def update_settings(body: SettingsIn, user: User = Depends(current_user), db: Session = Depends(get_db)):
    s: UserSettings = user.settings
    if body.memory_enabled is not None:
        s.memory_enabled = body.memory_enabled
    if body.crisis_region is not None:
        s.crisis_region = _valid_region(body.crisis_region)
    db.commit()
    return _me(user)


@router.post("/onboarding", response_model=MeOut)
def complete_onboarding(body: OnboardingIn, user: User = Depends(current_user), db: Session = Depends(get_db)):
    p, s = user.profile, user.settings
    p.preferred_name = body.preferred_name.strip()
    if not p.name:
        p.name = p.preferred_name
    if body.timezone:
        p.timezone = _valid_tz(body.timezone)
    if body.language:
        p.language = body.language
    if body.crisis_region:
        s.crisis_region = _valid_region(body.crisis_region)
    s.memory_enabled = body.memory_enabled
    s.onboarding_completed_at = s.onboarding_completed_at or datetime.now(timezone.utc)
    db.commit()
    return _me(user)


@router.get("/crisis-resources")
def crisis_resources(user: User = Depends(current_user), settings: Settings = Depends(get_settings_dep)):
    return resources_for(user.settings.crisis_region or settings.default_crisis_region)


@router.get("/export")
def export_data(user: User = Depends(current_user), db: Session = Depends(get_db)):
    def iso(d):
        return d.isoformat() if d else None

    p, s = user.profile, user.settings
    payload = {
        "exported_at": datetime.now(timezone.utc).isoformat(),
        "account": {"id": user.id, "email": user.email, "created_at": iso(user.created_at)},
        "profile": {"name": p.name, "preferred_name": p.preferred_name, "timezone": p.timezone,
                    "language": p.language, "preferences": p.preferences},
        "settings": {"memory_enabled": s.memory_enabled, "crisis_region": s.crisis_region},
        "memories": [
            {"id": m.id, "category": m.category, "content": m.content, "confidence": m.confidence,
             "source": m.source, "created_at": iso(m.created_at), "updated_at": iso(m.updated_at)}
            for m in db.scalars(select(Memory).where(Memory.user_id == user.id).order_by(Memory.created_at))
        ],
        "conversation_summaries": [
            {"conversation_id": c.conversation_id, "summary": c.summary, "important_points": c.important_points,
             "emotional_context": c.emotional_context, "follow_up": c.follow_up, "created_at": iso(c.created_at)}
            for c in db.scalars(
                select(ConversationSummary).where(ConversationSummary.user_id == user.id)
                .order_by(ConversationSummary.created_at)
            )
        ],
        "conversations": [
            {"id": c.id, "started_at": iso(c.started_at), "ended_at": iso(c.ended_at),
             "duration_seconds": c.duration_seconds}
            for c in db.scalars(select(Conversation).where(Conversation.user_id == user.id).order_by(Conversation.started_at))
        ],
        "safety_events": [
            {"level": e.level, "categories": e.categories, "created_at": iso(e.created_at)}
            for e in db.scalars(select(SafetyEvent).where(SafetyEvent.user_id == user.id).order_by(SafetyEvent.created_at))
        ],
        "note": "Raw audio and full transcripts are never stored, so they are not part of this export.",
    }
    return payload


def _delete_user_rows(db: Session, user_id: str) -> None:
    # ON DELETE CASCADE removes profile, settings, conversations, summaries,
    # memories, embeddings and safety events.
    db.execute(delete(User).where(User.id == user_id))
    db.commit()
    db.expunge_all()


@router.delete("/data", status_code=204)
def delete_all_data(user: User = Depends(current_user), db: Session = Depends(get_db)):
    """Deletes everything stored about the user but keeps the login."""
    email, uid = user.email, user.id
    _delete_user_rows(db, uid)
    db.add(User(id=uid, email=email))
    db.add(UserProfile(user_id=uid, preferences={}))
    db.add(UserSettings(user_id=uid))
    db.commit()
    return Response(status_code=204)


@router.delete("", status_code=204)
def delete_account(
    user: User = Depends(current_user),
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_settings_dep),
):
    uid = user.id
    _delete_user_rows(db, uid)
    if settings.supabase_url and settings.supabase_service_role_key:
        try:
            r = httpx.delete(
                f"{settings.supabase_url.rstrip('/')}/auth/v1/admin/users/{uid}",
                headers={
                    "apikey": settings.supabase_service_role_key,
                    "Authorization": f"Bearer {settings.supabase_service_role_key}",
                },
                timeout=10,
            )
            if r.status_code >= 400 and r.status_code != 404:
                log.error("Supabase auth user deletion failed with %s", r.status_code)
                raise HTTPException(502, "Your data was deleted, but removing the login failed. Please try again.")
        except httpx.HTTPError:
            raise HTTPException(502, "Your data was deleted, but removing the login failed. Please try again.")
    return Response(status_code=204)

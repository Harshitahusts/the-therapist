"""Verifies Supabase access tokens and maps them to a local `users` row."""

from __future__ import annotations

from dataclasses import dataclass

import jwt
from fastapi import Depends, HTTPException, Request, status
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from .config import Settings
from .models import User, UserProfile, UserSettings


@dataclass
class AuthenticatedUser:
    id: str
    email: str | None


class TokenVerifier:
    def __init__(self, settings: Settings):
        self.settings = settings
        self._jwks: jwt.PyJWKClient | None = None
        if not settings.supabase_jwt_secret and settings.supabase_url:
            self._jwks = jwt.PyJWKClient(
                f"{settings.supabase_url.rstrip('/')}/auth/v1/.well-known/jwks.json",
                cache_keys=True,
                lifespan=3600,
            )

    def verify(self, token: str) -> AuthenticatedUser:
        options = {"require": ["exp", "sub"]}
        try:
            if self.settings.supabase_jwt_secret:
                claims = jwt.decode(
                    token,
                    self.settings.supabase_jwt_secret,
                    algorithms=["HS256"],
                    audience=self.settings.jwt_audience,
                    options=options,
                )
            elif self._jwks is not None:
                key = self._jwks.get_signing_key_from_jwt(token)
                claims = jwt.decode(
                    token,
                    key.key,
                    algorithms=["RS256", "ES256"],
                    audience=self.settings.jwt_audience,
                    options=options,
                )
            else:
                raise HTTPException(status.HTTP_503_SERVICE_UNAVAILABLE, "Auth is not configured")
        except (jwt.PyJWTError, jwt.PyJWKClientError):
            raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Invalid or expired token")
        if claims.get("role") not in (None, "authenticated"):
            raise HTTPException(status.HTTP_403_FORBIDDEN, "Not a user token")
        return AuthenticatedUser(id=str(claims["sub"]), email=claims.get("email"))


def _bearer(request: Request) -> str:
    header = request.headers.get("authorization", "")
    scheme, _, token = header.partition(" ")
    if scheme.lower() != "bearer" or not token:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Missing bearer token")
    return token


def get_db(request: Request):
    yield from request.app.state.db.session()


def current_user(request: Request, db: Session = Depends(get_db)) -> User:
    auth = request.app.state.verifier.verify(_bearer(request))
    user = db.get(User, auth.id)
    if user is None:
        user = User(id=auth.id, email=auth.email)
        db.add(user)
        db.add(UserProfile(user_id=auth.id, preferences={}))
        db.add(UserSettings(user_id=auth.id))
        try:
            db.commit()
        except IntegrityError:  # concurrent first request created it
            db.rollback()
            user = db.get(User, auth.id)
    return user

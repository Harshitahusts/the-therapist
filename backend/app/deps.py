from fastapi import Request

from .ai import AIProvider
from .config import Settings


def get_ai(request: Request) -> AIProvider:
    return request.app.state.ai


def get_settings_dep(request: Request) -> Settings:
    return request.app.state.settings

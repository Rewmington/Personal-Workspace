from __future__ import annotations

import re

from fastapi import APIRouter, HTTPException

from ..config import settings
from ..schemas import Profile, ProfileUpdate


router = APIRouter(prefix="/api/profile", tags=["profile"])


@router.get("", response_model=Profile)
def get_profile() -> Profile:
    return Profile(display_name=settings.display_name, github_username=settings.github_username or "")


@router.put("", response_model=Profile)
def update_profile(payload: ProfileUpdate) -> Profile:
    username = payload.github_username.strip()
    if username and not re.fullmatch(r"[A-Za-z0-9-]+", username):
        raise HTTPException(status_code=400, detail="GitHub 用户名格式不正确")
    settings.save_profile(payload.display_name)
    if username != (settings.github_username or ""):
        settings.save_github(username, None)
    return get_profile()

from __future__ import annotations

import re

from fastapi import APIRouter, HTTPException, Query, Request

from ..config import settings
from ..schemas import GithubActivity, GithubRefreshResponse, GithubRepo, GithubSettings, GithubSettingsStatus
from ..services.github import cached_activity, cached_repositories, fetch_profile, refresh_github


router = APIRouter(prefix="/api/github", tags=["github"])


def _require_local_request(request: Request) -> None:
    host = request.client.host if request.client else ""
    if host not in {"127.0.0.1", "::1", "localhost"}:
        raise HTTPException(status_code=403, detail="GitHub 凭据只能在运行服务的电脑上设置")


@router.get("/settings", response_model=GithubSettingsStatus)
def get_settings() -> GithubSettingsStatus:
    return GithubSettingsStatus(
        username=settings.github_username or "",
        token_configured=bool(settings.github_token),
    )


@router.put("/settings", response_model=GithubSettingsStatus)
def update_settings(request: Request, payload: GithubSettings) -> GithubSettingsStatus:
    _require_local_request(request)
    username = payload.username.strip()
    if username and not re.fullmatch(r"[A-Za-z0-9-]+", username):
        raise HTTPException(status_code=400, detail="GitHub 用户名格式不正确")

    token = payload.token.strip() if payload.token is not None else None
    settings.save_github(username, token)
    return get_settings()


@router.get("/profile")
async def profile() -> dict[str, str | None]:
    if not settings.github_username:
        return {"login": "", "name": "", "avatar_url": "", "html_url": "", "bio": ""}
    try:
        return await fetch_profile()
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"GitHub 个人资料请求失败: {exc}") from exc

@router.get("/repos", response_model=list[GithubRepo])
def list_repositories() -> list[GithubRepo]:
    return [GithubRepo.model_validate(item) for item in cached_repositories()]


@router.get("/activity", response_model=list[GithubActivity])
def list_activity(limit: int = Query(default=50, ge=1, le=100)) -> list[GithubActivity]:
    return [GithubActivity.model_validate(item) for item in cached_activity(limit)]


@router.post("/refresh", response_model=GithubRefreshResponse)
async def refresh() -> GithubRefreshResponse:
    try:
        result = await refresh_github()
    except RuntimeError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"GitHub 请求失败: {exc}") from exc
    return GithubRefreshResponse.model_validate(result)

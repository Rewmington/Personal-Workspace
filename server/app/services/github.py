from __future__ import annotations

import json
from datetime import datetime, timedelta, timezone
from typing import Any

import httpx

from ..config import settings
from ..database import connection, utc_now


GITHUB_API = "https://api.github.com"


def _headers() -> dict[str, str]:
    headers = {"Accept": "application/vnd.github+json", "User-Agent": "personal-workstation"}
    if settings.github_token:
        headers["Authorization"] = f"Bearer {settings.github_token}"
    return headers


def _username() -> str:
    if not settings.github_username:
        raise RuntimeError("未配置 GITHUB_USERNAME")
    return settings.github_username


async def refresh_github() -> dict[str, int | str | bool]:
    username = _username()
    async with httpx.AsyncClient(base_url=GITHUB_API, headers=_headers(), timeout=settings.github_fetch_timeout) as client:
        repos_response = await client.get(f"/users/{username}/repos", params={"per_page": 100, "sort": "pushed"})
        repos_response.raise_for_status()
        repos = repos_response.json()
        events_response = await client.get(f"/users/{username}/events/public", params={"per_page": 100})
        events_response.raise_for_status()
        events = events_response.json()

        commits: list[dict[str, Any]] = []
        for repo in repos[:20]:
            response = await client.get(f"/repos/{repo['full_name']}/commits", params={"per_page": 20})
            if response.is_success:
                for commit in response.json():
                    commit["repo_name"] = repo["name"]
                    commits.append(commit)

    now = utc_now()
    with connection() as conn:
        repo_ids: dict[str, int] = {}
        for repo in repos:
            cursor = conn.execute(
                """INSERT INTO github_repos(name, owner, html_url, description, language, stars, open_issues, pushed_at, last_fetch_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(name) DO UPDATE SET owner=excluded.owner, html_url=excluded.html_url,
                description=excluded.description, language=excluded.language, stars=excluded.stars,
                open_issues=excluded.open_issues, pushed_at=excluded.pushed_at, last_fetch_at=excluded.last_fetch_at""",
                (repo["name"], repo["owner"]["login"], repo.get("html_url", ""), repo.get("description") or "", repo.get("language") or "", repo.get("stargazers_count", 0), repo.get("open_issues_count", 0), repo.get("pushed_at"), now),
            )
            row = conn.execute("SELECT id FROM github_repos WHERE name = ?", (repo["name"],)).fetchone()
            repo_ids[repo["name"]] = row[0]
        for commit in commits:
            repo_id = repo_ids.get(commit.get("repo_name", ""))
            if repo_id is None:
                continue
            commit_data = commit.get("commit", {})
            author = (commit_data.get("author") or {}).get("name", "")
            pushed_at = (commit_data.get("author") or {}).get("date") or now
            conn.execute(
                "INSERT OR IGNORE INTO github_commits(repo_id, sha, message, author, pushed_at) VALUES (?, ?, ?, ?, ?)",
                (repo_id, commit.get("sha", ""), (commit_data.get("message") or "").splitlines()[0], author, pushed_at),
            )
        for event in events:
            repo_name = (event.get("repo") or {}).get("name", "").split("/", 1)[-1]
            repo_id = repo_ids.get(repo_name)
            conn.execute(
                "INSERT INTO github_events(repo_id, type, actor, payload, created_at) VALUES (?, ?, ?, ?, ?)",
                (repo_id, event.get("type", "UnknownEvent"), (event.get("actor") or {}).get("login", ""), json.dumps(event.get("payload") or {}, ensure_ascii=False), event.get("created_at") or now),
            )
    return {"ok": True, "fetched_repositories": len(repos), "fetched_commits": len(commits), "fetched_events": len(events), "message": "GitHub 数据已刷新"}


def cached_repositories() -> list[dict[str, Any]]:
    with connection() as conn:
        rows = conn.execute("SELECT * FROM github_repos ORDER BY pushed_at DESC, name").fetchall()
    return [dict(row) for row in rows]


def cached_activity(limit: int = 50) -> list[dict[str, Any]]:
    with connection() as conn:
        rows = conn.execute("SELECT * FROM github_events ORDER BY created_at DESC, id DESC LIMIT ?", (limit,)).fetchall()
    values = []
    for row in rows:
        item = dict(row)
        try:
            item["payload"] = json.loads(item["payload"])
        except json.JSONDecodeError:
            item["payload"] = {}
        values.append(item)
    return values


def cached_commits(days: int = 180) -> list[dict[str, Any]]:
    cutoff = (datetime.now(timezone.utc) - timedelta(days=days)).isoformat()
    with connection() as conn:
        rows = conn.execute("SELECT * FROM github_commits WHERE pushed_at >= ? ORDER BY pushed_at DESC", (cutoff,)).fetchall()
    return [dict(row) for row in rows]


async def fetch_profile() -> dict[str, str | None]:
    username = _username()
    async with httpx.AsyncClient(base_url=GITHUB_API, headers=_headers(), timeout=settings.github_fetch_timeout) as client:
        response = await client.get(f"/users/{username}")
        response.raise_for_status()
        profile = response.json()
    return {
        "login": profile.get("login") or username,
        "name": profile.get("name") or profile.get("login") or username,
        "avatar_url": profile.get("avatar_url") or "",
        "html_url": profile.get("html_url") or f"https://github.com/{username}",
        "bio": profile.get("bio") or "",
    }
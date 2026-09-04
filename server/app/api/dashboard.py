from __future__ import annotations

from datetime import datetime, timedelta, timezone

from fastapi import APIRouter

from ..database import connection
from ..services.github import cached_commits


router = APIRouter(prefix="/api/dashboard", tags=["dashboard"])


@router.get("/summary")
def summary() -> dict[str, int | float]:
    with connection() as conn:
        total_tasks = conn.execute("SELECT COUNT(*) FROM tasks").fetchone()[0]
        completed_tasks = conn.execute(
            "SELECT COUNT(*) FROM tasks t JOIN columns c ON c.id = t.column_id WHERE c.name IN ('已完成', '完成', 'Done')"
        ).fetchone()[0]
        notes = conn.execute("SELECT COUNT(*) FROM notes").fetchone()[0]
        projects = conn.execute("SELECT COUNT(*) FROM github_repos").fetchone()[0]
    commits = cached_commits(days=30)
    completion = round((completed_tasks / total_tasks) * 100, 1) if total_tasks else 0.0
    return {
        "tasks_total": total_tasks,
        "tasks_completed": completed_tasks,
        "task_completion_rate": completion,
        "notes_total": notes,
        "github_commits_30d": len(commits),
        "github_repositories": projects,
    }


@router.get("/github-heatmap")
def github_heatmap(days: int = 180) -> dict[str, object]:
    with connection() as conn:
        rows = conn.execute(
            """SELECT c.sha, c.message, c.author, c.pushed_at, r.name AS repo_name
               FROM github_commits c
               JOIN github_repos r ON c.repo_id = r.id
               WHERE DATE(c.pushed_at) >= DATE('now', '-' || ? || ' days')
               ORDER BY c.pushed_at DESC""",
            (days,),
        ).fetchall()

    day_commits: dict[str, list[dict[str, str]]] = {}
    for row in rows:
        try:
            day = datetime.fromisoformat(row["pushed_at"].replace("Z", "+00:00")).date().isoformat()
        except (ValueError, TypeError):
            continue
        if day not in day_commits:
            day_commits[day] = []
        day_commits[day].append({
            "sha": row["sha"][:7],
            "message": row["message"],
            "author": row["author"] or "",
            "repo_name": row["repo_name"],
        })

    today = datetime.now(timezone.utc).date()
    start = today - timedelta(days=days - 1)
    series: list[dict[str, object]] = []
    total = 0
    for offset in range(days):
        d = start + timedelta(days=offset)
        dk = d.isoformat()
        commits_list = day_commits.get(dk, [])
        total += len(commits_list)
        series.append({"date": dk, "count": len(commits_list), "commits": commits_list})

    return {"days": days, "total": total, "items": series}


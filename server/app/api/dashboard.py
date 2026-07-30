from __future__ import annotations

from collections import Counter
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
    commits = cached_commits(days=days)
    counts = Counter()
    for commit in commits:
        try:
            day = datetime.fromisoformat(commit["pushed_at"].replace("Z", "+00:00")).date().isoformat()
        except (ValueError, TypeError):
            continue
        counts[day] += 1
    today = datetime.now(timezone.utc).date()
    start = today - timedelta(days=days - 1)
    series = []
    for offset in range(days):
        day = start + timedelta(days=offset)
        series.append({"date": day.isoformat(), "count": counts.get(day.isoformat(), 0)})
    return {"days": days, "total": sum(counts.values()), "items": series}


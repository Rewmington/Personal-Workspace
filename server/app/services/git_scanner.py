from __future__ import annotations
import subprocess
from datetime import datetime
from pathlib import Path

def run_git(path: str, *args: str, timeout: int = 10) -> str:
    result = subprocess.run(["git", *args], cwd=path, capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=timeout)
    if result.returncode: raise RuntimeError(result.stderr.strip() or "git 命令失败")
    return result.stdout.strip()

def scan_repository(path: str) -> dict:
    root = str(Path(path).expanduser().resolve())
    branch = run_git(root, "branch", "--show-current")
    status_lines = run_git(root, "status", "--porcelain").splitlines()
    last = run_git(root, "log", "-1", "--format=%s%x1f%aI").split("\x1f", 1)
    ahead = behind = 0
    try:
        counts = run_git(root, "rev-list", "--left-right", "--count", f"{branch}...@{{upstream}}")
        ahead, behind = [int(x) for x in counts.split()]
    except Exception: pass
    return {"path": root, "name": Path(root).name, "branch": branch, "ahead": ahead, "behind": behind,
            "changed": sum(1 for x in status_lines if x and x[0] == " "), "staged": sum(1 for x in status_lines if x and x[0] not in " ?"),
            "untracked": sum(1 for x in status_lines if x.startswith("??")), "last_commit": last[0] if last else "",
            "last_commit_at": last[1] if len(last)>1 else "", "last_scan": datetime.now().astimezone().isoformat(), "status": "ok"}

def scan_directories(directories: list[str], max_depth: int = 5) -> list[dict]:
    found=[]
    for directory in directories:
        base=Path(directory).expanduser()
        if not base.exists(): continue
        for git in base.glob("**/.git"):
            try:
                if len(git.relative_to(base).parts) <= max_depth + 1: found.append(scan_repository(str(git.parent)))
            except Exception: pass
    return found

def recent_commits(path: str, count: int = 20):
    text=run_git(path, "log", f"-{count}", "--format=%H%x1f%s%x1f%an%x1f%aI")
    return [dict(zip(("sha","message","author","pushed_at"), line.split("\x1f"))) for line in text.splitlines() if line]

def working_tree_changes(path: str) -> list[dict]:
    rows = run_git(path, "status", "--porcelain=v1", "-uall").splitlines()
    changes = []
    for row in rows:
        if len(row) < 4:
            continue
        index_status, worktree_status = row[0], row[1]
        file_path = row[3:]
        if " -> " in file_path:
            file_path = file_path.split(" -> ", 1)[-1]
        changes.append({
            "path": file_path,
            "index": index_status,
            "worktree": worktree_status,
            "status": "untracked" if row.startswith("??") else "staged" if index_status != " " else "modified",
        })
    return changes

from __future__ import annotations
from pathlib import Path
from fastapi import APIRouter, HTTPException
from ..database import connection, row_dict
from ..services.git_scanner import scan_directories, scan_repository, recent_commits, working_tree_changes
router=APIRouter(prefix="/api/git", tags=["git"])
@router.get("/repos")
def repos():
    with connection() as conn: rows=conn.execute("SELECT * FROM git_repos_local ORDER BY name").fetchall()
    return [dict(r) for r in rows]
@router.get("/repos/{repo_id}")
def repo(repo_id:int):
    with connection() as conn: row=conn.execute("SELECT * FROM git_repos_local WHERE id=?",(repo_id,)).fetchone()
    if row is None: raise HTTPException(404,"仓库不存在")
    item=dict(row)
    item["changes"] = working_tree_changes(item["path"])
    item["commits"] = recent_commits(item["path"], count=100)
    return item
@router.post("/repos/refresh")
def refresh(paths: list[str] | None = None):
    items=scan_directories(paths or [str(Path.cwd())]) if paths is None or any(Path(p).is_dir() and not (Path(p)/".git").exists() for p in paths) else [scan_repository(p) for p in paths]
    with connection() as conn:
        for item in items:
            conn.execute("INSERT INTO git_repos_local(path,name,branch,ahead,behind,changed,staged,untracked,last_commit,last_commit_at,last_scan,status) VALUES(?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(path) DO UPDATE SET name=excluded.name,branch=excluded.branch,ahead=excluded.ahead,behind=excluded.behind,changed=excluded.changed,staged=excluded.staged,untracked=excluded.untracked,last_commit=excluded.last_commit,last_commit_at=excluded.last_commit_at,last_scan=excluded.last_scan,status=excluded.status", tuple(item.values()))
    return {"ok":True,"count":len(items),"repos":items}
@router.delete("/repos/{repo_id}", status_code=204)
def remove(repo_id:int):
    with connection() as conn: cur=conn.execute("DELETE FROM git_repos_local WHERE id=?",(repo_id,))
    if cur.rowcount==0: raise HTTPException(404,"仓库不存在")
@router.get("/repos/config")
def config(): return {"paths": [], "max_depth": 5}
@router.put("/repos/config")
def update_config(payload: dict): return payload

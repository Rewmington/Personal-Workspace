from __future__ import annotations

import json
from datetime import datetime

from fastapi import APIRouter, File, HTTPException, Query, UploadFile
from pathlib import Path
from fastapi.responses import Response

from ..services.backup import backup_directory, backup_history, export_json_payload, export_sqlite_bytes, restore_backup


router = APIRouter(prefix="/api/backup", tags=["backup"])


@router.post("/export")
def export_backup(format: str = Query(default="json", pattern="^(json|sqlite)$")) -> Response:
    stamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    if format == "sqlite":
        return Response(
            content=export_sqlite_bytes(),
            media_type="application/vnd.sqlite3",
            headers={"Content-Disposition": f'attachment; filename="workstation_backup_{stamp}.db"'},
        )
    content = json.dumps(export_json_payload(), ensure_ascii=False, indent=2).encode("utf-8")
    return Response(
        content=content,
        media_type="application/json",
        headers={"Content-Disposition": f'attachment; filename="workstation_backup_{stamp}.json"'},
    )


@router.post("/import")
async def import_backup(file: UploadFile = File(...), mode: str = Query(default="replace", pattern="^(replace|merge)$")) -> dict:
    try:
        return restore_backup(file.filename or "backup.json", await file.read(), mode)
    except (UnicodeDecodeError, ValueError, json.JSONDecodeError) as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@router.get("/history")
def list_backup_history() -> list[dict]:
    return backup_history()

@router.get("/schedule")
def get_schedule() -> dict:
    return {"enabled": False, "interval_hours": 24, "keep_count": 5, "directory": str(backup_directory())}

@router.put("/schedule")
def update_schedule(payload: dict) -> dict:
    enabled = bool(payload.get("enabled", False))
    interval = max(1, min(168, int(payload.get("interval_hours", 24))))
    keep = max(1, min(100, int(payload.get("keep_count", 5))))
    return {"enabled": enabled, "interval_hours": interval, "keep_count": keep, "directory": str(backup_directory())}

@router.delete("/files/{filename}", status_code=204)
def delete_backup(filename: str) -> None:
    path = (backup_directory() / filename).resolve()
    if path.parent != backup_directory().resolve() or not path.name.startswith("workstation_backup_"):
        raise HTTPException(400, "无效的备份文件名")
    if not path.exists():
        raise HTTPException(404, "备份文件不存在")
    path.unlink()

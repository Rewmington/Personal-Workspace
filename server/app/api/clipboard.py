"""剪贴板历史 — 保留最近 20 条"""

from __future__ import annotations

from datetime import datetime, timezone

from fastapi import APIRouter, Query
from pydantic import BaseModel

from ..database import connection

router = APIRouter(prefix="/api/clipboard", tags=["clipboard"])

MAX_ITEMS = 20


class ClipboardCreate(BaseModel):
    content: str
    source: str = "manual"


@router.get("")
def list_clipboard(limit: int = Query(default=MAX_ITEMS, le=100)):
    with connection() as db:
        rows = db.execute(
            "SELECT id, content, source, created_at FROM clipboard_history ORDER BY created_at DESC LIMIT ?",
            (limit,),
        ).fetchall()
        items = [dict(r) for r in rows]
        return {"items": items}


@router.post("")
def add_clipboard(body: ClipboardCreate):
    if not body.content.strip():
        return {"ok": False, "error": "内容不能为空"}
    with connection() as db:
        now = datetime.now(timezone.utc).replace(microsecond=0).isoformat()
        db.execute(
            "INSERT INTO clipboard_history (content, source, created_at) VALUES (?, ?, ?)",
            (body.content.strip(), body.source, now),
        )
        # 保留最近 MAX_ITEMS 条
        overflow = db.execute(
            "SELECT id FROM clipboard_history ORDER BY created_at DESC LIMIT -1 OFFSET ?",
            (MAX_ITEMS,),
        ).fetchall()
        for row in overflow:
            db.execute("DELETE FROM clipboard_history WHERE id = ?", (row["id"],))
    return {"ok": True}


@router.delete("/{item_id}")
def delete_clipboard(item_id: int):
    with connection() as db:
        db.execute("DELETE FROM clipboard_history WHERE id = ?", (item_id,))
    return {"ok": True}


@router.delete("")
def clear_clipboard():
    with connection() as db:
        db.execute("DELETE FROM clipboard_history")
    return {"ok": True}

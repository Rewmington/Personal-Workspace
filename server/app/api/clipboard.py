"""剪贴板历史 — 保留最近 20 条"""

from __future__ import annotations

from datetime import datetime, timezone

from fastapi import APIRouter, Query
from pydantic import BaseModel

from ..config import settings
from ..database import connection

router = APIRouter(prefix="/api/clipboard", tags=["clipboard"])

MAX_ITEMS = 20


class ClipboardCreate(BaseModel):
    content: str
    source: str = "manual"


class SyncPayload(BaseModel):
    enabled: bool


def add_clipboard_record(content: str, source: str = "manual") -> bool:
    """写入剪贴板历史并裁剪到 MAX_ITEMS 条。同步逻辑与 REST 共用。"""
    if not content.strip():
        return False
    with connection() as db:
        now = datetime.now(timezone.utc).replace(microsecond=0).isoformat()
        db.execute(
            "INSERT INTO clipboard_history (content, source, created_at) VALUES (?, ?, ?)",
            (content.strip(), source, now),
        )
        # 保留最近 MAX_ITEMS 条
        overflow = db.execute(
            "SELECT id FROM clipboard_history ORDER BY created_at DESC LIMIT -1 OFFSET ?",
            (MAX_ITEMS,),
        ).fetchall()
        for row in overflow:
            db.execute("DELETE FROM clipboard_history WHERE id = ?", (row["id"],))
    return True


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
async def add_clipboard(body: ClipboardCreate):
    text = body.content.strip()
    if not text:
        return {"ok": False, "error": "内容不能为空"}
    ok = add_clipboard_record(text, body.source)  # 历史始终保留
    try:
        from ..clipboard_sync import handle_local_text  # 延迟导入避免循环依赖

        await handle_local_text(text, body.source)  # 同步：去重→写本机→广播
    except Exception:
        pass  # 同步失败不阻断历史保存
    return {"ok": ok}


@router.get("/sync")
def get_clipboard_sync():
    return {"enabled": settings.clipboard_sync_enabled}


@router.put("/sync")
def set_clipboard_sync(payload: SyncPayload):
    settings.save_clipboard(payload.enabled)
    return {"ok": True, "enabled": settings.clipboard_sync_enabled}


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

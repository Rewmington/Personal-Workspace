from __future__ import annotations

from fastapi import APIRouter
from pydantic import BaseModel

from ..websocket.manager import manager

router = APIRouter(prefix="/api/notify", tags=["notify"])

_ALLOWED_TYPES = {"info", "success", "warning", "confirm"}


class NotifyPayload(BaseModel):
    title: str
    message: str = ""
    type: str = "info"


@router.post("")
async def notify(payload: NotifyPayload) -> dict[str, object]:
    """接收一条通知并通过 WebSocket 广播给所有客户端（局域网，无鉴权）。"""
    title = payload.title.strip()[:80]
    if not title:
        return {"ok": False, "error": "title 不能为空"}
    message = payload.message.strip()[:240]
    typ = payload.type if payload.type in _ALLOWED_TYPES else "info"
    await manager.broadcast({"type": "notify", "data": {"title": title, "message": message, "type": typ}})
    return {"ok": True}

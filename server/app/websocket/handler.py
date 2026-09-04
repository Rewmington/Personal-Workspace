from __future__ import annotations

import asyncio

from fastapi import WebSocket, WebSocketDisconnect

from ..api.tasks import get_board_tasks
from ..database import connection
from .manager import manager


def full_state() -> dict[str, object]:
    with connection() as conn:
        boards = [dict(row) for row in conn.execute("SELECT * FROM boards ORDER BY position, id").fetchall()]
        notes = []
        for row in conn.execute("SELECT * FROM notes ORDER BY updated_at DESC").fetchall():
            item = dict(row)
            notes.append(item)
    board_state = []
    for board in boards:
        board_state.append({"board": board, "columns": [column.model_dump(mode="json") for column in get_board_tasks(board["id"])]})
    return {"boards": board_state, "notes": notes}


async def websocket_endpoint(websocket: WebSocket) -> None:
    await manager.connect(websocket)
    last_pong = asyncio.get_running_loop().time()

    async def heartbeat() -> None:
        nonlocal last_pong
        while True:
            await asyncio.sleep(30)
            sent_at = asyncio.get_running_loop().time()
            await manager.send(websocket, {"type": "ping"})
            await asyncio.sleep(10)
            if last_pong < sent_at:
                await websocket.close(code=1001, reason="heartbeat timeout")
                return

    heartbeat_task = asyncio.create_task(heartbeat())
    try:
        await manager.send(websocket, {"type": "connected", "message": "个人工作台已连接"})
        while True:
            message = await websocket.receive_json()
            message_type = message.get("type")
            if message_type == "pong":
                last_pong = asyncio.get_running_loop().time()
            elif message_type == "sync_init":
                await manager.send(websocket, {"type": "sync_state", "data": full_state()})
            elif message_type == "sync_request":
                last_seq = max(0, int(message.get("last_seq", 0)))
                messages = manager.get_messages_since(last_seq)
                if messages:
                    for item in messages:
                        await manager.send(websocket, item)
                else:
                    await manager.send(websocket, {"type": "sync_state", "data": full_state()})
            elif message_type in {"task_move", "task_updated", "note_edit"}:
                await manager.broadcast({"type": message_type, "data": message.get("data", message)}, exclude=[websocket])
            elif message_type == "github_refresh":
                await manager.broadcast({"type": "github_refresh_requested"}, exclude=[websocket])
            else:
                await manager.send(websocket, {"type": "error", "message": f"未知消息类型: {message_type}"})
    except WebSocketDisconnect:
        manager.disconnect(websocket)
    except Exception:
        manager.disconnect(websocket)
    finally:
        heartbeat_task.cancel()

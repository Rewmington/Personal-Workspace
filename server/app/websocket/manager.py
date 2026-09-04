from __future__ import annotations

from collections.abc import Iterable

from fastapi import WebSocket


class ConnectionManager:
    def __init__(self) -> None:
        self.active: list[WebSocket] = []
        self._seq_counter = 0
        self._message_buffer: list[dict] = []
        self._max_buffer = 500

    async def connect(self, websocket: WebSocket) -> None:
        await websocket.accept()
        self.active.append(websocket)

    def disconnect(self, websocket: WebSocket) -> None:
        if websocket in self.active:
            self.active.remove(websocket)

    async def send(self, websocket: WebSocket, payload: dict) -> None:
        await websocket.send_json(payload)

    def get_messages_since(self, last_seq: int) -> list[dict]:
        return [message for message in self._message_buffer if message["seq"] > last_seq]

    def _record(self, payload: dict) -> dict:
        self._seq_counter += 1
        message = {**payload, "seq": self._seq_counter}
        self._message_buffer.append(message)
        if len(self._message_buffer) > self._max_buffer:
            del self._message_buffer[:len(self._message_buffer) - self._max_buffer]
        return message

    async def broadcast(self, payload: dict, exclude: Iterable[WebSocket] | None = None) -> None:
        payload = self._record(payload)
        excluded = set(exclude or [])
        stale = []
        for websocket in list(self.active):
            if websocket in excluded:
                continue
            try:
                await websocket.send_json(payload)
            except Exception:
                stale.append(websocket)
        for websocket in stale:
            self.disconnect(websocket)


manager = ConnectionManager()

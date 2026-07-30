from __future__ import annotations

import os
import sys
import tempfile
from pathlib import Path

# Keep the test database outside the repository so the suite does not depend
# on workspace permissions or leave test data beside the source files.
_test_db_dir = tempfile.TemporaryDirectory(prefix="personal-workstation-tests-")
os.environ["WORKSTATION_DB"] = os.path.join(_test_db_dir.name, "test.db")
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from fastapi.testclient import TestClient

from app.main import app


def test_health_and_seeded_board():
    with TestClient(app) as client:
        assert client.get("/api/health").json()["status"] == "ok"
        boards = client.get("/api/boards").json()
        assert boards and boards[0]["name"]
        columns = client.get(f"/api/boards/{boards[0]['id']}/tasks").json()
        assert len(columns) >= 3


def test_task_and_note_crud():
    with TestClient(app) as client:
        columns = client.get("/api/boards/1/tasks").json()
        task = client.post("/api/tasks", json={"column_id": columns[0]["id"], "title": "测试任务", "priority": "high"})
        assert task.status_code == 201
        task_id = task.json()["id"]
        updated = client.put(f"/api/tasks/{task_id}", json={"title": "已更新任务", "position": 0})
        assert updated.status_code == 200
        assert updated.json()["title"] == "已更新任务"
        assert client.delete(f"/api/tasks/{task_id}").status_code == 204

        note = client.post("/api/notes", json={"title": "测试笔记", "content": "hello", "tags": ["test"]})
        assert note.status_code == 201
        assert client.get("/api/notes", params={"search": "hello"}).json()[0]["title"] == "测试笔记"


def test_websocket_sync():
    with TestClient(app) as client:
        with client.websocket_connect("/ws") as websocket:
            assert websocket.receive_json()["type"] == "connected"
            websocket.send_json({"type": "sync_init"})
            response = websocket.receive_json()
            assert response["type"] == "sync_state"
            assert "boards" in response["data"]
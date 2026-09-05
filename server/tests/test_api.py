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


def test_backup_json_export_and_merge_import():
    with TestClient(app) as client:
        client.post("/api/notes", json={"title": "备份测试", "content": "# Markdown", "tags": ["backup"]})
        exported = client.post("/api/backup/export", params={"format": "json"})
        assert exported.status_code == 200
        assert exported.headers["content-type"].startswith("application/json")
        assert "personal-workstation-backup" in exported.text

        restored = client.post(
            "/api/backup/import",
            params={"mode": "merge"},
            files={"file": ("workstation.json", exported.content, "application/json")},
        )
        assert restored.status_code == 200
        assert restored.json()["ok"] is True


def test_rest_task_change_is_broadcast_with_sequence():
    with TestClient(app) as client:
        with client.websocket_connect("/ws") as websocket:
            assert websocket.receive_json()["type"] == "connected"
            columns = client.get("/api/boards/1/tasks").json()
            created = client.post("/api/tasks", json={"column_id": columns[0]["id"], "title": "实时广播测试"})
            event = websocket.receive_json()
            assert event["type"] == "task_created"
            assert event["seq"] > 0
            client.delete(f"/api/tasks/{created.json()['id']}")

def test_productivity_endpoints():
    with TestClient(app) as client:
        created = client.post("/api/snippets", json={"title": "pytest", "code": "assert 1", "language": "python", "tags": ["test"]})
        assert created.status_code == 201
        snippet_id = created.json()["id"]
        assert client.get("/api/snippets/meta").json()["languages"]["python"] >= 1
        assert client.delete(f"/api/snippets/{snippet_id}").status_code == 204
        session = client.post("/api/focus/start", json={"duration": 60})
        assert session.status_code == 200
        assert client.put(f"/api/focus/{session.json()['id']}/interrupt", json={"actual": 1}).status_code == 200
        log = client.get("/api/logs/today")
        assert log.status_code == 200
        assert client.put(f"/api/logs/{log.json()['id']}", json={"content": "test"}).status_code == 200


def test_notify_broadcast():
    with TestClient(app) as client:
        with client.websocket_connect("/ws") as websocket:
            assert websocket.receive_json()["type"] == "connected"
            resp = client.post("/api/notify", json={"title": "测试", "message": "hi", "type": "success"})
            assert resp.status_code == 200 and resp.json()["ok"] is True
            event = websocket.receive_json()
            assert event["type"] == "notify"
            assert event["data"]["title"] == "测试"
            assert event["data"]["type"] == "success"


def test_clipboard_post_broadcasts(monkeypatch):
    """手动添加剪贴板 → WS 客户端收到 clipboard 事件（不真实改写系统剪贴板）。"""
    monkeypatch.setattr("app.clipboard_sync.set_clipboard_text", lambda text: True)
    monkeypatch.setattr("app.clipboard_sync.get_clipboard_text", lambda: None)
    with TestClient(app) as client:
        with client.websocket_connect("/ws") as websocket:
            assert websocket.receive_json()["type"] == "connected"
            resp = client.post("/api/clipboard", json={"content": "sync-test-内容", "source": "manual"})
            assert resp.status_code == 200 and resp.json()["ok"] is True
            event = websocket.receive_json()
            assert event["type"] == "clipboard"
            assert event["data"]["content"] == "sync-test-内容"
            assert event["data"]["source"] == "manual"


def test_clipboard_publish_dedup(monkeypatch):
    """相同文本只发布一次（去重防回环），不同文本正常发布。"""
    import asyncio

    from app.clipboard_sync import publish_clipboard, state

    calls: list[dict] = []

    async def fake_broadcast(payload, exclude=None):
        calls.append(payload)

    monkeypatch.setattr("app.clipboard_sync.manager.broadcast", fake_broadcast)
    monkeypatch.setattr("app.clipboard_sync.add_clipboard_record", lambda content, source="manual": True)

    state.last_text = None

    async def go():
        await publish_clipboard("相同文本", "windows")
        await publish_clipboard("相同文本", "windows")
        await publish_clipboard("另一文本", "windows")

    asyncio.run(go())
    state.last_text = None  # 还原全局状态
    assert [c["data"]["content"] for c in calls] == ["相同文本", "另一文本"]


def test_clipboard_sync_toggle():
    """开关 API：PUT 关闭 → GET 为 false；恢复 true（settings.json 值还原默认）。"""
    with TestClient(app) as client:
        client.put("/api/clipboard/sync", json={"enabled": False})
        assert client.get("/api/clipboard/sync").json()["enabled"] is False
        client.put("/api/clipboard/sync", json={"enabled": True})
        assert client.get("/api/clipboard/sync").json()["enabled"] is True

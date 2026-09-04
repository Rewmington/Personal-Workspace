# 通知接力链路（电脑通知 → 手机提醒）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 打通"电脑端任意事件 → 服务端广播 → 手机系统通知"链路：新增 `POST /api/notify`，服务端用既有 WebSocket 广播 `notify` 事件，并让 `notify.ps1`（Claude 钩子 / 任意脚本）在发 Windows Toast 的同时推一条到手机。**与本仓库其它两端（Web/android 的接收端）配合即可闭环。**

**Architecture:** 纯新增下行通知通道，复用现有 `ConnectionManager.broadcast`（`server/app/websocket/manager.py`）。服务端加一个 router；`notify.ps1` 加一个静默 HTTP 调用。不改既有 API/WebSocket 行为；不落库（MVP 仅广播）。

**Tech Stack:** Python FastAPI + WebSocket（已有）；PowerShell `Invoke-RestMethod`（notify.ps1）。

**Spec:** `docs/superpowers/specs/2026-09-04-personal-workstation-beautify-design.md` §3（3.2 服务端接口 / 3.3 WS 事件 / 3.5 notify.ps1 接入 / 3.6 通用性）+ §4 硬约束 + §5 降级。

## Global Constraints

- **纯新增通知通道**，不改任何既有 API 路由、WS 接收/业务逻辑、数据库表。
- **零云/局域网**；无鉴权（局域网内部调用）；只在手机连着工作台时投递。
- `notify.ps1` 改动**绝不破坏现有 Windows Toast 与日志**——HTTP 调用必须 `try/catch` + `-ErrorAction SilentlyContinue`，工作台未起/请求失败时**静默失败**。
- 通知**不落库**（MVP 仅广播；历史留后续迭代）。
- `type` 白名单：`info / success / warning / confirm`，非法值回落 `info`。
- 广播 payload：`{"type":"notify","data":{"title":…,"message":…,"type":…}}`（带 seq，`ConnectionManager` 自动加）。
- 验收：`pytest` 新增测试通过；`curl POST /api/notify` 能被 WS 客户端收到；notify.ps1 不破坏现有行为。

---

### Task 1: 服务端 `POST /api/notify` + WS 广播（含测试）

**Files:**
- Create: `server/app/api/notify.py`
- Modify: `server/app/main.py`（import + `include_router`）
- Test: `server/tests/test_api.py`（追加 `test_notify_broadcast`）

**Interfaces:**
- Consumes: `ConnectionManager.broadcast`（`server/app/websocket/manager.py`，async）。
- Produces: `POST /api/notify`，body `{title,message,type}` → `{"ok":true}` 并 broadcast；路由名 `notify.router`（tag `notify`）。

- [ ] **Step 1: 写失败测试**（追加到 `server/tests/test_api.py`）

```python
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
```

- [ ] **Step 2: 运行确认失败**

Run: `cd server && .venv/Scripts/python -m pytest tests/test_api.py::test_notify_broadcast -v`
Expected: 失败（route 不存在 → 404/断言失败）。

- [ ] **Step 3: 新建 `server/app/api/notify.py`**

```python
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
```

- [ ] **Step 4: 注册路由（`server/app/main.py`）**

在 `from .api import …` 导入段加入 `notify`，在 `app.include_router(…)` 段加入 `app.include_router(notify.router)`。

- [ ] **Step 5: 运行确认通过 + 全量**

Run: `cd server && .venv/Scripts/python -m pytest tests/test_api.py -v`
Expected: 7 passed（原 6 + 新 notify）。

- [ ] **Step 6: 提交**

```bash
git add server/app/api/notify.py server/app/tests/__init__.py server/tests/test_api.py server/app/main.py
git commit -m "feat(server): /api/notify + WebSocket 广播 notify 事件"
```

---

### Task 2: `notify.ps1` 接入（发 Toast 同时推手机，防呆）

**Files:**
- Modify: `.claude/hooks/notify.ps1`

**Interfaces:**
- Consumes: 既有变量 `$Title/$Message/$Type`（本脚本已有）。
- Produces: 发完 Windows Toast 后，静默 POST 到工作台 `/api/notify`。

- [ ] **Step 1: 在 Toast 展示后追加静默推送**

在 `notify.ps1` 的 `$notifier.Show($toast)` 之后（仍在 `try` 块内或其后，但用独立 `try/catch`）追加：

```powershell
# Push to Personal Workstation (silent — never breaks the existing toast)
try {
    $notifyHost = if ($env:WORKSTATION_NOTIFY_HOST) { $env:WORKSTATION_NOTIFY_HOST } else { "127.0.0.1" }
    $notifyPort = if ($env:WORKSTATION_NOTIFY_PORT) { $env:WORKSTATION_NOTIFY_PORT } else { "8080" }
    $notifyBody = @{ title = $Title; message = $Message; type = $Type } | ConvertTo-Json -Compress
    Invoke-RestMethod -Uri "http://${notifyHost}:${notifyPort}/api/notify" -Method Post -ContentType "application/json" -Body $notifyBody -TimeoutSec 3 -ErrorAction Stop | Out-Null
} catch {
    "[$timestamp] notify-push skipped: $($_.Exception.Message)" | Add-Content -Path $logFile -Encoding UTF8
}
```

> 说明：`$timestamp`/`$logFile` 本脚本已有；`-TimeoutSec 3` 与 `-ErrorAction Stop` 保证失败即静默（工作台未起时不阻塞、不报错）。

- [ ] **Step 2: 验证（静态）**

Run: `powershell.exe -ExecutionPolicy Bypass -NoProfile -Command "& { $t=Get-Date; . .\\.claude\\hooks\\notify.ps1 -Title 't' -Message 'm' -Type info }"`（或最简：`Get-Content .claude/hooks/notify.ps1 | Select-String -Pattern 'api/notify|Invoke-RestMethod|try {'`）
Expected: 命中 `/api/notify`、`Invoke-RestMethod`、独立 `try`。

- [ ] **Step 3: 提交**

```bash
git add .claude/hooks/notify.ps1
git commit -m "feat(hooks): notify.ps1 推送消息到工作台（静默，不破坏 Toast）"
```

---

### Task 3: 端到端验证（curl 触发 → WS 收到）

**Files:** 无（验证）

- [ ] **Step 1: 启动服务端**

Run: `cd server && .venv/Scripts/python run_server.py`（后台）
Expected: 监听 8080。`curl http://127.0.0.1:8080/api/health` → 200。

- [ ] **Step 2: 手动触发 + 用 python 收 WS 广播**

```bash
curl -X POST http://127.0.0.1:8080/api/notify -H "Content-Type: application/json" -d '{"title":"任务完成","message":"6/10","type":"success"}'
```
另开终端：`cd server && .venv/Scripts/python -c "import asyncio,websockets,json; async def go():\n  async with websockets.connect('ws://127.0.0.1:8080/ws') as w:\n    await w.send(json.dumps({'type':'sync_request','last_seq':0}))\n    print('recv:', await w.recv())\nasyncio.run(go())"`
Expected: 能收到含 `"type":"notify"` 的消息。

- [ ] **Step 3: 回归**（确认未破坏既有）

Run: `cd server && .venv/Scripts/python -m pytest tests/test_api.py -v`
Expected: 7 passed。既有 `test_websocket_sync` / `test_rest_task_change_is_broadcast_with_sequence` 仍过（notify 未改 handler/manager）。

- [ ] **Step 4: 提交（若有调整）**

```bash
git add -A && git commit -m "feat(notify): 端到端验证"
```

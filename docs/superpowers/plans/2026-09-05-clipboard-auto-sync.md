# 剪贴板自动同步（电脑 ↔ 手机）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 打通"电脑与手机剪贴板自动双向同步"：任一端复制文本，另一端自动写入系统剪贴板。基于既有 `/api/clipboard` 历史与 WebSocket 广播扩展，不引入新依赖（Windows 剪贴板用标准库 ctypes 读写）。

**背景决策（用户已确认）:**
- Windows 端监听放在**服务端 Python 进程内**（ctypes 轮询，无新依赖；服务端常驻即生效，不依赖桌面壳）。
- 收到另一端推送时**自动写入本机系统剪贴板**（默认开启，可在 Web 设置页关闭）。

**Architecture:**
- 服务端 = Windows 系统：`clipboard_win.py`(ctypes 读/写系统剪贴板) + `clipboard_sync.py`(共享去重状态 `last_text` + `publish_clipboard()` 入库+WS 广播 + `clipboard_monitor_loop()` 后台轮询)。
- 广播 payload：`{"type":"clipboard","data":{"content":…,"source":…}}`（seq 由 `ConnectionManager` 自动加）。
- 回环防护：**文本相等去重**——服务端 `last_text`、Android 端 `lastLocal` 各自记录最近已知文本，相同即忽略；服务端 `last_text` 在收到上报并写入本机剪贴板时同步更新，monitor 下次读到相同文本不会重复广播。
- 开关：`settings.clipboard_sync_enabled`（`settings.json` 持久化 + 环境变量 `WORKSTATION_CLIPBOARD_SYNC` 覆盖，默认 true）；关闭时 monitor 不读剪贴板、POST 不写本机剪贴板不广播（仅存历史）；运行时可由 Web 设置页切换。
- **零云/局域网**；同步仅在手机连接工作台时生效；不新增第三方推送。

**Tech Stack:** Python FastAPI（已有）+ ctypes (Win32) + Kotlin `ClipboardManager.OnPrimaryClipChangedListener` + JS WebSocket（已有）。

## Global Constraints

- 不改/不删任何既有 API 路由、WS 事件解析、数据库表；`clipboard_history` 20 条上限逻辑保留。
- `tests/test_web.py` 的 18 项锚点断言、`tests/test_api.py` 既有 7 项必须继续通过。
- 测试环境不得真实改写系统剪贴板：写剪贴板的调用在测试中用 monkeypatch 隔离。
- 剪贴板同步是"附加能力"：任何读/写/广播失败都必须静默降级，不影响服务端主流程（尤其 monitor 循环用 try/except 包住，绝不因剪贴板异常杀死循环）。
- 每次广播/入库的文本截断：入库沿用现有 `strip()` 校验；广播内容最长为 80 字符截断（防空转？不——**保持原文本**，只有标题类才截断；此处直接透传，历史长度已由数据库限制）。

## Task 1: Windows 剪贴板读写模块（ctypes）

**Files:**
- Create: `server/app/clipboard_win.py`

**Interfaces:**
- Produces: `get_clipboard_text() -> str | None`（读 CF_UNICODETEXT，OpenClipboard 失败/无文本返回 None）；`set_clipboard_text(text: str) -> bool`（Empty 后 Set，系统接管内存不手动释放）。

- [ ] **Step 1: 新建 `clipboard_win.py`**
```python
"""Windows 系统剪贴板读写（ctypes，零第三方依赖）。"""
from __future__ import annotations

import ctypes
from ctypes import wintypes  # noqa: F401  (确保 user32/kernel32 类型注册)

CF_UNICODETEXT = 13
GMEM_MOVEABLE = 0x0002
GMEM_ZEROINIT = 0x0040

def get_clipboard_text() -> str | None:
    user32 = ctypes.windll.user32
    if not user32.OpenClipboard(None):
        return None
    try:
        if not user32.IsClipboardFormatAvailable(CF_UNICODETEXT):
            return None
        handle = user32.GetClipboardData(CF_UNICODETEXT)
        if not handle:
            return None
        kernel32 = ctypes.windll.kernel32
        ptr = kernel32.GlobalLock(handle)
        if not ptr:
            return None
        try:
            return ctypes.wstring_at(ptr).rstrip("\x00") or None
        finally:
            kernel32.GlobalUnlock(handle)
    finally:
        user32.CloseClipboard()

def set_clipboard_text(text: str) -> bool:
    user32 = ctypes.windll.user32
    if not user32.OpenClipboard(None):
        return False
    try:
        user32.EmptyClipboard()
        data = (text + "\x00").encode("utf-16-le")
        kernel32 = ctypes.windll.kernel32
        handle = kernel32.GlobalAlloc(GMEM_MOVEABLE | GMEM_ZEROINIT, len(data))
        if not handle:
            return False
        ptr = kernel32.GlobalLock(handle)
        ctypes.memmove(ptr, data, len(data))
        kernel32.GlobalUnlock(handle)
        user32.SetClipboardData(CF_UNICODETEXT, handle)  # 系统接管，勿手动释放
        return True
    except Exception:
        return False
    finally:
        try:
            user32.CloseClipboard()
        except Exception:
            pass
```

- [ ] **Step 2: 验证（Windows 本机可用）**
Run: `cd server && .venv/Scripts/python.exe -c "from app.clipboard_win import set_clipboard_text,get_clipboard_text; t='pw-clip-'+__import__('time').strftime('%H%M%S'); assert set_clipboard_text(t) and get_clipboard_text()==t; print('readback ok:', t)"`（测试会真实写系统剪贴板，属预期冒烟；注意会覆盖当前剪贴板，先复制缓存或接受覆盖）。

- [ ] **Step 3: 提交**
```bash
git add server/app/clipboard_win.py
git commit -m "feat(server): Windows 剪贴板读写模块(ctypes)"
```

---

## Task 2: 同步去重状态 + 发布 + 后台 monitor 循环

**Files:**
- Create: `server/app/clipboard_sync.py`
- Modify: `server/app/main.py`（lifespan 挂载/取消 monitor 循环）

**Interfaces:**
- Consumes: `clipboard_win.get_clipboard_text`、`clipboard.py` 的入库函数、`websocket.manager.manager.broadcast`（async）、`settings.clipboard_sync_enabled`。
- Produces: `ClipboardSyncState.last_text`；`publish_clipboard(text, source)`（async：入库 + 广播 + 更新 last_text）；`clipboard_monitor_loop(interval)`（async 常驻）。

- [ ] **Step 1: 新建 `clipboard_sync.py`**
```python
"""剪贴板自动同步：去重状态 + 发布 + 后台轮询（Windows 服务端本机）。"""
from __future__ import annotations

import asyncio
import logging

from .clipboard_win import get_clipboard_text, set_clipboard_text
from .config import settings
from .dat{...}  # 复用 clipboard.py 的入库
from .websocket.manager import manager

_logger = logging.getLogger("clipboard.sync")


class ClipboardSyncState:
    def __init__(self) -> None:
        self.last_text: str | None = None
```

**发布与入库复用 `clipboard.py` 现有逻辑**：为避免重复实现入库，把 `clipboard.py` 的"插入+裁剪 20 条"抽成 `add_clipboard_record(content, source) -> bool`（模块级函数），`clipboard.py` 的 REST handler 与 `clipboard_sync.publish_clipboard` 共用。

```python
async def publish_clipboard(text: str, source: str) -> None:
    """入库 + 广播 + 更新 last_text（调用方保证已通过去重）。"""
    add_clipboard_record(text, source)
    state.last_text = text
    await manager.broadcast({"type": "clipboard", "data": {"content": text, "source": source}})


async def handle_local_text(text: str | None, source: str) -> bool:
    """统一处理一段新文本：去重；需要写本机剪贴板时先写（手机/Web 上报）。返回是否发布。"""
    if settings.clipboard_sync_enabled is False:
        return False
    if not text or text == state.last_text:
        return False
    changed = False
    if source != "windows":
        changed = await asyncio.to_thread(set_clipboard_text, text)  # 把内容落到 Windows 剪贴板
    await publish_clipboard(text, source)
    return changed or True


async def clipboard_monitor_loop(interval: float = 0.8) -> None:
    """轮询本机剪贴板，变化即发布（source=windows）。永不因异常退出。"""
    while True:
        try:
            if settings.clipboard_sync_enabled is not False:
                text = await asyncio.to_thread(get_clipboard_text)
                if text and text != state.last_text:
                    try:
                        await publish_clipboard(text, "windows")
                    except Exception:
                        _logger.warning("剪贴板发布失败", exc_info=True)
        except Exception:
            _logger.debug("剪贴板读取异常（忽略）", exc_info=True)
        await asyncio.sleep(interval)
```
> 说明：`source != "windows"` 才写本机剪贴板——monitor 读到的内容本来就在本机剪贴板，无需回写。

- [ ] **Step 2: `clipboard.py` 抽出 `add_clipboard_record` 并让 POST 走 `handle_local_text`**
POST handler 改为：
```python
@router.post("")
async def add_clipboard(body: ClipboardCreate):
    text = body.content.strip()
    if not text:
        return {"ok": False, "error": "内容不能为空"}
    ok = add_clipboard_record(text, body.source)  # 入库（历史始终保留）
    try:
        await handle_local_text(text, body.source)  # 同步逻辑：去重→写本机→广播
    except Exception:
        pass  # 同步失败不阻断历史保存
    return {"ok": ok}
```
> 开关关闭时 `handle_local_text` 直接返回 False，仅入库（手动添加仍可用作历史）。广播失败（如无 WS 连接）也不影响入库。

- [ ] **Step 3: main.py lifespan 挂载/取消 monitor**
```python
from .clipboard_sync import clipboard_monitor_loop
...
_clipboard_task: asyncio.Task | None = None
# lifespan 内：
_clipboard_task = asyncio.create_task(clipboard_monitor_loop())
# 关闭时：
if _clipboard_task:
    _clipboard_task.cancel()
    try: await _clipboard_task
    except asyncio.CancelledError: pass
```

- [ ] **Step 4: 提交**
```bash
git add server/app/clipboard_win.py server/app/clipboard_sync.py server/app/api/clipboard.py server/app/main.py
git commit -m "feat(server): 剪贴板自动同步(去重+发布+monitor 循环)"
```

---

## Task 3: 开关配置 + API

**Files:**
- Modify: `server/app/config.py`（Settings 加 `clipboard_sync_enabled` + `save_clipboard(enabled)`）
- Modify: `server/app/api/clipboard.py`（`GET /api/clipboard/sync` + `PUT /api/clipboard/sync`）

**Interfaces:**
- Produces: `settings.clipboard_sync_enabled: bool`（默认 true，来源 env `WORKSTATION_CLIPBOARD_SYNC`("0"=关) 或 settings.json `clipboard_sync_enabled`）；`PUT /api/clipboard/sync {"enabled": bool}` 运行时切换并持久化。

- [ ] **Step 1: config 加字段与保存方法**（仿 `save_profile` 的原子写）
```python
clipboard_sync_enabled: bool
# 构造时：
clipboard_sync_enabled=os.getenv("WORKSTATION_CLIPBOARD_SYNC", "1") != "0" and bool(_local.get("clipboard_sync_enabled", True)),
```
```python
def save_clipboard(self, enabled: bool) -> None:
    self.clipboard_sync_enabled = bool(enabled)
    values = _read_local_config()
    values["clipboard_sync_enabled"] = self.clipboard_sync_enabled
    # 原子写（同 save_profile 模式）
```

- [ ] **Step 2: clipboard.py 加路由**
```python
@router.get("/sync")
def get_clipboard_sync():
    return {"enabled": settings.clipboard_sync_enabled}

@router.put("/sync")
def set_clipboard_sync(payload: SyncPayload):
    settings.save_clipboard(payload.enabled)
    return {"ok": True, "enabled": settings.clipboard_sync_enabled}
```
（`SyncPayload` 为 `pydantic.BaseModel`，字段 `enabled: bool`。注意路由顺序：`/sync` 必须在 `/{item_id}` 之前声明，避免被参数路由吞掉。）

- [ ] **Step 3: 提交**
```bash
git add server/app/config.py server/app/api/clipboard.py
git commit -m "feat(server): 剪贴板同步开关(settings+API)"
```

---

## Task 4: 服务端测试（TDD 补全）

**Files:**
- Modify: `server/tests/test_api.py`（追加 3 项）

- [ ] **Step 1: 写测试**
```python
def test_clipboard_post_broadcasts(monkeypatch):
    # 防止真实改写系统剪贴板
    monkeypatch.setattr("app.clipboard_sync.set_clipboard_text", lambda t: True)
    with TestClient(app) as client:
        with client.websocket_connect("/ws") as ws:
            assert ws.receive_json()["type"] == "connected"
            resp = client.post("/api/clipboard", json={"content": "sync-test-内容", "source": "manual"})
            assert resp.status_code == 200 and resp.json()["ok"] is True
            event = ws.receive_json()
            assert event["type"] == "clipboard" and event["data"]["content"] == "sync-test-内容"


def test_clipboard_publish_dedup(monkeypatch):
    calls = []
    async def fake_broadcast(payload, exclude=None):
        calls.append(payload)
    monkeypatch.setattr("app.clipboard_sync.manager.broadcast", fake_broadcast)
    monkeypatch.setattr("app.clipboard_sync.add_clipboard_record", lambda *_: True)
    import asyncio
    from app.clipboard_sync import state, publish_clipboard
    state.last_text = None
    async def go():
        await publish_clipboard("相同", "windows")
        await publish_clipboard("相同", "windows")   # 去重：不再发布
        await publish_clipboard("不同", "windows")
    asyncio.run(go())
    assert len(calls) == 2, "相同文本只发布一次"


def test_clipboard_sync_toggle(monkeypatch):
    monkeypatch.setattr("app.api.clipboard.settings.save_clipboard", lambda enabled: None)
    with TestClient(app) as client:
        r = client.put("/api/clipboard/sync", json={"enabled": False})
        assert r.status_code == 200 and r.json()["enabled"] is False
        assert client.get("/api/clipboard/sync").json()["enabled"] is False
        client.put("/api/clipboard/sync", json={"enabled": True})
```
> 注：去重测试里直接 `publish_clipboard` 两次"相同"文本——去重检查放在 `handle_local_text`/monitor，而 `publish_clipboard` 本身不判重。为符合"发布前判重"设计，`publish_clipboard` 内部加一行 `if text == state.last_text: return` 亦可（双保险）。采用后者：`publish_clipboard` 自带判重。

- [ ] **Step 2: 运行**
Run: `cd server && .venv/Scripts/python.exe -m pytest tests/test_api.py -v`
Expected: 10 passed（原 7 + 新 3）。

- [ ] **Step 3: 提交**
```bash
git add server/tests/test_api.py
git commit -m "test(server): 剪贴板同步广播/去重/开关"
```

---

## Task 5: Web 端（设置开关 + 剪贴板事件刷新）

**Files:**
- Modify: `web/app.js`

**Interfaces:**
- Consumes: `GET/PUT /api/clipboard/sync`；现有 WS onmessage。
- Produces: 设置页"剪贴板自动同步"开关（checkbox）；剪贴板 tab 收 `clipboard` 事件自动刷新列表。

- [ ] **Step 1: 设置页加开关**
在 `renderSettings()`（或设置表单尾部）注入一行：
```js
<div class="setting-row"><label><input type="checkbox" id="clipboard-sync-toggle"> 剪贴板自动同步（电脑 ↔ 手机复制即共享）</label></div>
```
绑定：加载时 `GET /api/clipboard/sync` 填状态；change 时 `PUT {"enabled": checked}`，失败 toast。
> 不改任何既有 id/class 锚点；新元素 id `clipboard-sync-toggle` 唯一。

- [ ] **Step 2: WS 收 clipboard 事件刷新**
在 `ReconnectingWebSocket.onmessage` 的 `clipboard` 分支：
```js
if (message.type === "clipboard") {
  if (state.view === "tools" && state.tool === "clipboard") {
    window.clearTimeout(realtimeRefreshTimer);
    realtimeRefreshTimer = window.setTimeout(() => bindClipboardTool?renderClipboard(), 150);
  }
  return;
}
```
> 浏览器无权可靠地自动写系统剪贴板（需焦点+权限），Web 端仅刷新列表；系统剪贴板由服务端统一写入。

- [ ] **Step 3: 验证**
Run: `cd server && .venv/Scripts/python.exe -m pytest tests/test_web.py -q`
Expected: 18 passed（服务端需在运行）。浏览器人工：设置页出现开关并持久化；剪贴板 tab 复制动作后列表刷新。

- [ ] **Step 4: 提交**
```bash
git add web/app.js
git commit -m "feat(web): 剪贴板同步开关 + clipboard 事件刷新列表"
```

---

## Task 6: Android 端（监听上报 + 接收写入）

**Files:**
- Modify: `android/.../core/network/ApiClient.kt`（listenRealtime 加 onClipboard）
- Modify: `android/.../MainActivity.kt`（ClipboardManager 监听器 + 上报 + 接收写入）

**Interfaces:**
- Consumes: `client.clipboardAdd(ClipboardCreateRequest(text, "android"))`（已有）、`ClipboardManager`（系统服务，无需权限）。
- Produces: 手机本机剪贴板变化 → 自动上报服务端；WS `clipboard` 事件 → 自动写入手机剪贴板。

- [ ] **Step 1: ApiClient.kt `listenRealtime` 加 `onClipboard`**
```kotlin
suspend fun listenRealtime(
    onEvent: suspend () -> Unit,
    onStatus: (String) -> Unit,
    onNotify: suspend (title: String, message: String, type: String) -> Unit = { _, _, _ -> },
    onClipboard: suspend (content: String, source: String) -> Unit = { _, _ -> },
) {
...
"clipboard" -> {
    val data = json["data"]?.jsonObject
    onClipboard(
        data?.get("content")?.toString()?.trim('"') ?: "",
        data?.get("source")?.toString()?.trim('"') ?: "",
    )
}
```

- [ ] **Step 2: MainActivity.kt**
WorkstationApp 内（靠近现有 `LaunchedEffect(client)`）：
```kotlin
var clipboardLastText by remember { mutableStateOf("") }
val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
    scope.launch {
        val clip = clipboardManager.primaryClip
        val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString().orEmpty()
        if (text.isNotBlank() && text != clipboardLastText) {
            clipboardLastText = text
            try { client.clipboardAdd(ClipboardCreateRequest(text, "android")) } catch (_: Exception) {}
        }
    }
}
LaunchedEffect(client) {
    clipboardManager.addPrimaryClipChangedListener(clipListener)
    tryOnDispose { clipboardManager.removePrimaryClipChangedListener(clipListener) }
}
```
`listenRealtime` 调用加：
```kotlin
onClipboard = { content, _ ->
    if (content.isNotBlank() && content != clipboardLastText) {
        clipboardLastText = content
        clipboardManager.setPrimaryClip(ClipData.newPlainText("workstation_sync", content))
    }
},
```
> 回环防护：写入前比对 `clipboardLastText`；`setPrimaryClip` 相同文本通常不触发 listener，即便触发也因 `text == clipboardLastText` 跳过上报。`tryOnDispose` 若不存在，用 `DisposableEffect` 的 `onDispose`。

- [ ] **Step 3: 验证**
Run: `cd android && ./gradlew.bat assembleDebug --console=plain`
Expected: BUILD SUCCESSFUL。真机验收（可选，需同一 WiFi）：手机复制 → 电脑剪贴板出现；电脑复制 → 手机剪贴板出现。

- [ ] **Step 4: 提交**
```bash
git add android/
git commit -m "feat(android): 剪贴板自动同步(监听上报+接收写入)"
```

---

## Task 7: 全量回归 + 真实冒烟

**Files:** 无（验证）
- [ ] **Step 1: 服务端全量**
Run: `cd server && .venv/Scripts/python.exe -m pytest tests/test_api.py tests/test_web.py -q`（先起服务端）
Expected: 10 + 18 passed。
- [ ] **Step 2: 真实剪贴板冒烟（Windows）**
启动服务端，python 脚本：写剪贴板"PW-SMOKE-…"→ 等 ~1.5s → `GET /api/clipboard` 首条应包含该文本且 WS 收到 `clipboard` 事件；再 `PUT /api/clipboard/sync {"enabled":false}` → 再写新文本 → 确认不再出现新广播（历史仍入库）。
- [ ] **Step 3: 提交（如有调整）**
```bash
git add -A && git commit -m "chore: 剪贴板同步全量回归"
```

---

## 验收清单

- [ ] `server/app/clipboard_win.py` 读写本机剪贴板（ctypes，零依赖）
- [ ] `server/app/clipboard_sync.py` 去重发布 + monitor 循环（异常静默）
- [ ] `POST /api/clipboard` 后 WS 收到 `clipboard` 事件；手机/Web 上报内容自动写入 Windows 剪贴板
- [ ] `settings.clipboard_sync_enabled` 开关（settings.json + env + `GET/PUT /api/clipboard/sync`）生效，关闭时不同步只存历史
- [ ] Web 设置页开关 + 剪贴板事件刷新列表
- [ ] Android 监听本机剪贴板变化自动上报；收到广播自动写入本地；两端防回环
- [ ] `test_api.py` 10 passed、`test_web.py` 18 passed、`gradlew assembleDebug` BUILD SUCCESSFUL
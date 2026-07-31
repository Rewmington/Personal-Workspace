# Personal Workstation v3 功能方案（全量）

> 涵盖三个梯队 9 个功能的设计文档 + 执行清单，取代之前的 18 个碎片文件。

---

## 总览

| # | 梯队 | 功能 | 估时 | 端覆盖 | 核心依赖 |
|---|------|------|------|--------|----------|
| 1 | 🥇 补齐短板 | ✅ Markdown 笔记渲染 | 2-3 天 | Web + Android | marked, Markwon |
| 2 | 🥇 补齐短板 | WebSocket 断线重连 | 2-3 天 | Server + Web + Android | 无 |
| 3 | 🥇 补齐短板 | 数据备份恢复 | 2-3 天 | Server + Web + Android | sqlite3.backup() |
| 4 | 🥈 效率工具 | 代码片段管理器 | 2-3 天 | Server + Web + Android | 步骤 1（Markdown） |
| 5 | 🥈 效率工具 | 格式化工具箱 | 1-2 天 | Web + Android（纯前端） | 无 |
| 6 | 🥈 效率工具 | Git 仓库总览 | 3-4 天 | Server + Web + Android | 系统 Git CLI |
| 7 | 🥉 工作流 | 番茄钟专注模式 | 2-3 天 | Server + Web + Android | 步骤 2（WebSocket） |
| 8 | 🥉 工作流 | 每日开发日志 | 2-3 天 | Server + Web + Android | 步骤 1（Markdown） |
| 9 | 🥉 工作流 | 快捷便签 | 1 天 | Web + Android（纯前端） | 无 |

**建议启动顺序**：Markdown 渲染 → WebSocket 重连 → 数据备份 → 代码片段 → 格式化工具箱 → Git 仓库总览 → 番茄钟 → 开发日志 → 快捷便签

---

## 1. ~~Markdown 笔记渲染~~ ✅（Web 端已完成，Android 留待后续轮次）

### 设计

**背景**：当前笔记仅支持纯文本，缺乏代码高亮、表格等格式化能力。Markdown 渲染也是后续代码片段和开发日志的基础设施。

**关键决策**：

| 决策点 | 选择 | 理由 |
|--------|------|------|
| Web 引擎 | marked@4.3.0（锁定 v4） | v5 已移除 setOptions highlight 回调，v4 最后可用版本 |
| Web 语法高亮 | highlight.js@11.9.0 | cdnjs 打包版，零配置自动检测语言 |
| Web 编辑器 | textarea + 预览双栏 | 复杂度最低；后续可升级 CodeMirror |
| Android | Markwon | 专为 Android 设计，内置语法高亮 |
| XSS 防护 | DOMPurify@3.2.6（已实施） | plan 原文「marked v5+ 默认安全」描述有误，marked 并不自动 sanitize，已改用 DOMPurify 清理 |

**数据结构**：无变更。现有 `notes.content TEXT` 直接存 Markdown 原文。

**API**：无新增，复用现有笔记 CRUD。

**交互**：三态切换 —— [编辑] 只显示 textarea、[预览] 只显示渲染结果、[分栏] 左右对照。左侧输入实时更新右侧预览。

**风险**：
- XSS → marked 默认 sanitize，不输出 raw HTML
- Android 大文档卡顿 → Markwon 异步渲染，超阈值切纯文本
- 存量纯文本兼容 → 非 Markdown 语法的文本原样展示

### 执行清单

**步骤 1 —— Web 端引入依赖**（`web/index.html`）

```html
<script src="https://cdn.jsdelivr.net/npm/marked/marked.min.js"></script>
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/highlight.js@11/styles/github-dark.css">
<script src="https://cdn.jsdelivr.net/npm/highlight.js@11/lib/highlight.min.js"></script>
```

**步骤 2 —— Web 端渲染函数**（`web/app.js`）

```javascript
marked.setOptions({
  highlight: function(code, lang) {
    if (lang && hljs.getLanguage(lang)) return hljs.highlight(code, { language: lang }).value;
    return hljs.highlightAuto(code).value;
  },
  breaks: true, gfm: true
});

function renderMarkdown(rawMd) {
  if (!rawMd) return '';
  return marked.parse(rawMd);
}
```

**步骤 3 —— Web 端编辑/预览双栏**（`web/index.html` + `web/app.js`）

- 笔记模态框 `.modal-body` 拆分为 `.note-editor`（textarea）和 `.note-preview`（div）
- 三个切换按钮 `[编辑] [预览] [分栏]`
- textarea `oninput` 调用 `renderMarkdown` 实时更新预览区

**步骤 4 —— Web 端 Markdown 预览样式**（`web/styles.css`）

```css
.note-preview { padding: 16px; overflow-y: auto; }
.note-preview pre { background: var(--panel-bg); padding: 12px; border-radius: 8px; }
.note-preview code { font-family: 'Fira Code', monospace; font-size: 13px; }
.note-preview blockquote { border-left: 3px solid var(--accent); padding-left: 12px; color: var(--text-secondary); }
.note-preview table { border-collapse: collapse; width: 100%; }
.note-preview th, .note-preview td { border: 1px solid var(--border); padding: 6px 12px; }
.note-preview input[type="checkbox"] { margin-right: 6px; }
```

**步骤 5 —— 保存 Markdown 原文**（`web/app.js`）

`saveNote()` 中 `content` 始终取 textarea 原始值，不做 HTML 转换。

**步骤 6 —— Android 端引入 Markwon**（`android/app/build.gradle.kts`）

```kotlin
implementation("io.noties.markwon:core:4.6.2")
implementation("io.noties.markwon:ext-strikethrough:4.6.2")
implementation("io.noties.markwon:ext-tables:4.6.2")
implementation("io.noties.markwon:ext-tasklist:4.6.2")
implementation("io.noties.markwon:syntax-highlight:4.6.2")
implementation("io.noties.markwon:image:4.6.2")
```

**步骤 7 —— Android 端 Markdown 预览组件**（`MainActivity.kt`）

```kotlin
@Composable
fun MarkdownPreview(content: String) {
    val markwon = remember { Markwon.builder(context).build() }
    AndroidView(factory = { ctx ->
        Markwon.create(ctx).apply { setMarkdown(this, content) }
    })
}
```

NoteEditScreen 中添加预览切换按钮。

**步骤 8 —— 端到端验证**

1. Web 端创建含标题、代码块、表格、任务列表、链接的笔记 → 预览正确
2. Electron 桌面端预览正确
3. Android 端预览正确
4. 两端编辑保存后互相查看一致
5. 边界情况（空内容、纯中文）不报错

---

## 2. WebSocket 断线重连

### 设计

**背景**：WebSocket 断线后不回自动恢复，导致桌面端和 Android 端丢失实时推送。

**关键决策**：

| 决策点 | 选择 | 理由 |
|--------|------|------|
| 重连策略 | 指数退避 + 随机抖动 | 避免重连风暴 |
| 退避参数 | 1s→2s→4s...上限 30s ±25% | 快速恢复短断线 |
| 增量同步 | 服务端消息序号 | 精确，无需时钟同步 |
| 心跳 | 服务端 30s ping | 主动探测死连接 |
| 状态恢复 | 重连后自动重新订阅 | 与现有 subscribe 兼容 |

**消息协议扩展**：每个广播消息携带递增 `seq` 字段。客户端重连时发送 `{type: "sync_request", last_seq: N}`，服务端补发遗漏消息。

**连接状态 UI**：绿色"已连接"、黄色旋转"重连中"、红色"已断开 [重连]"。

**风险**：
- 重连风暴 → ±25% 随机抖动
- 消息积压 → 队列上限 500 条，超量丢弃最早消息；重要变更用 REST API 兜底
- 旧连接未清理 → connect() 前先 close() 旧连接

### 执行清单

**步骤 1 —— 服务端消息序号**（`server/app/websocket/manager.py`）

```python
class WebSocketManager:
    def __init__(self):
        self._seq_counter = 0
        self._message_buffer = []   # 最多 500 条
        self._max_buffer = 500

    def _next_seq(self) -> int:
        self._seq_counter += 1
        return self._seq_counter

    def get_messages_since(self, last_seq: int) -> list:
        return [m for m in self._message_buffer if m["seq"] > last_seq]
```

`broadcast()` 自动附加 seq 并存入 buffer。

**步骤 2 —— 服务端心跳 + 增量同步处理**（`server/app/websocket/handler.py`）

- 每 30s 发 ping，10s 无 pong 断开
- 处理 `sync_request`：补发 `last_seq` 之后的消息
- 处理 `subscribe`：注册频道

**步骤 3 —— Web 端 ReconnectingWebSocket 类**（`web/app.js`）

```javascript
class ReconnectingWebSocket {
  constructor(url, options = {}) {
    this.url = url;
    this.maxDelay = options.maxDelay || 30000;
    this.baseDelay = options.baseDelay || 1000;
    this.retryCount = 0;
    this.lastSeq = 0;
    this._status = 'disconnected';
    this.connect();
  }

  connect() {
    if (this.ws) { this.ws.onclose = null; this.ws.close(); }
    this._setStatus('connecting');
    this.ws = new WebSocket(this.url);
    this.ws.onopen = () => {
      this.retryCount = 0;
      this._setStatus('connected');
      this.ws.send(JSON.stringify({ type: 'sync_request', last_seq: this.lastSeq }));
    };
    this.ws.onmessage = (e) => {
      const msg = JSON.parse(e.data);
      if (msg.seq) this.lastSeq = msg.seq;
      if (msg.type === 'ping') this.ws.send(JSON.stringify({ type: 'pong' }));
      if (this.onMessage) this.onMessage(msg);
    };
    this.ws.onclose = () => this._scheduleReconnect();
  }

  _scheduleReconnect() {
    this._setStatus('reconnecting');
    const delay = Math.min(this.baseDelay * Math.pow(2, this.retryCount), this.maxDelay)
                  * (0.75 + Math.random() * 0.5);
    this.retryCount++;
    setTimeout(() => this.connect(), delay);
  }

  _setStatus(s) { this._status = s; if (this.onStatusChange) this.onStatusChange(s); }
}
```

**步骤 4 —— Web 端连接状态 UI**（`web/index.html` + `web/app.js` + `web/styles.css`）

侧边栏底部添加状态指示器，三种颜色/动画 + 手动重连按钮。

**步骤 5 —— Android 端自动重连**（`MainActivity.kt`）

用 `Handler.postDelayed` 实现退避重连，维护 `lastSeq`，暴漏 `StateFlow<ConnectionStatus>`。

```kotlin
enum class ConnectionStatus { CONNECTED, RECONNECTING, DISCONNECTED }
class ReconnectingWebSocketClient(private val url: String) {
    private val _status = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val status: StateFlow<ConnectionStatus> = _status

    private fun scheduleReconnect() {
        val delay = minOf(1000L * (1L shl retryCount), 30000L) * (0.75 + Random.nextDouble() * 0.5).toLong()
        retryCount++
        handler.postDelayed({ connect() }, delay)
    }
}
```

**步骤 6 —— Android 端状态 UI**（`MainActivity.kt`）

设置页顶部连接状态条，`collectAsState()` 观察状态变化。

**步骤 7 —— 端到端验证**

1. 双端同时连接 → 断 Wi-Fi 10s → 恢复后自动重连
2. 重启服务 → 自动重连
3. 重连后任务变更被同步
4. 断开期间创建任务 → 重连后通过 sync_request 收到
5. 三端同时重连无风暴日志

---

## 3. 数据备份与恢复

### 设计

**背景**：SQLite 数据全在本地，一旦误删或磁盘损坏就永久丢失。

**关键决策**：

| 决策点 | 选择 | 理由 |
|--------|------|------|
| 备份格式 | SQLite .db + JSON | .db 完整恢复，JSON 可读可脚本处理 |
| 热备份 | sqlite3.Connection.backup() | 原生支持，逐页拷贝，不阻塞读写 |
| 自动备份 | asyncio.create_task + sleep | 零额外依赖 |
| 恢复策略 | "替换"和"合并"两选项 | 灵活应对不同场景 |
| 保留策略 | 最近 N 份（默认 5） | 简单直接 |

**API**：

```
POST   /api/backup/export?format=sqlite|json   # 导出
POST   /api/backup/import                       # 恢复（multipart: file + mode）
GET    /api/backup/schedule                     # 获取自动备份计划
PUT    /api/backup/schedule                     # 更新计划
GET    /api/backup/history                      # 备份文件列表
DELETE /api/backup/files/{filename}             # 删除单个备份
```

**配置**（`server/data/settings.json`）：

```json
{
  "backup": {
    "enabled": true,
    "interval_hours": 24,
    "keep_count": 5,
    "directory": "~/Documents/WorkstationBackups"
  }
}
```

**风险**：
- 恢复时数据丢失 → 恢复前自动生成临时备份
- 备份文件损坏 → 备份后 `PRAGMA integrity_check` 校验
- 跨版本不兼容 → JSON 带 version 字段

### 执行清单

**步骤 1 —— 备份服务模块**（`server/app/services/backup.py` 新建）

```python
import sqlite3, json, os
from datetime import datetime
from pathlib import Path

async def export_sqlite(db_path: str, output_dir: str) -> str:
    os.makedirs(output_dir, exist_ok=True)
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    filepath = os.path.join(output_dir, f"workstation_backup_{timestamp}.db")
    src = sqlite3.connect(db_path)
    dst = sqlite3.connect(filepath)
    src.backup(dst)
    src.close(); dst.close()
    # 校验
    verify = sqlite3.connect(filepath)
    verify.execute("PRAGMA integrity_check")
    verify.close()
    return filepath

async def export_json(db_path: str, output_dir: str) -> str:
    # 导出所有 7 张表为 JSON，带 version 字段

async def restore_from_json(db_path: str, filepath: str, mode: str = "replace") -> dict:
    # 从 JSON 恢复，replace 模式清空后导入，merge 模式 INSERT OR REPLACE
```

**步骤 2 —— 备份 API 路由**（`server/app/api/backup.py` 新建）

- `POST /export`：返回文件下载流（sqlite）或 JSON
- `POST /import`：接收 multipart 上传，恢复前自动临时备份

**步骤 3 —— 注册路由**（`server/app/main.py` + `database.py`）

`app.include_router(backup.router)`

**步骤 4 —— 定时自动备份**（`server/app/services/backup.py` 追加）

```python
async def start_auto_backup(db_path, backup_dir, interval_hours=24):
    async def _loop():
        while True:
            await asyncio.sleep(interval_hours * 3600)
            path = await export_sqlite(db_path, backup_dir)
            _cleanup_old_backups(backup_dir, keep=5)
    asyncio.create_task(_loop())
```

**步骤 5 —— Web 端备份/恢复 UI**（`web/index.html` + `app.js`）

设置页新增"数据备份"卡片：导出 .db、导出 JSON、导入备份（选择模式）、自动备份开关和间隔下拉。

**步骤 6 —— Android 端备份入口**（`MainActivity.kt`）

设置页底部"数据管理"区域：导出备份、导入恢复。

**步骤 7 —— 端到端验证**

1. 创建测试数据 → Web 导出 SQLite → 删除数据 → 导入恢复 → 完整
2. Web 导出 JSON → 文本编辑器确认可读
3. Android 导出 → 电脑确认一致
4. 自动备份 1 分钟测试 → 恢复 24h
5. 合并模式不覆盖已有数据
6. 损坏文件恢复 → 友好错误提示

---

## 4. 代码片段管理器

### 设计

**背景**：代码片段散落在各处 txt 文件、聊天记录中，需要集中管理并支持语法高亮和搜索。

**数据表**：

```sql
CREATE TABLE IF NOT EXISTS snippets (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    title       TEXT    NOT NULL,
    code        TEXT    NOT NULL,
    language    TEXT    DEFAULT 'plain',
    tags        TEXT    DEFAULT '[]',       -- JSON 数组
    description TEXT    DEFAULT '',
    created_at  TEXT    NOT NULL DEFAULT (datetime('now', 'localtime')),
    updated_at  TEXT    NOT NULL DEFAULT (datetime('now', 'localtime'))
);
CREATE INDEX IF NOT EXISTS idx_snippets_language ON snippets(language);
CREATE INDEX IF NOT EXISTS idx_snippets_updated ON snippets(updated_at DESC);
```

**API**：

```
GET    /api/snippets              # 列表（?q= &language= &tag= &page= &page_size=）
POST   /api/snippets              # 创建
GET    /api/snippets/{id}         # 详情
PUT    /api/snippets/{id}         # 更新
DELETE /api/snippets/{id}         # 删除
GET    /api/snippets/meta         # 标签列表 + 语言统计
```

**交互**：左侧筛选面板（语言列表 + 标签云），右侧卡片列表。每张卡片显示标题、语言标签、代码前 5 行预览、📋 复制按钮。点击卡片进入详情/编辑。

**关键决策**：
- 标签存储：JSON 数组字符串（与 notes 表一致）
- 全文搜索：LIKE（初版），后续可升级 FTS5
- Android 语法高亮：复用 Markwon
- 编辑体验：textarea（初版），后续升级 CodeMirror

**风险**：
- 大代码编辑卡顿 → 限制单片段 5000 字符
- 语言标识不统一 → 前端标准化映射

### 执行清单

**步骤 1 —— snippets 表 DDL**（`server/app/database.py`）

**步骤 2 —— snippets API 路由**（`server/app/api/snippets.py` 新建）

完整 CRUD + meta 端点。

**步骤 3 —— 注册路由 + Schemas**（`main.py` + `schemas.py`）

**步骤 4 —— Web 端列表页**（`web/index.html` + `app.js`）

侧边栏入口 + 筛选面板 + 卡片列表 + 搜索框 + 📋 复制。

**步骤 5 —— Web 端编辑模态框**（`web/index.html` + `app.js`）

标题、语言下拉、标签输入、描述、代码 textarea。

**步骤 6 —— Android 端**（`MainActivity.kt` + `Models.kt`）

SnippetsScreen：搜索 + 语言标签筛选 + 卡片列表 + FAB 新建。
SnippetDetailScreen：完整代码高亮（Markwon）+ 复制。

**步骤 7 —— 端到端验证**

Web 创建 Python 片段 → Android 可见 → 复制。Android 创建 SQL 片段 → Web 可见 → 高亮正确。标签筛选、搜索、空状态提示、删除确认提示。

---

## 5. 格式化工具箱

### 设计

**背景**：开发者频繁使用的格式化工具，每次打开外部网站。纯前端内嵌，零延迟。

**工具清单**：

| 工具 | Web 实现 | Android 实现 |
|------|----------|-------------|
| JSON 格式化/压缩 | `JSON.stringify/parse` | kotlinx.serialization.Json |
| JSON ↔ YAML | js-yaml | snakeyaml |
| Base64 编解码 | `btoa/atob`（Unicode 安全包装） | `android.util.Base64` |
| URL 编解码 | `encodeURIComponent/decodeURIComponent` | `URLEncoder/URLDecoder` |
| 时间戳转换 | `new Date(ts*1000)` + 秒/毫秒自动识别 | `SimpleDateFormat` |
| JWT 解析 | `atob(token.split('.')[1])` | Base64.decode + JSON |
| 正则测试 | `new RegExp(pattern).exec(text)` | `Regex.findAll` + Spannable 高亮 |
| Markdown 预览 | marked（复用） | Markwon（复用） |

**关键决策**：
- 纯客户端实现，零服务端改动
- Web：Tab 顶部切换，双栏（输入 + 输出）
- Android：独立 ToolboxScreen，Tab 切换
- 历史记录：localStorage / SharedPreferences（最多 10 条）
- 输入限制：100KB
- 实时处理：输入变化 300ms 后自动执行（可关闭）

**风险**：
- 敏感数据 → 纯本地处理，不做服务端传输
- 超大输入卡死 → 100KB 限制

### 执行清单

**步骤 1 —— Web 端 HTML 结构**（`web/index.html`）

侧边栏入口 + Tab 导航 + 双栏布局 + 工具栏（格式化/压缩/复制/清空）+ 实时处理复选框 + 历史记录区

**步骤 2 —— Web 端 JSON 工具**（`web/app.js`）

格式化 `JSON.stringify(JSON.parse(x), null, 2)`，压缩 `JSON.stringify(JSON.parse(x))`，错误红字提示

**步骤 3 —— Web 端 JSON↔YAML**（`web/index.html` 引入 js-yaml CDN + `app.js`）

```javascript
yaml: {
  jsonToYaml(input) { return jsyaml.dump(JSON.parse(input), { indent: 2 }); },
  yamlToJson(input) { return JSON.stringify(jsyaml.load(input), null, 2); }
}
```

**步骤 4 —— Web 端 Base64/URL/时间戳/JWT**

每个工具独立编解码函数，秒/毫秒时间戳自动识别。

**步骤 5 —— Web 端正则测试**

正则 + 标志位输入行 + 测试文本区域 → 匹配结果 `<mark>` 高亮 + 捕获组列表。

**步骤 6 —— Web 端历史记录 + 实时处理**

localStorage 存最近 10 条 + 300ms 防抖实时处理 + [填入] 恢复历史。

**步骤 7 —— Android 端**（`core/tools/*.kt` 新建目录）

每个工具独立 `object`：`JsonTool`、`YamlTool`、`Base64Tool`、`UrlCodecTool`、`TimestampTool`、`RegexTool`。
`MainActivity.kt` 新增 `ToolboxScreen`。

**步骤 8 —— 端到端验证**

每个工具 3 组输入（正常 + 边界 + 错误），两端输出一致。实时处理开关、历史持久化、Tab 切换不丢状态、100KB 不卡顿。

---

## 6. 本地 Git 仓库总览

### 设计

**背景**：多仓库管理需要逐个 `git status`。仪表盘一站式展示所有本地仓库状态。

**关键决策**：

| 决策点 | 选择 | 理由 |
|--------|------|------|
| Git 调用 | subprocess 调系统 Git CLI | 零 Python 依赖 |
| 扫描策略 | 用户指定根目录，递归深度 5 层 | 避免全盘扫描 |
| 缓存 | SQLite 缓存 + 定时/手动刷新 | 不每次重扫 |
| 超时 | 每仓库 10s + 总 60s | 防止卡死 |
| Android | 纯 API 消费，服务端扫描 | 手机不需要 Git |

**数据表**：

```sql
CREATE TABLE IF NOT EXISTS git_repos_local (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    path            TEXT    NOT NULL UNIQUE,
    name            TEXT    NOT NULL,
    branch          TEXT    DEFAULT '',
    ahead           INTEGER DEFAULT 0,
    behind          INTEGER DEFAULT 0,
    changed         INTEGER DEFAULT 0,
    staged          INTEGER DEFAULT 0,
    untracked       INTEGER DEFAULT 0,
    last_commit     TEXT    DEFAULT '',
    last_commit_at  TEXT    DEFAULT '',
    last_scan       TEXT    DEFAULT '',
    status          TEXT    DEFAULT 'ok'
);
```

**API**：

```
GET    /api/git/repos              # 所有仓库状态
GET    /api/git/repos/{id}         # 详情（含最近 20 条提交）
POST   /api/git/repos/refresh      # 立即扫描
DELETE /api/git/repos/{id}         # 移除
GET    /api/git/repos/config       # 扫描配置
PUT    /api/git/repos/config       # 更新配置
```

**颜色编码**：🟢 干净、🟡 有未暂存变更、🔴 有已暂存未提交

**风险**：
- git fetch 耗时 → 15s 超时 + N/A 降级
- Windows 中文路径 → `subprocess encoding='utf-8', errors='replace'`

### 执行清单

**步骤 1 —— git_repos_local 表 DDL**（`server/app/database.py`）

**步骤 2 —— Git 扫描服务**（`server/app/services/git_scanner.py` 新建）

`scan_repository(path)`：subprocess 执行 `git rev-parse`、`git fetch`（可选）、`git status --porcelain`、`git log`。
`scan_directories(dirs, max_depth)`：递归扫描，跳过 `.git` 内部。
`get_recent_commits(path, count=20)`：返回提交列表。

**步骤 3 —— Git API 路由**（`server/app/api/git.py` 新建）

列表、详情、刷新、删除、配置 CRUD。`refresh` 调用扫描服务并写入数据库。

**步骤 4 —— 注册路由 + Schemas**（`main.py` + `schemas.py`）

**步骤 5 —— Web 端仪表盘 Git 卡片**（`web/index.html` + `app.js`）

颜色编码卡片：仓库名、分支、变更统计、最近提交信息。

**步骤 6 —— Web 端设置页配置**（`web/index.html` + `app.js`）

扫描目录输入、刷新间隔下拉、立即扫描按钮。

**步骤 7 —— Android 端**（`MainActivity.kt` + `Models.kt`）

仪表盘 Git 仓库卡片 + 详情页（提交列表）。纯 API 消费，无需本地 Git。

**步骤 8 —— 端到端验证**

扫描 3 仓库目录 → 仪表盘显示 3 张卡片 → 做修改 → 刷新 → 状态更新 → Android 同步显示 → 非 Git 目录不报错。

---

## 7. 番茄钟与专注模式

### 设计

**背景**：与任务看板联动，选中任务启动计时，完成后记录统计，形成"计划→执行→回顾"闭环。

**关键决策**：

| 决策点 | 选择 | 理由 |
|--------|------|------|
| 计时位置 | 客户端计时 + 服务端记录 | 离线可用，`Date.now() - startTime` 防降频 |
| 统计维度 | 天/周/月，关联任务 | 与仪表盘设计一致 |
| 多端冲突 | 各端独立计时 | 简化设计，不引入分布式锁 |
| 通知 | Web Notification + Android Notification | 标签页标题倒计时兜底 |

**数据表**：

```sql
CREATE TABLE IF NOT EXISTS focus_sessions (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    task_id     INTEGER DEFAULT NULL,
    task_title  TEXT    DEFAULT '',
    duration    INTEGER NOT NULL,               -- 计划时长（秒）
    actual      INTEGER DEFAULT 0,              -- 实际时长（秒）
    status      TEXT    DEFAULT 'running',       -- running/completed/interrupted
    started_at  TEXT    NOT NULL DEFAULT (datetime('now', 'localtime')),
    ended_at    TEXT    DEFAULT ''
);
```

**API**：

```
POST   /api/focus/start      # {task_id?, duration?} → {id}
PUT    /api/focus/{id}/stop  # 完成，status=completed
PUT    /api/focus/{id}/interrupt  # 中断，status=interrupted, actual=实际时长
GET    /api/focus/today      # 今日统计 {count, total_minutes, sessions}
GET    /api/focus/stats      # ?period=week|month → {days[], total}
GET    /api/focus/history    # 分页历史
```

**UI**：顶部栏或侧边栏显示环形进度条 + 倒计时 + 任务名（可最小化为标签页标题）。仪表盘统计卡片：今日番茄数、总时长、趋势热力图。

**风险**：
- 标签页后台 setInterval 降频 → `Date.now() - startTime` 计算实际时间
- 页面关闭丢失状态 → 服务端 `started_at` 可恢复判断
- 通知权限被拒 → toast 兜底 + 标签页标题倒计时

### 执行清单

**步骤 1 —— focus_sessions 表 DDL**（`server/app/database.py`）

**步骤 2 —— 番茄钟 API**（`server/app/api/focus.py` 新建）

start/stop/interrupt/today/stats/history。

**步骤 3 —— 注册路由 + Schemas**（`main.py` + `schemas.py`）

**步骤 4 —— Web 端番茄钟组件**（`web/app.js` + `styles.css`）

`PomodoroTimer` 类：start/stop/interrupt + Canvas 环形进度条 + 标签页标题倒计时。
从看板任务上点击"🍅 开始专注"启动。

**步骤 5 —— Web 端仪表盘统计**（`web/index.html` + `app.js`）

今日番茄数、总时长、周趋势图。

**步骤 6 —— Android 端**（`MainActivity.kt`）

顶部栏番茄钟图标 → BottomSheet 完整界面 → `Handler.postDelayed` 计时 → `NotificationCompat.Builder` 通知 → 仪表盘统计卡片。

**步骤 7 —— 端到端验证**

25 分钟完整流程 → 暂停/继续 → 中断记录 → 任务关联 → 仪表盘统计正确 → 双端独立运行互不干扰 → 页面关闭重开统计不丢失。

---

## 8. 每日开发日志

### 设计

**背景**：与笔记的结构化文档定位不同，日志偏向每日流水记录——遇到的问题和解决思路，按日期归档形成知识沉淀。

**关键决策**：

| 决策点 | 选择 | 理由 |
|--------|------|------|
| 与笔记关系 | 独立建表 | 职责清晰，UNIQUE(date) 约束 |
| 自动创建 | 请求 today API 时懒创建 | 避免大量空行 |
| 日历热力图 | CSS Grid 矩阵 | 简单，与仪表盘风格一致 |
| 关联 Git 提交 | 手动触发同步 | 避免自动扫描开销 |

**数据表**：

```sql
CREATE TABLE IF NOT EXISTS dev_logs (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    date        TEXT    NOT NULL UNIQUE,      -- YYYY-MM-DD
    content     TEXT    NOT NULL DEFAULT '',
    mood        TEXT    DEFAULT '',           -- happy/neutral/frustrated/tired/''
    tags        TEXT    DEFAULT '[]',
    commits     TEXT    DEFAULT '[]',         -- JSON 数组
    created_at  TEXT    NOT NULL DEFAULT (datetime('now', 'localtime')),
    updated_at  TEXT    NOT NULL DEFAULT (datetime('now', 'localtime'))
);
```

**API**：

```
GET    /api/logs/today                     # 今日日志（不存在则创建空记录）
GET    /api/logs?date=YYYY-MM-DD           # 指定日期日志
PUT    /api/logs/{id}                      # 更新
GET    /api/logs/calendar?year=&month=     # 日历热力图数据
GET    /api/logs/streak                    # 连续记录天数
DELETE /api/logs/{id}                      # 删除
```

**UI**：日期 + 星期 + 心情选择器（😊😐😤💤） + Markdown 双栏编辑 + 同步今日提交按钮 + 前后天导航 + 本月/连续天数统计。

**风险**：
- 空日志大量创建 → 30 天后自动清理空记录
- 跨天编辑 → 使用创建时的日期归属，today API 始终返回当日

### 执行清单

**步骤 1 —— dev_logs 表 DDL**（`server/app/database.py`）

**步骤 2 —— 日志 API**（`server/app/api/logs.py` 新建）

today（懒创建）/ query by date / update / calendar / streak。

**步骤 3 —— 注册路由 + Schemas**（`main.py` + `schemas.py`）

**步骤 4 —— Web 端仪表盘入口 + 热力图**（`web/index.html` + `app.js`）

仪表盘"📝 今日日志"卡片 + CSS Grid 热力图渲染。

**步骤 5 —— Web 端日志编辑页**（`web/index.html` + `app.js`）

日期 + 心情选择器 + Markdown 双栏编辑（复用步骤 1 编辑器）+ 同步提交按钮 + 日期导航。

**步骤 6 —— Android 端**（`MainActivity.kt` + `Models.kt`）

仪表盘入口 → DevLogScreen（日期 + 心情 + Markdown 预览）→ 日期导航 + 日历热力图 + 连续天数。

**步骤 7 —— 端到端验证**

Web 编辑 → Android 可见 → Android 修改 → Web 刷新一致 → 连续 3 天 streak=3 → 间隔重置 → 热力图准确 → Markdown 渲染正确。

---

## 9. 快捷便签 (Scratchpad)

### 设计

**背景**："草稿纸"定位——临时记下电话号码、命令、随手思路，用完即弃或转为笔记。追求极致的低摩擦。

**关键决策**：

| 决策点 | 选择 | 理由 |
|--------|------|------|
| 存储 | localStorage / SharedPreferences | 零网络，本地瞬写 |
| 触发方式 | Web: Ctrl+Shift+P + 底部小条；Android: FAB | 不干扰主界面 |
| 转为笔记 | POST /api/notes 一键创建 | 减少手动步骤 |
| 时间戳 | 快捷键 Ctrl+T 插入 | 不强制 |
| 多端同步 | 不跨设备同步（有意为之） | 草稿纸不需同步 |

**存储格式**：

```javascript
// Web - localStorage
{ "scratchpad_content": "...", "scratchpad_timestamp": "..." }

// Android - SharedPreferences
{ "scratchpad_content": "...", "scratchpad_timestamp": 1234567890 }
```

**交互**：底部可折叠面板（默认折叠）。展开时显示 textarea + 工具栏（转为笔记/清空/插时间戳/复制）。自动保存每个输入变化。

**风险**：
- 误关闭丢数据 → 每次输入自动保存
- 跨设备不同步 → 设计取舍，同步应使用笔记

### 执行清单

**步骤 1 —— Web 端 HTML**（`web/index.html`）

固定底部便签栏：标题栏（Ctrl+Shift+P + 折叠图标）+ Body（textarea + 工具栏）。

**步骤 2 —— Web 端 CSS**（`web/styles.css`）

固定定位、折叠/展开动画、等宽字体、工具栏样式。

**步骤 3 —— Web 端 Scratchpad 类**（`web/app.js`）

```javascript
class Scratchpad {
  constructor() {
    this.content = localStorage.getItem('scratchpad_content') || '';
    this.input.value = this.content;
  }
  toggle()        // 展开/折叠
  autoSave()      // 每次输入自动保存
  clear()         // 清空确认
  copy()          // 复制到剪贴板
  convertToNote() // POST /api/notes → 清空
  insertTimestamp() // 光标处插入时间戳
}
// 全局 + 快捷键 Ctrl+Shift+P
```

**步骤 4 —— Android 端**（`MainActivity.kt`）

FAB → ModalBottomSheet：输入框 + 工具栏（转为笔记/清空/插时间戳）。
SharedPreferences 持久化。

**步骤 5 —— 端到端验证**

输入 → 关闭浏览器 → 重开 → 内容保留。快捷键开关正常。转为笔记成功。时间戳格式正确。双端便签各自独立。不干扰主界面操作。

---

## 附录：涉及的所有文件变更汇总

### 新增文件

```
server/app/api/          backup.py, snippets.py, git.py, focus.py, logs.py
server/app/services/     backup.py, git_scanner.py
android/.../core/tools/  JsonTool.kt, YamlTool.kt, Base64Tool.kt,
                         UrlCodecTool.kt, TimestampTool.kt, RegexTool.kt
```

### 修改文件

```
server/app/               main.py, database.py, schemas.py
server/app/websocket/     handler.py, manager.py
web/                      index.html, app.js, styles.css
android/                  build.gradle.kts, MainActivity.kt, Models.kt
```

### 无变更模块

```
server/app/api/           tasks.py, notes.py, github.py, dashboard.py, profile.py, connect.py
server/app/services/      github.py
desktop/                  main.js, preload.js
server/data/              （运行时自动更新，无需代码改动）
```

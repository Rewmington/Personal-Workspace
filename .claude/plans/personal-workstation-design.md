# Personal Workstation - 设计文档

## 背景

双端个人工作台：Windows 做服务端，原生 Android App 做客户端，局域网内连接。
目标是把一个人日常开发/工作需要的四类工具整合到一个本地系统中，不依赖任何云服务。

## 需求分析

### 核心功能（十二大模块）

1. **笔记/知识管理** — 富文本笔记，标签分类，全文搜索，支持 Markdown
2. **任务看板** — 类 Kanban 看板，列可自定义，任务可拖拽/移动，优先级+截止日期
3. **代码片段** — 多语言代码片段管理，语法高亮
4. **开发日志** — 月历热力图、心情表情、标签输入
5. **番茄钟** — 倒计时环形进度、预设时长切换
6. **本地 Git** — 扫描本地仓库，展示详情和状态概览
7. **GitHub 追踪** — 接入 GitHub API，追踪近期提交、PR/Issue 状态、项目进展
8. **快捷便签** — 全局浮动按钮，快速输入自动保存
9. **数据仪表盘** — 汇总所有模块关键指标，可视化展示
10. **数据备份** — 一键备份/恢复 SQLite 数据库
11. **局域网发现** — mDNS + UDP 广播双通道自动发现，扫码连接
12. **系统托盘** — Windows 托盘最小化运行，关闭窗口不退出

### 技术约束
- Windows 服务端：FastAPI，本地运行
- Web 前端：纯 HTML/CSS/JS，托管在 FastAPI 静态文件服务
- Desktop 桌面端：Electron 壳层，内嵌服务端 EXE，含系统托盘
- Android 客户端：原生 Kotlin + Jetpack Compose，支持 API 26+
- 网络：局域网 HTTP + WebSocket，IPv4
- 数据存储：服务端本地 SQLite，零云依赖

### 风险点
- Android 原生开发周期长 → MVP 阶段单个 MainActivity.kt 承载全部 UI，后续按需拆分
- GitHub API 频率限制（未认证 60/h，认证 5000/h）→ 服务端缓存 + 用户可选填 token
- 局域网多设备 → 服务端暴露局域网 IP，同时提供 mDNS + UDP 双通道自动发现 + 扫码连接
- Windows 防火墙 → README 提供防火墙放行命令

## 方案设计

### 架构总览

Desktop 端由 Electron 壳层 + 内嵌 FastAPI 服务端 + Web 静态前端组成；Android 端通过 HTTP/WebSocket 连接服务端。服务端同时广播 mDNS 和 UDP 发现包，供 Android 自动发现。

```
┌──────────────────┐      局域网 TCP       ┌──────────────────────────────┐
│   Android App    │◄── HTTP/WebSocket ──► │      Windows Desktop          │
│ (Kotlin+Compose) │                        │                              │
│                  │                        │  ┌─ Electron 壳层 ─────────┐ │
│ ┌─ 仪表盘 ─┐     │      局域网 TCP       │  │  窗口管理 + 系统托盘     │ │
│ ┌─ 看板 ──┐     │◄── HTTP/WebSocket ──► │  ├──────────────────────────┤ │
│ ┌─ 笔记 ──┐     │                        │  │  FastAPI 服务端          │ │
│ ┌─ 片段 ──┐     │                        │  │  ├─ REST API ×12        │ │
│ ┌─ 日志 ──┐     │                        │  │  ├─ WebSocket 同步       │ │
│ ┌─ 番茄 ──┐     │                        │  │  ├─ mDNS + UDP 广播      │ │
│ ┌─ Git ───┐     │                        │  │  ├─ SQLite 数据库        │ │
│ ┌─ GitHub ┐     │      局域网 HTTP       │  │  └─ 静态文件 (web/)      │ │
│ ┌─ 便签 ──┐     │◄───────────────────────│  └──────────────────────────┘ │
│ ┌─ 设置 ──┐     │                        └──────────────────────────────┘
│              │                                  ▲
│ 扫码 ◄──────┼──────────────────────────────────┘
└──────────────┘           生成二维码
```

### 模块结构

```
personal-workstation/
├── server/                        # FastAPI 服务端
│   ├── run_server.py              # 启动入口（自动检测局域网 IP）
│   ├── app/
│   │   ├── main.py                # FastAPI 入口 + 静态文件挂载 + 生命周期
│   │   ├── config.py              # 配置管理（环境变量 + JSON 配置文件）
│   │   ├── database.py            # SQLite 连接 + 建表（CREATE TABLE IF NOT EXISTS）
│   │   ├── schemas.py             # Pydantic 数据校验模型
│   │   ├── api/
│   │   │   ├── tasks.py           # 任务看板（Board/Column/Task CRUD）
│   │   │   ├── notes.py           # 笔记 CRUD
│   │   │   ├── snippets.py        # 代码片段 CRUD
│   │   │   ├── logs.py            # 开发日志
│   │   │   ├── focus.py           # 番茄钟
│   │   │   ├── git.py             # 本地 Git 仓库扫描
│   │   │   ├── github.py          # GitHub 数据抓取
│   │   │   ├── dashboard.py       # 仪表盘数据聚合
│   │   │   ├── backup.py          # 数据备份/恢复
│   │   │   ├── profile.py         # 用户资料
│   │   │   └── connect.py         # 连接状态 + mDNS/UDP 发现
│   │   ├── services/
│   │   │   ├── github.py          # GitHub API 调用
│   │   │   ├── git_scanner.py     # 本地 Git 扫描逻辑
│   │   │   └── backup.py          # 备份逻辑
│   │   ├── websocket/
│   │   │   └── handler.py         # 实时同步 + 广播
│   │   ├── mdns_broadcaster.py    # mDNS (zeroconf) 局域网广播
│   │   └── udp_discovery.py       # UDP 广播发现（回退方案）
│   ├── data/                      # 运行时数据（SQLite + settings.json）
│   ├── tests/                     # pytest + Playwright 测试
│   └── requirements.txt
├── web/                           # 浏览器端工作台（纯 HTML/CSS/JS）
│   ├── index.html                 # 单页面应用入口
│   ├── app.js                     # 前端交互逻辑（72 KB 单文件）
│   └── styles.css                 # 深色主题样式
├── android/                       # Kotlin + Jetpack Compose 客户端
│   └── app/src/main/java/.../app/
│       ├── MainActivity.kt        # 主界面（120 KB 单文件，所有 UI 内联）
│       ├── core/model/Models.kt   # 数据模型
│       └── core/network/ApiClient.kt  # API 客户端（OkHttp + WebSocket）
├── desktop/                       # Electron 桌面壳层
│   ├── main.js                    # 主进程（窗口 + 托盘 + 服务生命周期）
│   ├── preload.js                 # 安全桥接
│   └── assets/                    # 应用图标
└── memory/                        # 项目元信息与设计资料
```

### 关键技术决策

| 决策点 | 选项 | 选择 | 理由 |
|--------|------|------|------|
| 服务端框架 | Flask / FastAPI / Node.js | **FastAPI** | 自动 OpenAPI 文档，AsyncIO 天然支持 WebSocket，类型安全 |
| 数据库 | SQLite / JSON 文件 / MongoDB | **SQLite** | 零部署，事务完整，Python 内置 |
| 数据库迁移 | Alembic / 手工 SQL | **CREATE TABLE IF NOT EXISTS** | MVP 阶段简单直接，无需迁移工具 |
| 实时通信 | 纯 HTTP 轮询 / WebSocket / SSE | **WebSocket** | 看板拖拽、同步需双向实时通信 |
| Web 前端 | React / Vue / 原生 JS | **原生 HTML/CSS/JS** | 零构建，直接挂载到 FastAPI 静态文件 |
| Desktop 包装 | 无 / Electron / Tauri | **Electron** | 窗口管理 + 系统托盘，内嵌服务端 EXE |
| Android 网络库 | OkHttp / Ktor / Retrofit | **OkHttp** | 生态成熟，WebSocket 支持稳，Kotlin 协程适配好 |
| Android UI | XML / Jetpack Compose | **Jetpack Compose** | 现代声明式，开发快 |
| Android 架构 | MVVM 多模块 / 单文件 | **单 MainActivity.kt** | MVP 快速验证，后续按需拆分 |
| GitHub 数据 | 实时请求 / 定时缓存 | **手动刷新 + WebSocket 推送** | 避免 API 限流，用户主动触发 |
| 服务发现 | 手动输入 / mDNS / UDP | **mDNS + UDP 双通道 + 扫码** | mDNS 自动发现，UDP 回退，扫码兜底 |
| 数据同步 | 实时全量 / 增量事件 | **WebSocket 增量事件** | 多端同时操作避免冲突 |
| 数据备份 | 无 / 手动备份 | **一键备份/恢复 SQLite** | 数据安全底线 |
| 系统托盘 | 无 / 最小化到托盘 | **Electron Tray + 开关控制** | 后台静默运行，关闭不退出 |

### 数据结构（核心表）

```sql
-- 笔记
notes (id, title, content TEXT, tags TEXT, created_at, updated_at)
-- 任务看板
boards (id, name, position)
columns (id, board_id, name, position)
tasks (id, column_id, title, description, priority, due_date, position, created_at)
-- 代码片段
snippets (id, title, language, code TEXT, tags TEXT, created_at, updated_at)
-- 开发日志
dev_logs (id, content TEXT, mood TEXT, tags TEXT, log_date DATE, created_at)
-- 专注记录
focus_sessions (id, duration INT, started_at, ended_at, created_at)
-- 本地 Git 仓库
git_repos (id, path TEXT, name, last_scan_at)
-- GitHub 缓存
github_repos (id, name, owner, last_fetch_at)
github_commits (id, repo_id, sha, message, author, pushed_at)
github_events (id, repo_id, type, actor, created_at)
-- 仪表盘指标（快照表）
dashboard_snapshots (id, module, metric_key, metric_value, snapshot_at)
-- 备份记录
backups (id, filename, size, created_at)
```

### API 接口定义（REST + WebSocket）

#### REST (HTTP)

**笔记**
```
GET    /api/notes              — 列表（搜索 / 标签过滤）
POST   /api/notes              — 新建
GET    /api/notes/{id}         — 详情
PUT    /api/notes/{id}         — 更新
DELETE /api/notes/{id}         — 删除
```
**任务看板**
```
GET    /api/boards             — 看板列表
POST   /api/boards             — 新建看板
GET    /api/boards/{id}/tasks  — 看板任务
POST   /api/tasks              — 新建任务
PUT    /api/tasks/{id}         — 更新（含列移动）
DELETE /api/tasks/{id}         — 删除
POST   /api/tasks/undo         — 撤销删除
```
**代码片段**
```
GET    /api/snippets           — 列表（语言 / 标签过滤）
POST   /api/snippets           — 新建
PUT    /api/snippets/{id}      — 更新
DELETE /api/snippets/{id}      — 删除
```
**开发日志**
```
GET    /api/logs               — 列表（日期范围）
POST   /api/logs               — 新建
PUT    /api/logs/{id}          — 更新
DELETE /api/logs/{id}          — 删除
```
**番茄钟**
```
GET    /api/focus/sessions     — 专注记录
POST   /api/focus/sessions     — 新建记录
```
**本地 Git**
```
GET    /api/git/repos          — 扫描本地仓库
GET    /api/git/repos/{id}     — 仓库详情
POST   /api/git/scan           — 触发扫描
```
**GitHub 追踪**
```
GET    /api/github/settings    — Token 配置
PUT    /api/github/settings    — 更新 Token
GET    /api/github/activity    — 活动数据
GET    /api/github/repos       — 仓库列表
POST   /api/github/refresh     — 手动刷新
```
**仪表盘**
```
GET    /api/dashboard/summary        — 汇总数据
GET    /api/dashboard/github-heatmap — GitHub 热力图
```
**备份**
```
GET    /api/backup/list       — 备份列表
POST   /api/backup/create     — 创建备份
POST   /api/backup/restore    — 恢复备份
DELETE /api/backup/{id}       — 删除备份
```
**连接与发现**
```
GET    /api/connect/status    — 服务器连接状态
GET    /api/connect/discover  — mDNS/UDP 发现状态
GET    /api/connect/qrcode    — 连接二维码
```
**用户资料**
```
GET    /api/profile           — 用户资料
PUT    /api/profile           — 更新资料
```

#### WebSocket
```
连接: ws://<server-ip>:<port>/ws

客户端→服务端:
  {"type": "sync_init"}          — 首次连接，拉取全量
  {"type": "task_move", ...}     — 拖拽任务到其他列
  {"type": "note_edit", ...}     — 笔记编辑
  {"type": "github_refresh"}     — 手动刷新 GitHub

服务端→客户端:
  {"type": "sync_state", ...}    — 全量状态
  {"type": "task_updated", ...}  — 任务变更广播
  {"type": "github_updated", ...} — GitHub 数据更新通知
```

## 当前完成状态

V3 全部 14 项功能已完成：

1. ✅ 仪表盘（任务进度环 + 热力图 + 最近笔记 + 统计卡片）
2. ✅ 任务看板（完整 CRUD + 列移动 + WebSocket 同步 + 撤销）
3. ✅ 笔记管理（CRUD + 搜索 + 标签 + Markdown）
4. ✅ 代码片段（多语言 + 语法高亮 + 筛选）
5. ✅ 开发日志（月历热力图 + 心情表情 + 标签）
6. ✅ 番茄钟（环形进度 + 预设时长切换）
7. ✅ 本地 Git（仓库扫描 + 详情 + 状态概览）
8. ✅ GitHub 追踪（个人资料 + 仓库 + 提交记录 + 刷新）
9. ✅ 快捷便签（浮动按钮 + 自动保存）
10. ✅ 数据备份（一键备份 / 恢复 SQLite）
11. ✅ 局域网发现（mDNS + UDP 双通道 + 扫码连接）
12. ✅ Windows 系统托盘（最小化 + 开关控制）
13. ✅ Electron 桌面应用（安装包 + 绿色版）
14. ✅ Web 前端（纯 HTML/CSS/JS，不依赖框架）

## 已知的待补项

以下内容设计文档中有提及，当前版本未实现：

| 待补项 | 优先级 | 说明 |
|--------|--------|------|
| 数据库迁移工具 | 低 | 当前用 `CREATE TABLE IF NOT EXISTS`，后续表结构变更时引入 Alembic |
| Android 架构拆分 | 中 | `MainActivity.kt` 120KB 单文件，后续按 features/ 模块拆分 |
| Android 本地缓存 | 中 | 添加 DataStore 缓存层，减少网络请求 |
| Android 依赖注入 | 低 | 引入 Hilt/Koin 解耦 |
| GitHub Token 加密 | 中 | 当前明文存 JSON，需用 Fernet 加密 |
| 后台定时刷新 | 低 | GitHub 数据当前手动刷新，后续加后台任务 |
| API 请求工具 | 低 | 开发者工具集中的 HTTP 请求调试面板 |

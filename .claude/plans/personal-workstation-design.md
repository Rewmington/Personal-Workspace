# Personal Workstation - 设计文档

## 背景

双端个人工作台：Windows 做服务端，原生 Android App 做客户端，局域网内连接。
目标是把一个人日常开发/工作需要的四类工具整合到一个本地系统中，不依赖任何云服务。

## 需求分析

### 核心功能（五大模块）

1. **笔记/知识管理** — 富文本笔记，标签分类，全文搜索，支持 Markdown
2. **任务看板** — 类 Kanban 看板，列可自定义，任务可拖拽/移动，优先级+截止日期
3. **开发者工具集** — 代码片段管理、API 请求工具、简易终端/脚本执行、正则测试等
4. **GitHub 追踪** — 接入 GitHub API，追踪近期提交频率、PR/Issue 状态、各项目进展时间线
5. **数据仪表盘** — 汇总所有模块的关键指标，可视化展示（GitHub 活动热力图、任务完成率、笔记统计等）

### 技术约束
- Windows 服务端：需本地运行，开机自启可选
- Android 客户端：原生 Kotlin，支持 API 26+ (Android 8.0+)
- 网络：局域网 HTTP + WebSocket，IPv4
- 数据存储：服务端本地文件/SQLite，零外部依赖
- 渐进式开发：先 MVP 跑通，再迭代

### 风险点
- Android 原生开发周期长 → MVP 阶段只做核心页面，UI 先朴素可用
- GitHub API 频率限制（未认证 60/h，认证 5000/h）→ 服务端缓存 + 用户可选填 token
- 局域网多设备 → 服务端暴露局域网 IP，客户端首次连接需手动输入 IP

## 方案设计

### 架构总览

```
┌──────────────────┐         局域网 TCP         ┌──────────────────┐
│   Android App    │◄──── HTTP/WebSocket ────► │   Windows Server │
│   (原生 Kotlin)   │                            │   (Python)        │
│                  │                            │                    │
│  ┌─ 笔记 ─┐      │                            │  ┌─ REST API ─┐   │
│  ┌─ 看板 ─┐      │                            │  ┌─ WebSocket┐  │   │
│  ┌─ 工具 ─┐      │                            │  ┌─ GitHub   │  │   │
│  ┌─ 仪表盘┐      │                            │  ┌─ SQLite   │  │   │
│  ┌─ 设置 ─┐      │                            │  ┌─ 文件存储 ┐  │   │
└──────────────────┘                            └──────────────────┘
```

### 模块结构

```
personal-workstation/
├── server/                      # Windows 服务端
│   ├── app/
│   │   ├── main.py             # FastAPI 入口
│   │   ├── config.py           # 配置 (端口、数据库路径)
│   │   ├── database.py         # SQLite 连接管理
│   │   ├── models.py           # 数据模型 (SQLAlchemy)
│   │   ├── api/
│   │   │   ├── notes.py        # 笔记 CRUD
│   │   │   ├── tasks.py        # 任务看板 CRUD
│   │   │   ├── snippets.py     # 代码片段
│   │   │   ├── dashboard.py    # 仪表盘数据聚合
│   │   │   └── github.py       # GitHub 数据抓取
│   │   ├── workers/
│   │   │   └── github_fetcher.py  # 后台定时抓取 GitHub
│   │   └── websocket/
│   │       └── handler.py      # 实时同步 handler
│   ├── migrations/             # 数据库迁移
│   ├── data/                   # 运行时数据 (SQLite + 文件)
│   ├── requirements.txt
│   └── run_server.py           # 启动脚本
└── android/                     # Android 原生客户端
    └── app/
        ├── MainActivity.kt
        ├── core/
        │   ├── network/        # HTTP Client + WebSocket
        │   ├── di/             # 依赖注入
        │   └── datastore/      # 本地缓存
        ├── features/
        │   ├── notes/
        │   ├── kanban/
        │   ├── tools/
        │   ├── dashboard/
        │   ├── github/
        │   └── settings/       # 连接配置 (IP/端口)
        └── ui/theme/
```

### 关键技术决策

| 决策点 | 选项 | 选择 | 理由 |
|--------|------|------|------|
| 服务端框架 | Flask / FastAPI / Node.js | **FastAPI** | 自动 OpenAPI 文档，AsyncIO 天然支持 WebSocket，类型安全 |
| 数据库 | SQLite / JSON 文件 / MongoDB | **SQLite** | 零部署，事务完整，Python 内置，足够个人使用 |
| 实时通信 | 纯 HTTP 轮询 / WebSocket / SSE | **WebSocket** | 看板拖拽、GitHub 更新需双向实时同步 |
| Android 网络库 | OkHttp + 手写 / Ktor / Retrofit | **Ktor Client** | 原生支持 WebSocket，协程友好，单一库 |
| Android UI | XML / Jetpack Compose | **Jetpack Compose** | 现代声明式，开发快，适合 MVP 快速迭代 |
| GitHub 数据 | 实时请求 / 定时缓存 | **定时缓存 (每30分钟)** | 避免 API 限流，仪表盘无需实时秒级数据 |
| 服务发现 | 手动输入 IP / mDNS / 固定配置 | **手动输入 (MVP) → mDNS (后续)** | MVP 阶段最简单，后续再加自动发现 |
| 数据同步 | 实时全量 / 增量事件 | **WebSocket 增量事件** | 多端同时操作时避免冲突 |

### 数据结构（核心表）

```sql
-- 笔记
notes (id, title, content TEXT, tags TEXT, created_at, updated_at)
-- 任务看板
boards (id, name, position)
columns (id, board_id, name, position)
tasks (id, column_id, title, description, priority, due_date, position, created_at)
-- 代码片段
snippets (id, title, language, code TEXT, tags TEXT, created_at)
-- GitHub 缓存
github_repos (id, name, owner, last_fetch_at)
github_commits (id, repo_id, sha, message, author, pushed_at)
github_events (id, repo_id, type, actor, created_at)
-- 仪表盘指标 (快照表)
dashboard_snapshots (id, module, metric_key, metric_value, snapshot_at)
```

### API 接口定义（REST + WebSocket）

#### REST (HTTP)
```
GET    /api/notes          — 笔记列表（支持搜索/标签过滤）
POST   /api/notes          — 新建笔记
GET    /api/notes/{id}     — 笔记详情
PUT    /api/notes/{id}     — 更新笔记
DELETE /api/notes/{id}     — 删除笔记

GET    /api/boards         — 看板列表
GET    /api/boards/{id}/tasks — 看板任务列表
POST   /api/tasks          — 新建任务
PUT    /api/tasks/{id}     — 更新任务（含列移动）
DELETE /api/tasks/{id}     — 删除任务

GET    /api/snippets       — 片段列表
POST   /api/snippets       — 新建片段
GET    /api/snippets/{id}  — 片段详情

GET    /api/github/activity  — GitHub 活动数据
GET    /api/github/repos     — 仓库列表
GET    /api/github/repos/{name}/progress — 项目进展

GET    /api/dashboard/summary  — 仪表盘汇总
GET    /api/dashboard/github-heatmap — GitHub 热力图数据
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

## MVP 范围（第一阶段）

为控制范围，MVP 只实现：
1. ✅ 任务看板（完整 CRUD + 列拖拽 + WebSocket 同步）
2. ✅ GitHub 追踪（活动列表 + 提交频率可视化）
3. ✅ 基础仪表盘（任务统计 + GitHub 热力图）
4. ⚠️ 笔记（只做基础 CRUD，搜索后续加）
5. ❌ 开发者工具集（第二阶段）

## 风险与替代方案

- **Android 原生开发量大**：若后期发现太重，可降级为 PWA（渐进式网页应用），共享同一套前端代码
- **SQLite 并发**：单用户个人工作台不存在并发问题，无需考虑
- **GitHub 隐私**：所有数据本地存储，不上云；token 本地加密存储
- **Windows 防火墙**：首次运行需提示用户开放端口，提供一键脚本

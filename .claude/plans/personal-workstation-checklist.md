# Personal Workstation - 执行清单 (MVP 阶段)

## 阶段 0：项目初始化

### 步骤 0.1：创建项目目录结构
- **文件**：`personal-workstation/` 根目录
- **操作**：新建
- **内容**：创建 `server/` 和 `android/` 两个根目录及子目录
- **验收条件**：目录结构与设计文档模块结构一致
- **依赖**：无

### 步骤 0.2：服务端 Python 环境
- **文件**：`server/requirements.txt`
- **操作**：新建
- **内容**：`fastapi>=0.110`, `uvicorn>=0.27`, `sqlalchemy>=2.0`, `pydantic>=2.0`, `httpx>=0.27`
- **验收条件**：`pip install -r server/requirements.txt` 成功
- **依赖**：步骤 0.1

### 步骤 0.3：Android 项目初始化
- **文件**：`android/` 项目
- **操作**：新建
- **内容**：用 Android Studio 创建新的 Kotlin Compose 项目，minSdk 26
- **验收条件**：项目可在模拟器启动，无编译错误
- **依赖**：步骤 0.1

## 阶段 1：服务端核心骨架

### 步骤 1.1：数据库模型
- **文件**：`server/app/database.py`, `server/app/models.py`
- **操作**：新建
- **内容**：
  - `database.py`: SQLite 引擎 + Session 工厂，路径 `data/workstation.db`
  - `models.py`: Board, Column, Task, Note, GitHubRepo, GitHubCommit 等 SQLAlchemy 模型
- **验收条件**：`init_db()` 能创建所有表
- **依赖**：步骤 0.2

### 步骤 1.2：配置与入口
- **文件**：`server/app/config.py`, `server/app/main.py`, `server/run_server.py`
- **操作**：新建
- **内容**：
  - `config.py`: 端口、数据库路径、GitHub token 配置（环境/配置文件）
  - `main.py`: FastAPI app，挂载所有路由 + WebSocket endpoint
  - `run_server.py`: `uvicorn app.main:app --host 0.0.0.0 --port 8080`，打印局域网 IP
- **验收条件**：`python run_server.py` 启动后，`curl http://localhost:8080/api/health` 返回 200
- **依赖**：步骤 1.1

### 步骤 1.3：任务看板 API
- **文件**：`server/app/api/tasks.py`
- **操作**：新建
- **内容**：REST CRUD for Board/Column/Task，支持任务列移动和排序
- **验收条件**：Postman/curl 可完成 增删改查 + 列移动
- **依赖**：步骤 1.2

### 步骤 1.4：笔记基础 API
- **文件**：`server/app/api/notes.py`
- **操作**：新建
- **内容**：Note CRUD，支持 tags 字段（JSON 数组）
- **验收条件**：curl 可完成 增删改查
- **依赖**：步骤 1.2

### 步骤 1.5：GitHub 数据抓取
- **文件**：`server/app/api/github.py`, `server/app/workers/github_fetcher.py`
- **操作**：新建
- **内容**：
  - `github_fetcher.py`: 用 httpx 调用 GitHub REST API，定时每 30 分钟抓取用户 repo 列表、近期 commits、events
  - `github.py`: 暴露 API 接口返回缓存数据，支持手动触发刷新
  - token 可选（环境变量 `GITHUB_TOKEN`），未配置时使用匿名限制
- **验收条件**：配置 token 后，API 返回 repo 列表和 commit 数据
- **依赖**：步骤 1.2

### 步骤 1.6：仪表盘 API
- **文件**：`server/app/api/dashboard.py`
- **操作**：新建
- **内容**：
  - `GET /api/dashboard/summary`: 聚合任务完成率、笔记数量、GitHub 近期活动数
  - `GET /api/dashboard/github-heatmap`: 返回按天分组的提交数（用于热力图）
- **验收条件**：返回结构化的 JSON 汇总数据
- **依赖**：步骤 1.5

### 步骤 1.7：WebSocket 实时同步
- **文件**：`server/app/workspace/handler.py`
- **操作**：新建
- **内容**：
  - `ws` endpoint 管理客户端连接列表
  - `sync_init`: 连接时推送全量看板+笔记状态
  - 任务变更时广播 `task_updated` 事件给所有连接
- **验收条件**：两个 WebSocket 客户端连接，一端改任务另一端收到推送
- **依赖**：步骤 1.3

## 阶段 2：Android 客户端

### 步骤 2.1：网络层
- **文件**：`android/app/src/main/kotlin/.../core/network/`
- **操作**：新建
- **内容**：
  - Ktor HttpClient 实例，拦截器加 IP/端口（从设置读取）
  - WebSocket 连接管理（重连机制）
  - REST API Client（封装所有 endpoint 调用）
- **验收条件**：能成功请求到服务端 health endpoint
- **依赖**：步骤 1.2

### 步骤 2.2：本地数据缓存
- **文件**：`android/app/src/main/kotlin/.../core/datastore/`
- **操作**：新建
- **内容**：DataStore 存储服务器 IP/端口；本地 Room DB 缓存离线数据
- **验收条件**：设置 IP 后下次启动能记住
- **依赖**：步骤 2.1

### 步骤 2.3：设置页面（连接配置）
- **文件**：`android/app/src/main/kotlin/.../features/settings/SettingsScreen.kt`
- **操作**：新建
- **内容**：输入框填 IP + 端口，测试连接按钮（调 health API）
- **验收条件**：输入正确 IP 后显示"连接成功"，错误 IP 显示"连接失败"
- **依赖**：步骤 2.1

### 步骤 2.4：底部导航 + 主框架
- **文件**：`android/app/src/main/kotlin/.../MainActivity.kt`, `MainScreen.kt`
- **操作**：新建
- **内容**：BottomNavigation 四个 tab：看板、笔记、GitHub、仪表盘
- **验收条件**：四个 tab 可切换，导航高亮正确
- **依赖**：步骤 2.3

### 步骤 2.5：看板页面（核心）
- **文件**：`android/app/src/main/kotlin/.../features/kanban/KanbanScreen.kt`
- **操作**：新建
- **内容**：
  - LazyRow 展示列，每列 LazyColumn 展示任务
  - 长按拖拽任务到其他列（DragAndDrop）
  - 点击任务弹对话框编辑
  - 连接 WebSocket 实时同步
- **验收条件**：能创建任务、拖拽到不同列、另一端操作时本地自动更新
- **依赖**：步骤 2.4、步骤 1.7

### 步骤 2.6：GitHub 页面
- **文件**：`android/app/src/main/kotlin/.../features/github/GithubScreen.kt`
- **操作**：新建
- **内容**：
  - 仓库列表（LazyColumn）
  - 提交频率柱状图（简单柱状图，Compose 手绘或第三方库）
  - 近期活动流（PR/Issue/Commit）
- **验收条件**：显示 repo 列表和提交活动，时间轴按日分组
- **依赖**：步骤 2.4、步骤 1.5

### 步骤 2.7：仪表盘页面
- **文件**：`android/app/src/main/kotlin/.../features/dashboard/DashboardScreen.kt`
- **操作**：新建
- **内容**：
  - 任务完成率环形进度
  - GitHub 提交热力图（按天，颜色深浅）
  - 笔记数量统计
- **验收条件**：数据与后端 dashboard API 一致
- **依赖**：步骤 2.4、步骤 1.6

### 步骤 2.8：笔记页面（基础版）
- **文件**：`android/app/src/main/kotlin/.../features/notes/NotesScreen.kt`
- **操作**：新建
- **内容**：笔记列表 + 新建/编辑（文本输入），标签显示
- **验收条件**：能创建、编辑、删除笔记，列表实时刷新
- **依赖**：步骤 2.4、步骤 1.4

## 阶段 3：联调与收尾

### 步骤 3.1：局域网联调
- **文件**：`server/run_server.py`
- **操作**：修改
- **内容**：启动时自动检测并打印局域网 IP，提示用户防火墙规则
- **验收条件**：Windows 启动后，安卓手机输入打印的 IP 能连接
- **依赖**：全部服务端步骤

### 步骤 3.2：WebSocket 断线重连
- **文件**：`android/.../core/network/` + `server/.../handler.py`
- **操作**：修改
- **内容**：Android 端断线自动重连（指数退避），重连后触发 sync_init
- **验收条件**：服务端重启后，Android 端自动重连并恢复数据
- **依赖**：步骤 2.1、步骤 1.7

### 步骤 3.3：MVP 验收
- **文件**：全局
- **操作**：验收
- **内容**：
  1. Windows 启动服务 → 打印 IP
  2. 安卓输入 IP 连接 → 显示看板/笔记/GitHub/仪表盘
  3. 看板拖拽任务 → 双方实时同步
  4. GitHub 页面 → 显示近期提交活动
  5. 仪表盘 → 显示统计数据
- **验收条件**：以上 5 条全部通过
- **依赖**：全部步骤

## 第二阶段（待 MVP 验收后）
- 笔记全文搜索 + Markdown 渲染
- 开发者工具集（代码片段、API 调试、正则测试）
- 开机自启 + 系统托盘图标（Windows）
- mDNS 自动服务发现
- 数据导出/备份
- GitHub token 安全存储 + 仓库管理界面

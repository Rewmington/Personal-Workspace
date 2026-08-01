# 接口测试工具 — 实现计划

## 联动关系
0. 兼容准备检查（schema / settings / route registry / load order）
1. 数据库 → `database.py` 加表定义，依赖 `config.py` 无改动
2. 后端 API → `server/app/api/http_client.py` 新建路由文件，依赖 httpx 发请求 + database 读写
3. 主入口 → `main.py` 注册 router
4. 前端 HTML → 无需改（复用现有 `renderTools` 框架）
5. 前端 JS → `app.js` 新增 tab、markup、bind 函数
6. 样式 → `styles.css` 新增面板样式
7. 计划表更新 → 设计文档标记已完成

## 零 release checklist
- 不做 release
- 不做 npm publish / changelog / 版本号
- 不做 GitHub Actions / CI

## 分步计划

### Step 0: 兼容检查 — 读取现状
- 读取 `server/app/database.py` SCHEMA 尾部（找最后一个表定义位置）
- 读取 `server/app/main.py` router 注册段（找插入位置）
- 读取 `web/styles.css` 末尾区域（找新增样式位置）

### Step 1: 数据库 — 新增 http_requests 表
**文件**: `server/app/database.py`
**改动**: 在 SCHEMA 字符串末尾追加建表语句
**验证**: `init_db()` 执行不报错

### Step 2: 后端 API — 新建 http_client.py
**文件**: `server/app/api/http_client.py`（新文件）
**路由**:
- `POST /api/http-client/send` — 代理发送请求 + 自动保存记录
- `GET /api/http-client/history?limit=50` — 历史记录列表
- `PUT /api/http-client/history/{id}/star` — 切换收藏
- `DELETE /api/http-client/history/{id}` — 删除记录
**依赖**: httpx（已有）、database.connection

### Step 3: 主入口 — 注册路由
**文件**: `server/app/main.py`
**改动**: 在 api 导入段加入 http_client，在 router 注册段加入 include_router

### Step 4: 前端 JS — 扩展开发工具页
**文件**: `web/app.js`
**改动**:
- `renderTools()` — 在 tool-tabs 末尾追加"接口测试"按钮
- `renderToolPanel()` — 新增 `http-client` case
- `toolMarkup()` — 新增 http-client 布局（请求区 + 响应区）
- `bindHttpClientTool()` — 新函数（发送请求、方法切换、请求头行管理、JSON 格式化、历史加载、收藏切换）
- `navigate()` 的 titles map — 无需改动（复用 tools）

### Step 5: 样式 — 新增面板样式
**文件**: `web/styles.css`
**改动**: 追加 http-client 面板样式（请求构造区、响应展示区、历史侧栏）

### Step 6: 计划表更新
**文件**: `.claude/plans/http-client-design.md`
**改动**: 追加实现完成标记

## 关键实现要点
### 请求体模式切换
- JSON 模式：textarea 输入 + 格式化按钮
- 表单模式：键值对行编辑器
- 纯文本模式：单 textarea

### 响应展示
- 状态码 → 绿色(2xx)、橙色(3xx)、红色(4xx/5xx) 徽章
- 耗时 → 以 ms 显示
- 响应体 → 尝试 JSON 格式化，失败则纯文本

### 历史记录
- 发送请求时自动保存（加载状态不阻塞保存）
- 历史列表显示在请求面板左侧/顶部，点击恢复
- 收藏项置顶

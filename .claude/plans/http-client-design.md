# 接口测试工具 — 设计文档

> **状态**: ✅ 已完成

## 时间线

- 设计确认: 2026-08-01
- 实现完成: 2026-08-01

## 改动清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `server/app/database.py` | 改 | SCHEMA 新增 `http_requests` 表 + 索引 |
| `server/app/api/http_client.py` | 新 | 4 个路由：send / history / star / delete |
| `server/app/main.py` | 改 | 导入并注册 http_client.router |
| `web/app.js` | 改 | 新增 tab + markup + bindHttpClientTool |
| `web/styles.css` | 改 | 追加 ~90 行 http-client 面板样式 |

## 原始设计（不变）

## 背景

在工作台内新增接口测试（HTTP Client），电脑端 Web 界面使用，可发送 HTTP 请求并查看响应，支持历史记录和收藏。

## 目标

提供一个轻量完整的 HTTP 调试面板，集成到现有 Personal Workstation 侧边栏中。

## 功能清单

| 功能 | 说明 |
|------|------|
| 请求构造 | URL 输入 + 方法切换（GET/POST/PUT/DELETE/PATCH/HEAD/OPTIONS） |
| 请求头编辑 | 键值对列表，动态增删行 |
| 请求体编辑 | JSON / 表单 / 纯文本 三种模式，支持格式化 |
| 发送请求 | 点击发送或 Ctrl+Enter，显示加载状态 |
| 响应展示 | 状态码徽章、耗时、响应头折叠、响应体 JSON 格式化高亮 |
| 历史记录 | 最近 50 条自动保存，点击恢复参数 |
| 收藏夹 | 星标收藏，常用请求快速复用 |

## 技术方案

### 后端：HTTP 代理

浏览器跨域限制，由服务端 httpx 代为发起请求：

```
POST /api/http-client/send
  请求体: { method, url, headers: [{key,value}], body, content_type }
  响应体: { status, time_ms, headers: {key:value}, body, error? }
```

### 数据存储

```sql
CREATE TABLE IF NOT EXISTS http_requests (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  method      TEXT NOT NULL DEFAULT 'GET',
  url         TEXT NOT NULL,
  headers     TEXT DEFAULT '[]',       -- JSON
  body        TEXT DEFAULT '',
  content_type TEXT DEFAULT 'json',
  response_status INTEGER,
  response_time_ms INTEGER,
  response_body TEXT,
  is_favorite INTEGER DEFAULT 0,
  created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

API：
- `GET /api/http-client/history?limit=50` — 历史列表
- `POST /api/http-client/send` — 发送并自动保存
- `DELETE /api/http-client/history/{id}` — 删除一条
- `PUT /api/http-client/history/{id}/star` — 切换收藏

### 前端：工作台侧边栏新面板

在现有侧边栏新增「接口测试」入口，单页布局：
- **上半屏**：URL 栏 + 方法选择 + 请求头/请求体编辑区
- **下半屏**：响应状态栏 + 响应体（代码块高亮）
- **侧边栏/标签**：历史列表 + 收藏列表

### 改动文件

| 文件 | 改动 |
|------|------|
| `server/app/api/http_client.py` | 新增 — 代理发送 + CRUD 路由 |
| `server/app/database.py` | http_requests 建表语句 |
| `server/app/main.py` | 注册路由 |
| `web/index.html` | 新增面板 HTML + 侧边栏入口 |
| `web/app.js` | 新增模块逻辑 |
| `web/styles.css` | 新增样式 |
| `server/requirements.txt` | 无新增（httpx 已有） |

## 非目标

- 不包含环境变量管理（留待后续迭代）
- 不包含前置/后置脚本
- 不包含 WebSocket/GraphQL 测试
- 不在 Android 端提供此功能

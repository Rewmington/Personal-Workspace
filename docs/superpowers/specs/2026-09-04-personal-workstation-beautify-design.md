# Personal Workstation · 双端美化 + 通知推送设计

> 状态：设计确认中
> 日期：2026-09-04

## 1. 背景与目标

现有 Personal Workstation（Windows 服务端 + Web 前端 + Android 客户端，局域网零云）功能完备，但**视觉平庸**且**缺乏辨识度**。目标是让它在"好看"之外，拥有真正让用户记住的**视觉亮点**与**功能亮点**。

**目标**
1. 视觉：套用一套统一的高级设计系统（配色/圆角/阴影/字体/组件），并打造"驾驶舱氛围 + 招牌可视化"的记忆点。
2. 功能：让电脑上的任意事件（尤以 Claude Code hooks 为代表）能**实时推送到手机提醒**，形成"电脑通知 → 手机提醒"的双端同心体验。

**范围**
- 双端视觉重做：`web/styles.css`（+少量 index.html / app.js）、Android `MainActivity.kt`（Compose 主题）。
- 新增通知接力：服务端、WebSocket、Android 推送、`notify.ps1` 接入。

## 2. 视觉设计系统（indigo 蓝紫）

方向：**精致社交感 + 克制**（兼顾用户偏好"数据密集、像 Linear/Obsidian"）。双端统一设计语言，仅容器/导航不同。

### 2.1 配色 Token（中性层双端一致）

| Token | 值 | 用途 |
|---|---|---|
| --bg | `#0B0D11` | 深空舞台底（带冷蓝，非纯黑） |
| --surface | `#14161C` | 卡片分层 |
| --elevated | `#191C24` | 悬浮/高亮 |
| --line | `rgba(255,255,255,.06)` | 超细提亮边框 |
| --text / --text-2 / --text-3 | `#ECEEF2` / `#9AA3B0` / `#626A76` | 三级文字 |
| --accent / --accent-lt | `#6E7BF2` / `#98A5F8` | 主强调 indigo 蓝紫 |
| --accent-soft | `rgba(110,123,242,.15)` | 选中/焦点底 |
| --success / --warning / --danger | `#3ECF8E` / `#E8A93D` / `#EF6B6B` | 语义色 |
| --glow-lt / --glow-bg | `rgba(152,165,248,.55)` / `rgba(110,123,242,.12)` | 光晕 |

### 2.2 形状 / 阴影 / 间距 / 字体

- 圆角：`8/12/16/20` + `pill`（卡片 16、大面板 20、按钮 12、输入 12、导航胶囊 pill）
- 阴影（柔和多层）：`--sh-1:0 1px 2px rgba(0,0,0,.3)`、卡片 `0 8px 24px rgba(0,0,0,.3)`、光晕 `0 0 0 1px accent-soft + 0 8px 24px accent-16`
- 间距：content 左右 `32`、卡片内边距 `16-18`、卡片间距 `14`
- 字体：Inter；大标题 `800` 收紧 `-0.5px`；次要文字展开；正文 `400/500`；代码 JetBrains Mono `12-13px`

### 2.3 驾驶舱氛围（克制）

- 背景两团微弱环境光（右上 indigo、左下冷蓝）+ 一层 `radial-gradient(rgba(255,255,255,.05) 1px, transparent 1px)` 点阵网格（`opacity≈.32`，不抢内容）。
- 侧边栏 / 面板用半透明 + `backdrop-filter: blur`（通透玻璃感）。
- 状态点、左侧竖条、关键数字带**弱光晕**；不做过强霓虹。

### 2.4 招牌可视化（记忆点）

- **贡献星野（热力图）**：贡献度映射 `indigo 18%/38%/62%` + 高亮格自带光斑；hover 发光放大（`scale .3 + glow`）；附"少→多"图例。
- **任务轨道环**：完成率 SVG 渐变圆弧 + `3.6s` 呼吸光晕动画（`breathe`），中心大数字。
- **发光时间线（活动流）**：每条事件一个发光点连成导线。

### 2.5 组件规范（双端同造型）

卡片 `16` 圆角 + 分层表面 + 细边框 + 柔影；主按钮 `12` 圆角 + accent 渐变 + 微阴影；输入 `12` 圆角 + 半透明底 + focus accent 光晕；导航（Web 侧边玻璃底 + 左侧发光竖条 / Android 底部胶囊 indicator）；标签 pill；头像 accent 渐变圆形；热力图 4 级 accent 不透明度；模态框玻璃 backdrop + 强下层影；线性 SVG 图标（stroke 2 round）。

### 2.6 双端文件归属

| 端 | 文件 | 改动 |
|---|---|---|
| Web | `web/styles.css` | 重写 :root token + 基础组件类 + 面板统一 + 氛围背景 |
| Web | `web/index.html` / `web/app.js` | 少量：字符图标换内联 SVG、个别渲染辅助 |
| Android | `MainActivity.kt`（或拆 `ui/DesignTokens.kt` + `AppTheme`） | 抽 token，替换散落硬编码 `Color(0x…)`，统一 Card/Button/Input/Navigation |

## 3. 功能亮点：电脑通知 → 手机提醒（双端接力）

### 3.1 架构

```
Claude Code hook(任务完成/需确认) / 任意脚本
        │ 运行 notify.ps1 (已存在)
        │      ▲ 现：弹 Windows Toast
        │      └─ 新增：POST /api/notify {title,message,type}
        ▼
FastAPI 服务端  ──WebSocket 广播──►  Android 客户端 ──► 系统通知
```

### 3.2 服务端接口（新增）

`POST /api/notify`，body `{ title:str, message:str="", type:"info|success|warning|confirm" }`：
- 校验 title 非空（截断长度），type 白名单校验；
- （MVP 仅为广播，不落库；通知历史留待后续迭代）；
- 通过 `ConnectionManager.broadcast({"type":"notify","data":{title,message,type}})` 推送所有已连接客户端；
- 无鉴权（局域网），支持被任意脚本/curl 调用。

### 3.3 WebSocket 事件

- 新增下行事件：`{ "type": "notify", "data": {title, message, type} }`（带 `seq`）。
- 手机端据此弹通知；Web 端可显示角落 toast（可选）。

### 3.4 Android 通知实现

- `ApiClient` 已有 WebSocket 连接；在消息处理中新增 `notify` 分支；
- 创建通知渠道（id `workstation_notify`，IMPORTANCE_HIGH）；
- `Android 13+` 运行时申请 `POST_NOTIFICATIONS` 权限；`AndroidManifest` 声明 `<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>`；
- 通知样式：小标题 + 正文 + 点击进入对应 App。

### 3.5 notify.ps1 接入（防呆）

- 在发 Toast 后，追加一个 HTTP 调用（`Invoke-RestMethod`）到 `http://<host>:8080/api/notify`，传 Title/Message/Type；
- **防呆**：`try/catch` 包裹 + `-ErrorAction SilentlyContinue`；工作台未运行 / 请求失败时**静默失败**，绝不影响现有 Windows Toast 与日志；
- host 可配置：读取环境变量 `WORKSTATION_HOST`（默认 `127.0.0.1`；若手机经局域网连接，则填同一局域网 IP）。建议后续把 host 做成用户可配置，MVP 先固定。

### 3.6 通用性

任何脚本均可 `curl -X POST .../api/notify`，例如 gradle 构建完成、服务器异常、git 提交成功。Claude Code hooks 只是第一个接入方。

## 4. 硬约束

1. **纯视觉/通知层**，不改任何业务逻辑、不破坏现有功能。
2. Web 端**必须保留全部功能性** `id`/`class`/`data-view` 选择器（`#page-title`、`.brand`、`#settings-form`、`#jwt-secret`、`#new-task`、`button[data-view=…]` 等），只改外观——保证 JS 与 `tests/test_web.py` 锚点存活。
3. **零云 / 局域网**：仅同一 WiFi 下、App 运行时可收到；不加云推送、不加第三方服务。
4. `notify.ps1` 改动**绝不破坏现有 Windows 通知**（防呆 + 静默失败）。

## 5. 错误处理与降级

| 场景 | 处理 |
|---|---|
| 手机未连接 / 不在线 | broadcast 无处投递，服务端静默；不重试（局域网会话式） |
| notify.ps1 请求失败 / 工作台未起 | 静默吞掉，仅记本地 log，无打扰 |
| Android 无通知权限 | 系统静默拒绝；引导到系统设置开启 |
| 长时间无事件 | Android 心跳保活（已有 30s 心跳） |

## 6. 测试

- **Web**：浏览器手动验收关键页（仪表盘/看板/笔记/GitHub/设置/开发工具）；跑通 `tests/test_api.py`（6/6）与 `tests/test_web.py`（Playwright，需 chromium）。
- **Android**：`gradlew assembleDebug` 编译通过；真机验收通知权限引导、收到 notify 弹通知。
- **通知通道**：`curl -X POST /api/notify` 手动触发 → 服务端日志 / WS 广播 / Android 通知。
- 回归：确保未破坏现有功能（任务/笔记/GitHub/工具等）。

## 7. 非目标

- 不加云推送 / 第三方推送服务（保持零云）。
- 不做远程通知（不同 WiFi 收到）——若未来需要，另议（公网/FRP）。
- 不做通知自定义规则引擎（先支持列出的 type 与来源）。

## 8. 实施顺序

1. **Web**：tokens → 基础组件 → 仪表盘（星野/轨道环/时间线/氛围）→ 其余面板统一 → 图标/动效 → 回归测试。
2. **Android**：抽取 token/主题 → 统一组件 → 各页视觉 → 通知权限/渠道/监听 → 编译与真机验收。
3. **通知接力**：服务端 `/api/notify` + WS 广播 → Android 通知接收 → `notify.ps1` 接入（防呆）→ 手动验证。

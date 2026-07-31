<p align="center">
  <h1 align="center">Personal Workstation</h1>
  <p align="center">局域网个人工作台 · Python + Android + Electron · 零云依赖</p>
</p>

---

Personal Workstation 是一个面向个人开发者的局域网工作台，所有数据存储在本地 SQLite，无需任何云服务。电脑端运行桌面应用，手机端通过同一 Wi-Fi 即可访问任务、笔记、GitHub 和仪表盘。

## 核心功能

| 模块 | 说明 |
|------|------|
| 仪表盘 | 任务进度环、GitHub 贡献热力图、最近笔记，一站式概览 |
| 任务看板 | 四列看板，支持搜索、筛选、拖拽、撤销和删除 |
| 笔记管理 | 富文本笔记，标题/内容编辑、标签分类、关键词搜索 |
| GitHub 追踪 | 个人资料、仓库、提交记录、动态时间线，支持手动刷新 |
| 实时同步 | REST API + WebSocket，桌面和手机共享数据，操作实时广播 |
| 扫码连接 | 设置页生成二维码，Android 扫码自动填入服务器地址 |
| 局域网发现 | 点击「扫描局域网」自动发现同 Wi-Fi 下的工作台 |
| 系统托盘 | 最小化到 Windows 托盘，关闭窗口不退出，后台静默运行 |

## 项目结构

```
PersonalWorkstationDemo1/
│
├── server/                        FastAPI 服务端
│   ├── run_server.py              启动入口（自动检测局域网 IP）
│   ├── app/
│   │   ├── main.py                应用组装 + 静态文件挂载
│   │   ├── database.py            SQLite 数据库初始化
│   │   ├── config.py              配置管理
│   │   ├── api/                   REST API（tasks / notes / github / dashboard / profile / connect）
│   │   ├── services/              GitHub API 调用与缓存
│   │   ├── websocket/             连接管理与消息推送
│   │   ├── mdns_broadcaster.py    mDNS 局域网广播
│   │   └── udp_discovery.py       UDP 广播发现
│   ├── tests/                     服务端测试
│   └── requirements.txt           Python 依赖
│
├── web/                           浏览器端工作台
│   ├── index.html                 单页面应用
│   ├── app.js                     前端交互逻辑
│   └── styles.css                 深色主题样式
│
├── android/                       Kotlin + Jetpack Compose 客户端
│   └── app/src/main/java/
│       ├── MainActivity.kt        主界面（仪表盘 / 看板 / 笔记 / GitHub / 设置）
│       ├── core/model/            数据模型
│       └── core/network/          API 客户端（OkHttp）
│
├── desktop/                       Electron 桌面壳层
│   ├── main.js                    主进程（窗口管理 + 服务生命周期）
│   ├── preload.js                 安全桥接
│   └── assets/                    应用图标
│
└── memory/                        项目元信息与设计资料
```

## 快速开始

**启动服务端**

```powershell
cd server
python -m pip install -r requirements.txt
python run_server.py
```

浏览器访问 `http://127.0.0.1:8080/`，工作台地址为 `http://127.0.0.1:8080/app/`。

手机和电脑连同一 Wi-Fi 后，启动日志会显示局域网 IP 和二维码，在 Android 端填写即可。

**运行测试**

```powershell
cd server
python -m pytest tests/ -v
```

## GitHub 配置

在桌面端或 Android 端的「设置」页填写 GitHub 用户名和 Token，配置保存在本地 `server/data/settings.json`。Token 建议只授予读取仓库和用户信息的最小权限。

## 构建打包

**Android APK**

```powershell
cd android
.\gradlew.bat assembleDebug
```

输出：`android/app/build/outputs/apk/debug/app-debug.apk`

**Windows 桌面应用**

```powershell
pyinstaller personal-workstation-server.spec       # 打包服务端
cd desktop && npm install && npm run dist           # 构建安装包
```

输出：`desktop/dist/个人工作台 Setup 0.1.0.exe`

> 桌面应用已将服务端打包在内，运行安装后的 `个人工作台.exe` 即可，无需手动管理后台进程。

## 数据与安全

- 所有运行数据存储在 `server/data/`，包括 SQLite 数据库和配置文件
- 不要把 GitHub Token 写入源代码或提交记录
- 服务监听 `0.0.0.0:8080`，适用于局域网访问，请确保防火墙放行

## 真机验收

### 安装 APK

```powershell
cd android
.\gradlew.bat assembleDebug
```

APK 位于 `android/app/build/outputs/apk/debug/app-debug.apk`，通过 USB、微信文件传输或内网共享传到手机安装。

### 连接工作台

**方式一：局域网扫描（推荐）**

1. 手机和电脑连同一 Wi-Fi
2. Android App → 设置 → 连接 → 点击「扫描局域网」
3. 等待 3-5 秒，点击发现的工作台 → 自动填入地址 → 测试连接

设备会同时尝试 mDNS 和 UDP 广播，自动选择可用方式，无需用户干预。

**方式二：扫码连接**

1. 电脑端 → 设置 → 连接，查看二维码
2. 手机 App → 扫描电脑二维码，自动填入地址

**方式三：手动输入**

1. 电脑查看局域网 IP（`ipconfig` → IPv4 地址）
2. 手机设置 → 手动输入 `IP:8080` → 测试连接

### Windows 防火墙放行

连接失败通常是防火墙拦截了 8080 端口：

```powershell
# 管理员 PowerShell
New-NetFirewallRule -DisplayName "Personal Workstation (8080)" -Direction Inbound -Protocol TCP -LocalPort 8080 -Action Allow -Profile Private
```

> 或通过 安全中心 → 防火墙 → 高级设置 → 入站规则 → 新建端口规则（TCP 8080）。

### 验收清单

| 检查项 | 预期 |
|--------|------|
| 仪表盘 | 任务进度环、热力图、最近笔记正常加载 |
| 任务看板 | 能新建、移动、编辑、删除任务卡片 |
| 笔记管理 | 能新建、编辑、搜索、标签过滤笔记 |
| GitHub 追踪 | 仓库列表、提交记录可查看和刷新 |
| 开发日志 | 月历热力图、心情表情、标签输入 |
| 番茄钟 | 倒计时环形进度、预设时长切换 |
| 代码片段 | 多语言语法高亮、编辑保存 |
| 本地 Git | 仓库详情、状态概览 |
| 快捷便签 | 浮动按钮弹出，输入自动保存 |
| 连接设置 | 扫描 / 扫码 / 手动输入均可连接 |
| 实时同步 | 电脑操作后手机端自动刷新 |
| 弱网重连 | 关闭再打开 Wi-Fi，App 可自动恢复 |

### 常见问题

**连接成功但不加载数据？**

服务端需以 `python run_server.py` 启动（监听 `0.0.0.0`），不要用 `127.0.0.1`。

**扫描局域网搜不到？**

- 确认手机和电脑在同一 Wi-Fi 子网
- 设备会自动回退到 UDP 广播，必要时改用扫码或手动输入
- 企业 / 校园网络可能屏蔽组播和 UDP 广播
- Windows 端请运行桌面应用而非直接启动后台服务

**WebSocket 断开？**

正常现象，每 30 秒自动重连。切后台后唤醒可能延迟 5-10 秒恢复。

**APK 安装被拦截？**

Debug APK 的正常提示，手机 设置 → 安全 → 允许安装未知来源。

## License

[Apache License 2.0](LICENSE)

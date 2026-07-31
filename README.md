# Personal Workstation

Personal Workstation 是一个面向个人开发者的局域网工作台，所有数据存储在本地 SQLite，无需任何云服务。电脑端运行桌面应用，手机端通过同一 Wi-Fi 即可访问任务、笔记、GitHub 数据和仪表盘。

## 核心功能

- **仪表盘**：任务进度环、GitHub 贡献热力图、最近笔记、快捷工具入口，一站式概览。
- **任务看板**：四列看板管理任务，支持搜索、列筛选、创建、移动、撤销和删除。
- **笔记管理**：富文本笔记，支持标题/内容编辑、标签分类和关键词搜索。
- **GitHub 追踪**：读取个人资料、仓库、提交记录和动态时间线，支持手动刷新。
- **实时同步**：REST API + WebSocket，桌面端和 Android 端共享同一份本地数据，操作实时广播。
- **二维码连接**：设置页生成局域网连接二维码，Android 扫码自动填入服务器地址。
- **局域网自动发现**：点击「扫描局域网」自动发现同一 Wi-Fi 下的工作台，无需手动输入 IP。
- **系统托盘**：支持最小化到 Windows 系统托盘，关闭窗口不退出程序，后台静默运行。

## 项目结构

```text
PersonalWorkstationDemo1/
├── server/                    # FastAPI 服务端
│   ├── run_server.py          # 启动入口（自动检测局域网 IP）
│   ├── app/
│   │   ├── main.py            # 应用组装 + 静态文件挂载
│   │   ├── database.py        # SQLite 数据库初始化
│   │   ├── config.py          # 配置管理（环境变量 + JSON 配置文件）
│   │   ├── api/               # REST API 模块（tasks/notes/github/dashboard/profile/connect）
│   │   ├── services/          # 业务服务层（GitHub API 调用 + 缓存）
│   │   ├── websocket/         # WebSocket 连接管理与消息处理
│   │   ├── mdns_broadcaster.py # mDNS 局域网广播
│   │   └── udp_discovery.py   # UDP 广播发现
│   ├── tests/                 # 服务端测试
│   └── requirements.txt       # Python 依赖
├── web/                       # 浏览器 / 桌面端 Web 工作台
│   ├── index.html             # 单页面应用
│   ├── app.js                 # 前端交互逻辑
│   └── styles.css             # 深色主题样式
├── android/                   # Kotlin + Jetpack Compose Android 客户端
│   └── app/src/main/java/.../app/
│       ├── MainActivity.kt    # 主界面（仪表盘/看板/笔记/GitHub/设置）
│       ├── core/model/        # 数据模型
│       └── core/network/      # API 客户端（OkHttp）
├── desktop/                   # Electron Windows 桌面壳层
│   ├── main.js                # 主进程（窗口管理 + 服务生命周期）
│   ├── preload.js             # 安全桥接
│   └── assets/                # 应用图标
├── memory/                    # 项目元信息与设计资料
└── TASK_PROGRESS.md           # 开发进度追踪
```

## 快速启动

### 服务端

```powershell
cd server
python -m pip install -r requirements.txt
python run_server.py
```

启动后浏览器访问 `http://127.0.0.1:8080/`，工作台地址为 `http://127.0.0.1:8080/app/`。

手机和电脑连接同一 Wi-Fi 后，启动日志会显示局域网 IP 和二维码，在 Android 设置中填写即可。

### 运行测试

```powershell
cd server
python -m pytest tests/ -v
```

## GitHub 配置

在桌面端或 Android 端的「设置」页中填写 GitHub 用户名和 Token，配置保存在本地 `server/data/settings.json`。Token 建议只授予读取仓库和用户信息所需的最小权限。

## 构建与打包

### Android APK

```powershell
cd android
.\gradlew.bat assembleDebug
```

APK 输出路径：`android/app/build/outputs/apk/debug/app-debug.apk`

### Windows 桌面应用

```powershell
# 1. 打包服务端
pyinstaller personal-workstation-server.spec

# 2. 构建桌面安装包
cd desktop
npm install
npm run dist
```

安装包输出：`desktop/dist/个人工作台 Setup 0.1.0.exe`

> 桌面应用已将服务端打包在内，用户只需运行安装后的 `个人工作台.exe`，无需手动管理后台进程。

## 数据与安全

- 所有运行数据存储在 `server/data/`，包括 SQLite 数据库和配置文件。
- 不要把 GitHub Token 写入源代码、README 或提交记录。
- 服务默认监听 `0.0.0.0:8080`，适用于局域网内设备访问，请确保防火墙允许该端口。

## 真机验收指南

### 1. 安装 APK

```powershell
cd android
.\gradlew.bat assembleDebug
```

APK 位于 `android/app/build/outputs/apk/debug/app-debug.apk`，通过 USB 数据线、微信文件传输或内网文件共享传输到 Android 手机安装。

### 2. 连接工作台

**方式一：局域网扫描（推荐）**

1. 确保手机和电脑连接同一 Wi-Fi
2. 打开 Android App → 设置 → 连接
3. 点击「扫描局域网」，等待 3-5 秒
4. 点击发现的工作台 → 自动填入 IP 和端口 → 测试连接

设备会自动尝试 mDNS 和 UDP 广播两种发现方式，无需用户干预。

**方式二：扫码连接**

1. 电脑端打开工作台 → 设置 → 连接
2. 手机 App → 设置 → 连接 → 扫描电脑二维码
3. 相机扫码后自动填入地址

**方式三：手动输入**

1. 查看电脑局域网 IP（Windows：`ipconfig` → 找 IPv4 地址）
2. 手机 App → 设置 → 连接 → 手动输入 IP:8080 → 测试连接

### 3. Windows 防火墙放行

如果手机连接失败，可能是 Windows 防火墙拦截了 8080 端口：

**方法 A：命令行（管理员 PowerShell）**

```powershell
New-NetFirewallRule -DisplayName "Personal Workstation (8080)" -Direction Inbound -Protocol TCP -LocalPort 8080 -Action Allow -Profile Private
```

**方法 B：图形界面**

1. 打开 Windows 安全中心 → 防火墙和网络保护 → 高级设置
2. 入站规则 → 新建规则 → 端口 → TCP → 8080
3. 允许连接 → 仅勾选「专用」网络 → 命名 `Personal Workstation`

### 4. 验收检查清单

| 检查项 | 预期结果 |
|--------|---------|
| 仪表盘加载 | 显示任务进度环、热力图、最近笔记 |
| 任务看板 CRUD | 能新建、移动、编辑、删除任务卡片 |
| 笔记管理 | 能新建、编辑、搜索、标签过滤笔记 |
| GitHub 追踪 | 显示仓库列表、提交记录，可刷新 |
| 开发日志 | 月历热力图、心情表情、标签输入 |
| 番茄钟 | 倒计时环形进度、预设时长切换 |
| 代码片段 | 支持多语言语法高亮、编辑保存 |
| 本地 Git | 仓库详情、状态概览 |
| 快捷便签 | 全局浮动按钮弹出，输入自动保存 |
| 连接设置 | 扫描局域网 / 扫码 / 手动输入 IP |
| 实时同步 | 电脑端操作后手机端自动刷新 |
| 弱网重连 | 关闭手机 Wi-Fi 再打开，App 可恢复连接 |

### 5. 常见问题

**Q: 连接测试显示成功但不加载数据？**
确保电脑端服务命令是 `python run_server.py`（监听 `0.0.0.0`），不要用 `127.0.0.1` 启动。

**Q: 扫描局域网搜不到？**
- 确认手机和电脑在同一 Wi-Fi 子网
- 如果提示「mDNS 不可用，正在 UDP 广播搜索」说明设备系统 mDNS 受限，App 会自动回退到 UDP 广播
- 部分企业/校园网络可能屏蔽组播和 UDP 广播，请改用扫码或手动输入
- Windows 端请运行桌面应用 `个人工作台.exe` 而非直接启动后台服务

**Q: WebSocket 自动断开？**
正常现象 — 每 30 秒自动重连。切后台后唤醒可能延迟 5-10 秒恢复。

**Q: APK 安装提示「未受信任的安装源」？**
这是 Debug APK 的正常提示，在手机 设置 → 安全 中允许安装未知来源即可。

## License

本项目使用 [Apache License 2.0](LICENSE)。

# Personal Workstation

Personal Workstation 是一个面向个人开发者的局域网工作台，秉承"零云依赖"理念——所有数据存储在本地 SQLite，无需任何云服务。电脑端运行 Python 后端 + Electron 桌面壳层，手机端通过同一 Wi-Fi 即可访问任务、笔记、GitHub 数据和仪表盘。

## 核心功能

- **仪表盘**：任务进度环、GitHub 贡献热力图、最近笔记、快捷工具入口，一站式概览。
- **任务看板**：四列看板管理任务，支持搜索、列筛选、创建、左右移动、撤销和删除。
- **笔记管理**：富文本笔记，支持标题/内容编辑、标签分类和关键词搜索。
- **GitHub 追踪**：读取个人资料、仓库、提交记录和动态时间线，支持手动刷新。
- **实时同步**：REST API + WebSocket，桌面端和 Android 端共享同一份本地数据，操作实时广播。
- **二维码连接**：电脑端设置页生成局域网连接二维码，Android 扫码自动填入服务器地址。
- **桌面服务**：Electron 桌面端内置 Python 服务，打开即启动、关闭即停止，无需手动管理进程。
- **本地配置**：GitHub Token、服务 IP 和端口保存在 `server/data/`（已加入 .gitignore），不提交到仓库。

## 项目结构

```text
PersonalWorkstationDemo1/
├── server/                    # FastAPI 服务端
│   ├── run_server.py          # 启动入口（自动检测局域网 IP）
│   ├── app/
│   │   ├── main.py            # 应用组装 + 静态文件挂载
│   │   ├── database.py        # SQLite 数据库初始化（7 张表）
│   │   ├── config.py          # 配置管理（环境变量 + JSON 配置文件）
│   │   ├── api/               # REST API 模块（tasks/notes/github/dashboard/profile/connect）
│   │   ├── services/          # 业务服务层（GitHub API 调用 + 缓存）
│   │   └── websocket/         # WebSocket 连接管理与消息处理
│   ├── tests/                 # 服务端测试
│   └── requirements.txt       # Python 依赖
├── web/                       # 浏览器 / 桌面端 Web 工作台
│   ├── index.html             # 单页面应用（侧边栏 + 模态框布局）
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
├── personal-workstation-server.spec  # PyInstaller 打包配置
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

在桌面端或 Android 端的"设置"页中填写 GitHub 用户名和 Token。配置写入 `server/data/settings.json`（该目录已加入 .gitignore）。Token 建议只授予读取仓库和用户信息所需的最小权限。

## 构建与打包

### Android APK

```powershell
cd android
.\gradlew.bat assembleDebug
```

APK 输出路径：`android/app/build/outputs/apk/debug/app-debug.apk`

### Windows 桌面安装包

```powershell
# 步骤 1：打包 Python 服务为可执行文件
pyinstaller personal-workstation-server.spec --noconfirm --clean

# 步骤 2：构建 Electron + NSIS 安装包
cd desktop
npm install
npm run dist
```

安装包输出路径：`desktop/dist/`

## 数据与安全

- 所有运行数据位于 `server/data/`，包括 SQLite 数据库和配置文件，不会被 Git 跟踪。
- 不要把 GitHub Token 写入源代码、README 或提交记录。
- 服务默认监听 `0.0.0.0:8080`，适用于局域网内设备访问，请确保防火墙允许该端口。

## License

本项目使用 [Apache License 2.0](LICENSE)。
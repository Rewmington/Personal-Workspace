# Personal Workspace

Personal Workspace 是一个面向个人开发者的局域网工作台：电脑端运行本地服务和 Windows 桌面应用，手机端通过同一 Wi-Fi 访问任务、笔记和 GitHub 信息。

## 功能

- 任务看板：按列管理任务，支持左右移动和撤销操作。
- 笔记：创建、编辑、搜索和标签筛选。
- GitHub：读取个人资料、头像、仓库、提交和活动，并支持打开仓库链接。
- 双端同步：REST API + WebSocket，电脑端和 Android 端共享同一份本地数据。
- 桌面服务：打开 Windows 软件时启动内置服务，关闭软件后服务随之停止。
- 本地配置：GitHub 用户名、Token、服务 IP 和端口保存在本机，不提交到仓库。

## 项目结构

```text
server/    FastAPI 服务端、SQLite 数据库和 WebSocket
web/       浏览器/桌面端工作台页面
android/   Kotlin + Jetpack Compose Android 客户端
desktop/   Electron Windows 桌面端
.claude/   UI 原型和项目协作资料
```

## 快速启动服务端

```powershell
cd server
python -m pip install -r requirements.txt
python run_server.py
```

浏览器访问 `http://127.0.0.1:8080/`，工作台地址为 `http://127.0.0.1:8080/app/`。手机和电脑连接同一 Wi-Fi 后，在 Android 设置中填写电脑的局域网 IP 和端口 `8080`。

## GitHub 配置

在桌面端或 Android 端的连接设置中填写 GitHub 用户名和 Token。配置写入本地 `server/data/`，该目录已被 Git 忽略。Token 建议只授予读取仓库和用户信息所需的权限。

## 构建

### Android APK

```powershell
cd android
.\gradlew.bat assembleDebug
```

APK 输出在 `android/app/build/outputs/apk/debug/app-debug.apk`。

### Windows 桌面安装包

```powershell
# 在项目根目录执行
pyinstaller personal-workstation-server.spec --noconfirm --clean
cd desktop
npm install
npm run dist
```

安装包输出在 `desktop/dist/`。

## 数据和安全

运行数据位于 `server/data/`，包含本地 SQLite 数据库和设置文件，不会被 Git 跟踪。不要把 GitHub Token 写入源代码、README 或提交记录。

## License

本项目使用仓库中的 [MIT License](LICENSE)。
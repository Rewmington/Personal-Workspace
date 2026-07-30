# 个人工作台

局域网双端个人工作台：Windows FastAPI 服务端 + Kotlin/Compose Android 客户端。

## 当前状态

第一版 MVP 工程已建立：

- `server/`：可运行的 REST + WebSocket 服务端，SQLite 自动初始化
- `android/`：可导入 Android Studio 的 Compose 客户端骨架
- `.claude/plans/ui-prototypes/`：原有 UI 视觉原型

## 快速启动服务端

```powershell
cd server
python -m pip install -r requirements.txt
python run_server.py
```

Windows 用户也可以双击 `server/start_server.bat` 启动；如果 `8080` 已被占用，窗口会显示具体错误。

启动后访问 `http://127.0.0.1:8080/docs` 查看接口文档。

直接打开 `http://127.0.0.1:8080/` 会显示服务状态；业务接口统一位于 `/api/` 下。

电脑端工作台界面位于 `http://127.0.0.1:8080/app/`。

Android 真机测试时，手机和电脑需在同一 Wi-Fi，并在客户端设置页填写服务端启动时打印的局域网 IP（例如 `192.168.1.34`）和端口 `8080`。

## GitHub 配置

```powershell
$env:GITHUB_USERNAME = "your-github-name"
$env:GITHUB_TOKEN = "ghp_xxx" # 可选，建议配置以提高 API 限额
```

然后调用 `POST /api/github/refresh` 刷新缓存。

## Windows 桌面版

桌面软件由 Electron 工作台和内置 FastAPI 服务组成，双击安装包后会自动启动本地服务并打开工作台：

`desktop/dist/个人工作台 Setup 0.1.0.exe`

安装后的任务、笔记、GitHub 配置保存在 Windows 用户数据目录，不依赖 Python 环境。软件默认使用 `127.0.0.1:8080`；如果端口被占用，请先关闭占用端口的服务。

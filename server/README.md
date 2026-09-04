# Personal Workstation Server

局域网个人工作台的 FastAPI 服务端。当前 MVP 提供：

- 任务看板、任务移动和列管理
- 笔记 CRUD、搜索和标签过滤
- GitHub 仓库/提交/事件缓存与手动刷新
- 仪表盘汇总和 GitHub 热力图数据
- WebSocket 全量同步与增量事件广播

## 启动

```powershell
cd server
python -m pip install -r requirements.txt
python run_server.py
```

也可以直接双击 `start_server.bat`。如果端口已被占用，窗口会保留错误信息；默认端口是 `8080`。

默认监听 `0.0.0.0:8080`。可用环境变量覆盖：

`WORKSTATION_PORT`、`WORKSTATION_DB`、`GITHUB_USERNAME`、`GITHUB_TOKEN`。

首次启动会创建 `data/workstation.db` 并写入少量示例看板数据。接口文档位于 `/docs`。
桌面 Web 工作台位于 `/app/`。

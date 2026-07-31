from __future__ import annotations

from contextlib import asynccontextmanager
import os
from pathlib import Path

from fastapi import FastAPI, WebSocket
from fastapi.responses import RedirectResponse
from fastapi.staticfiles import StaticFiles
from fastapi.middleware.cors import CORSMiddleware

from .api import backup, connect, dashboard, github, notes, profile, tasks, snippets, git, focus, logs
from .config import settings
from .database import init_db
from .mdns_broadcaster import get_mdns_broadcaster
from .udp_discovery import get_udp_discovery
from .websocket.handler import websocket_endpoint


@asynccontextmanager
async def lifespan(_: FastAPI):
    init_db()
    # 启动 mDNS 广播
    try:
        get_mdns_broadcaster().register(
            host=settings.host,
            port=settings.port,
            device_name="个人工作台",
        )
    except Exception:
        import logging
        logging.getLogger("mdns").warning("mDNS 广播启动失败，局域网自动发现将不可用", exc_info=True)
    # 启动 UDP 广播发现（NsdManager 不可用时的回退）
    get_udp_discovery().start(
        host=settings.host,
        port=settings.port,
        device_name=settings.display_name or "个人工作台",
    )
    yield
    # 停止 mDNS 广播
    try:
        get_mdns_broadcaster().unregister()
    except Exception:
        import logging
        logging.getLogger("mdns").debug("mDNS 广播停止时出错", exc_info=True)
    # 停止 UDP 发现
    try:
        get_udp_discovery().stop()
    except Exception:
        import logging
        logging.getLogger("udp_discovery").debug("UDP 发现停止时出错", exc_info=True)


app = FastAPI(
    title="Personal Workstation API",
    version="0.1.0",
    description="局域网个人工作台的任务、笔记、GitHub 和仪表盘服务",
    lifespan=lifespan,
)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)
app.include_router(tasks.router)
app.include_router(notes.router)
app.include_router(github.router)
app.include_router(profile.router)
app.include_router(connect.router)
app.include_router(backup.router)
app.include_router(dashboard.router)
app.include_router(snippets.router)
app.include_router(git.router)
app.include_router(focus.router)
app.include_router(logs.router)

WEB_DIR = Path(os.getenv("WORKSTATION_WEB_DIR", str(Path(__file__).resolve().parents[2] / "web")))
if WEB_DIR.exists():
    app.mount("/app", StaticFiles(directory=WEB_DIR, html=True), name="desktop-app")


@app.get("/app", include_in_schema=False)
def desktop_app_redirect() -> RedirectResponse:
    return RedirectResponse(url="/app/")


@app.get("/", tags=["system"])
def root() -> dict[str, str]:
    return {
        "service": "personal-workstation",
        "message": "个人工作台服务已运行",
        "docs": "/docs",
        "health": "/api/health",
        "websocket": "/ws",
    }


@app.get("/api/health", tags=["system"])
def health() -> dict[str, str]:
    return {"status": "ok", "service": "personal-workstation"}


@app.get("/api/version", tags=["system"])
def version() -> dict[str, str]:
    return {"version": app.version, "name": app.title}


@app.websocket("/ws")
async def websocket_route(websocket: WebSocket):
    await websocket_endpoint(websocket)



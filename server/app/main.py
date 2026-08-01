from __future__ import annotations

import asyncio
from contextlib import asynccontextmanager
import logging
import os
from pathlib import Path

from fastapi import FastAPI, WebSocket
from fastapi.responses import RedirectResponse
from fastapi.staticfiles import StaticFiles
from fastapi.middleware.cors import CORSMiddleware

from .api import backup, clipboard, connect, dashboard, github, http_client, notes, profile, tasks, snippets, git, focus, logs
from .config import settings
from .database import init_db
from .mdns_broadcaster import get_mdns_broadcaster
from .services.github import refresh_github
from .udp_discovery import get_udp_discovery
from .websocket.handler import websocket_endpoint

_logger = logging.getLogger("lifespan")
_github_refresh_task: asyncio.Task[None] | None = None

# ── GitHub 后台定时刷新 ──

async def _github_refresh_loop() -> None:
    """每隔 GITHUB_REFRESH_MINUTES（默认 30 分钟）拉取一次 GitHub 数据。"""
    await asyncio.sleep(30)  # 启动后先等 30 秒，让服务完全就绪
    interval = int(os.getenv("GITHUB_REFRESH_MINUTES", "30"))
    logger = logging.getLogger("github.background")
    while True:
        try:
            if settings.github_token and settings.github_username:
                logger.info("开始后台刷新 GitHub 数据...")
                await refresh_github()
                logger.info("后台 GitHub 刷新完成")
        except Exception:
            logger.warning("后台 GitHub 刷新失败", exc_info=True)
        await asyncio.sleep(interval * 60)


async def _start_discovery_services() -> None:
    """延后启动网络发现服务，避免阻塞服务 readiness。"""
    await asyncio.sleep(0.3)
    try:
        get_mdns_broadcaster().register(
            host=settings.host,
            port=settings.port,
            device_name="个人工作台",
        )
    except Exception:
        logging.getLogger("mdns").warning("mDNS 广播启动失败，局域网自动发现将不可用", exc_info=True)
    get_udp_discovery().start(
        host=settings.host,
        port=settings.port,
        device_name=settings.display_name or "个人工作台",
    )


@asynccontextmanager
async def lifespan(_: FastAPI):
    global _github_refresh_task
    init_db()
    # 网络发现服务延后后台启动，不阻塞 /api/health 就绪
    asyncio.create_task(_start_discovery_services())
    # 启动 GitHub 后台定时刷新
    _github_refresh_task = asyncio.create_task(_github_refresh_loop())
    yield
    # 停止 GitHub 后台刷新
    if _github_refresh_task:
        _github_refresh_task.cancel()
        try:
            await _github_refresh_task
        except asyncio.CancelledError:
            pass
    # 停止 mDNS 广播
    try:
        get_mdns_broadcaster().unregister()
    except Exception:
        logging.getLogger("mdns").debug("mDNS 广播停止时出错", exc_info=True)
    # 停止 UDP 发现
    try:
        get_udp_discovery().stop()
    except Exception:
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
app.include_router(http_client.router)
app.include_router(clipboard.router)

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



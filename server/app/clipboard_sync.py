"""剪贴板自动同步：去重状态 + 发布 + 后台轮询（服务端即 Windows 本机）。"""

from __future__ import annotations

import asyncio
import logging

from .api.clipboard import add_clipboard_record
from .clipboard_win import get_clipboard_text, set_clipboard_text
from .config import settings
from .websocket.manager import manager

_logger = logging.getLogger("clipboard.sync")


class ClipboardSyncState:
    """最近已知剪贴板文本，用于跨来源去重（防回环）。"""

    def __init__(self) -> None:
        self.last_text: str | None = None


state = ClipboardSyncState()


async def publish_clipboard(text: str, source: str) -> None:
    """入库 + 广播 + 更新 last_text。调用方保证传入非空文本。"""
    if not text or text == state.last_text:
        return
    add_clipboard_record(text, source)
    state.last_text = text
    await manager.broadcast(
        {"type": "clipboard", "data": {"content": text, "source": source}}
    )


async def handle_local_text(text: str | None, source: str) -> bool:
    """处理一段新文本（REST 上报 / 本机 monitor 均走此路径）。

    - 开关关闭：不写本机、不广播（历史入库由调用方负责）。
    - 与 last_text 相同：跳过（防回环）。
    - source != "windows"：先把内容落到 Windows 系统剪贴板（电脑端真同步）。
    - 统一入库 + 广播 + 更新 last_text。
    """
    if settings.clipboard_sync_enabled is False:
        return False
    if not text or text == state.last_text:
        return False
    if source != "windows":
        await asyncio.to_thread(set_clipboard_text, text)
    await publish_clipboard(text, source)
    return True


async def clipboard_monitor_loop(interval: float = 0.8) -> None:
    """轮询本机剪贴板，变化即发布（source=windows）。永不因异常退出。"""
    while True:
        try:
            if settings.clipboard_sync_enabled is not False:
                text = await asyncio.to_thread(get_clipboard_text)
                if text:
                    try:
                        await publish_clipboard(text, "windows")
                    except Exception:
                        _logger.warning("剪贴板发布失败", exc_info=True)
        except Exception:
            _logger.debug("剪贴板读取异常（忽略）", exc_info=True)
        await asyncio.sleep(interval)
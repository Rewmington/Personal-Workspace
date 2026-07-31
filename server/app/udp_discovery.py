"""UDP 广播发现 —— NsdManager 不可用时的回退方案。

服务器监听 UDP 端口，收到 WORKSTATION_DISCOVER 广播后
返回服务地址信息，供 Android 客户端自动发现。
"""
from __future__ import annotations

import json
import logging
import socket
import threading
from typing import Any

logger = logging.getLogger("udp_discovery")

DISCOVERY_PORT = 5354
BROADCAST_MAGIC = b"WORKSTATION_DISCOVER"
RESPONSE_MAGIC = b"WORKSTATION_HERE"

_udp_instance: UdpDiscovery | None = None


def get_udp_discovery() -> UdpDiscovery:
    global _udp_instance
    if _udp_instance is None:
        _udp_instance = UdpDiscovery()
    return _udp_instance


class UdpDiscovery:
    """UDP 广播响应器，在后台线程运行。"""

    def __init__(self) -> None:
        self._socket: socket.socket | None = None
        self._thread: threading.Thread | None = None
        self._running = False
        self._host: str = "0.0.0.0"
        self._port: int = 8080
        self._device_name: str = "个人工作台"

    @property
    def active(self) -> bool:
        return self._running

    def start(self, host: str = "0.0.0.0", port: int = 8080, device_name: str = "个人工作台") -> None:
        if self._running:
            return
        self._host = host
        self._port = port
        self._device_name = device_name
        self._running = True
        self._thread = threading.Thread(target=self._listen_loop, daemon=True, name="udp-discovery")
        self._thread.start()
        logger.info("UDP 发现服务已启动 (端口 %d)", DISCOVERY_PORT)

    def stop(self) -> None:
        self._running = False
        sock = self._socket
        if sock is not None:
            try:
                sock.close()
            except OSError:
                pass
        self._socket = None
        logger.info("UDP 发现服务已停止")

    def _make_response(self) -> dict[str, Any]:
        """构建发现响应数据。"""
        # 获取本机局域网 IP（非 127.x 非 0.0.0.0 的第一个）
        local_ip = "unknown"
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            s.settimeout(0.1)
            s.connect(("8.8.8.8", 80))
            local_ip = s.getsockname()[0]
            s.close()
        except Exception:
            pass

        return {
            "service": "personal-workstation",
            "host": local_ip,
            "port": self._port,
            "name": self._device_name,
        }

    def _listen_loop(self) -> None:
        try:
            self._socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            self._socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            self._socket.settimeout(2.0)
            self._socket.bind(("0.0.0.0", DISCOVERY_PORT))
        except OSError as e:
            logger.warning("UDP 发现绑定失败: %s", e)
            self._running = False
            return

        while self._running:
            try:
                data, addr = self._socket.recvfrom(1024)
                if data.strip() == BROADCAST_MAGIC:
                    resp = json.dumps(self._make_response(), ensure_ascii=False)
                    self._socket.sendto(resp.encode("utf-8"), addr)
                    logger.debug("UDP 发现响应 -> %s:%d", addr[0], addr[1])
            except socket.timeout:
                continue
            except OSError:
                if self._running:
                    logger.debug("UDP 发现 socket 异常", exc_info=True)
                break

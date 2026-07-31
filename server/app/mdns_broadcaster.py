"""
mDNS 广播模块 — 使用 zeroconf 在局域网注册个人工作台服务，
使 Android 客户端可以通过 NsdManager 自动发现服务端地址。
"""
from __future__ import annotations

import logging
import socket
import threading
from typing import Optional

from zeroconf import IPVersion, ServiceInfo, Zeroconf

logger = logging.getLogger("mdns")

SERVICE_TYPE = "_personal-workstation._tcp.local."
SERVICE_NAME = "个人工作台"


class WorkstationMDNS:
    """管理 mDNS 服务注册/注销的单例。"""

    def __init__(self) -> None:
        self._zc: Optional[Zeroconf] = None
        self._info: Optional[ServiceInfo] = None
        self._lock = threading.Lock()

    @staticmethod
    def _local_ip() -> str:
        """获取本机局域网 IPv4 地址。"""
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        try:
            s.connect(("8.8.8.8", 80))
            return s.getsockname()[0]
        except OSError:
            return "127.0.0.1"
        finally:
            s.close()

    @property
    def active(self) -> bool:
        return self._zc is not None

    def register(self, host: str, port: int, device_name: str = "个人工作台") -> None:
        """注册 mDNS 服务，开始广播。"""
        with self._lock:
            if self._zc is not None:
                return  # 已经注册

        bind_ip = host if host != "0.0.0.0" else self._local_ip()

        props: dict[str | bytes, bytes | str | bool | int | float] = {
            "host": bind_ip,
            "port": port,
            "device_name": device_name,
            "v": "1",
        }

        self._info = ServiceInfo(
            SERVICE_TYPE,
            f"{SERVICE_NAME}.{SERVICE_TYPE}",
            addresses=[socket.inet_aton(bind_ip)],
            port=port,
            properties=props,
            server=f"{socket.gethostname()}.local.",
        )

        self._zc = Zeroconf(ip_version=IPVersion.V4Only)
        self._zc.register_service(self._info, ttl=120)
        logger.info(
            "mDNS 广播已启动: %s (%s:%d)",
            SERVICE_TYPE,
            bind_ip,
            port,
        )

    def update(self, host: str, port: int, device_name: str = "个人工作台") -> None:
        """更新 mDNS 注册信息（例如配置改变后重新广播）。"""
        self.unregister()
        self.register(host, port, device_name)

    def unregister(self) -> None:
        """注销 mDNS 服务，停止广播。"""
        with self._lock:
            zc = self._zc
            info = self._info
            self._zc = None
            self._info = None

        if zc is not None and info is not None:
            zc.unregister_service(info)
        if zc is not None:
            zc.close()
        logger.info("mDNS 广播已停止")


# 全局单例
_broadcaster: Optional[WorkstationMDNS] = None


def get_mdns_broadcaster() -> WorkstationMDNS:
    global _broadcaster
    if _broadcaster is None:
        _broadcaster = WorkstationMDNS()
    return _broadcaster

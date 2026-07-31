from __future__ import annotations

from io import BytesIO
from urllib.parse import urlparse

import qrcode
from fastapi import APIRouter, HTTPException, Query
from fastapi.responses import Response

from ..mdns_broadcaster import get_mdns_broadcaster

router = APIRouter(prefix="/api/connect", tags=["connect"])


@router.get("/qr", response_class=Response)
def connection_qr(url: str = Query(min_length=10, max_length=500)) -> Response:
    parsed = urlparse(url)
    if parsed.scheme not in {"http", "https"} or not parsed.hostname:
        raise HTTPException(status_code=400, detail="连接地址格式不正确")
    image = qrcode.make(url)
    output = BytesIO()
    image.save(output, format="PNG")
    return Response(content=output.getvalue(), media_type="image/png", headers={"Cache-Control": "no-store"})


@router.get("/discover")
def discover_info():
    """返回 mDNS 广播信息，供客户端参考。"""
    b = get_mdns_broadcaster()
    return {
        "active": b.active,
        "service_type": "_personal-workstation._tcp.local.",
        "service_name": "个人工作台",
    }

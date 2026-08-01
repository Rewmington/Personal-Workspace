from __future__ import annotations

import json
import time
from datetime import datetime, timezone

import httpx
from fastapi import APIRouter
from pydantic import BaseModel

from ..database import connection

router = APIRouter(prefix="/api/http-client", tags=["http-client"])


class HeaderItem(BaseModel):
    key: str
    value: str


class SendRequest(BaseModel):
    method: str = "GET"
    url: str
    headers: list[HeaderItem] = []
    body: str = ""
    content_type: str = "json"


@router.post("/send")
async def send(request: SendRequest) -> dict[str, object]:
    """代理发送 HTTP 请求，绕过浏览器跨域限制。"""
    url = request.url.strip()
    if not url:
        return {"ok": False, "status": 0, "time_ms": 0, "headers": {}, "body": "", "error": "URL 不能为空"}
    if "://" not in url:
        url = "https://" + url

    req_headers: dict[str, str] = {h.key: h.value for h in request.headers if h.key.strip()}

    # 构造请求体
    req_body: str | bytes = ""
    if request.method.upper() in ("POST", "PUT", "PATCH") and request.body.strip():
        if request.content_type == "json":
            try:
                json.loads(request.body)
            except json.JSONDecodeError:
                return {"ok": False, "status": 0, "time_ms": 0, "headers": {}, "body": "", "error": "请求体不是合法 JSON"}
            req_body = request.body.encode()
            if "content-type" not in {k.lower() for k in req_headers}:
                req_headers["Content-Type"] = "application/json"
        elif request.content_type == "form":
            # body 为键值对多行格式: key=value 每行一对
            form_body: str = ""
            for line in request.body.strip().split("\n"):
                line = line.strip()
                if "=" in line:
                    k, v = line.split("=", 1)
                    if form_body:
                        form_body += "&"
                    form_body += f"{k}={v}"
            req_body = form_body.encode()
            if "content-type" not in {k.lower() for k in req_headers}:
                req_headers["Content-Type"] = "application/x-www-form-urlencoded"
        else:
            req_body = request.body.encode()

    # 发送请求并计时
    start = time.monotonic()
    try:
        async with httpx.AsyncClient(timeout=httpx.Timeout(30.0), follow_redirects=True) as client:
            resp = await client.request(
                method=request.method.upper(),
                url=url,
                headers=req_headers,
                content=None if not req_body else req_body,
            )
        elapsed_ms = round((time.monotonic() - start) * 1000)
        resp_headers = dict(resp.headers)
        resp_status = resp.status_code
        resp_body = resp.text

        # 自动检测 JSON 格式化
        pretty_body = resp_body
        try:
            parsed = json.loads(resp_body)
            pretty_body = json.dumps(parsed, ensure_ascii=False, indent=2)
        except (json.JSONDecodeError, TypeError):
            pass

        result = {"ok": True, "status": resp_status, "time_ms": elapsed_ms, "headers": resp_headers, "body": pretty_body, "raw_body": resp_body}
    except httpx.TimeoutException:
        elapsed_ms = round((time.monotonic() - start) * 1000)
        result = {"ok": False, "status": 0, "time_ms": elapsed_ms, "headers": {}, "body": "", "error": "请求超时（30s）"}
    except httpx.InvalidURL:
        result = {"ok": False, "status": 0, "time_ms": 0, "headers": {}, "body": "", "error": "URL 格式不合法"}
    except Exception as exc:
        elapsed_ms = round((time.monotonic() - start) * 1000)
        result = {"ok": False, "status": 0, "time_ms": elapsed_ms, "headers": {}, "body": "", "error": str(exc)}

    # 自动保存历史记录
    try:
        now = datetime.now(timezone.utc).isoformat()
        with connection() as conn:
            conn.execute(
                """INSERT INTO http_requests 
                   (method, url, headers, body, content_type, response_status, response_time_ms, response_headers, response_body, is_favorite, created_at)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?)""",
                (
                    request.method.upper(),
                    request.url,
                    json.dumps([{"key": h.key, "value": h.value} for h in request.headers]),
                    request.body,
                    request.content_type,
                    result["status"],
                    result["time_ms"],
                    json.dumps(result.get("headers", {})),
                    result.get("raw_body", ""),
                    now,
                ),
            )
            conn.execute("DELETE FROM http_requests WHERE id NOT IN (SELECT id FROM http_requests ORDER BY created_at DESC LIMIT 50)")
    except Exception:
        pass

    return result


@router.get("/history")
def history(limit: int = 50, favorites_only: bool = False) -> dict[str, object]:
    """获取请求历史。收藏项置顶。"""
    with connection() as conn:
        if favorites_only:
            rows = conn.execute(
                "SELECT id, method, url, headers, body, content_type, response_status, response_time_ms, is_favorite, created_at FROM http_requests WHERE is_favorite = 1 ORDER BY created_at DESC LIMIT ?",
                (limit,),
            ).fetchall()
        else:
            rows = conn.execute(
                "SELECT id, method, url, headers, body, content_type, response_status, response_time_ms, is_favorite, created_at FROM http_requests ORDER BY is_favorite DESC, created_at DESC LIMIT ?",
                (limit,),
            ).fetchall()
    items: list[dict[str, object]] = []
    for r in rows:
        items.append({
            "id": r["id"],
            "method": r["method"],
            "url": r["url"],
            "headers": json.loads(r["headers"]) if r["headers"] else [],
            "body": r["body"],
            "content_type": r["content_type"],
            "response_status": r["response_status"],
            "response_time_ms": r["response_time_ms"],
            "is_favorite": bool(r["is_favorite"]),
            "created_at": r["created_at"],
        })
    return {"items": items}


@router.put("/history/{req_id}/star")
def toggle_star(req_id: int) -> dict[str, object]:
    """切换收藏状态。"""
    with connection() as conn:
        current = conn.execute("SELECT is_favorite FROM http_requests WHERE id = ?", (req_id,)).fetchone()
        if current is None:
            return {"ok": False, "error": "记录不存在"}
        new_val = 0 if current["is_favorite"] else 1
        conn.execute("UPDATE http_requests SET is_favorite = ? WHERE id = ?", (new_val, req_id))
        conn.commit()
    return {"ok": True, "is_favorite": bool(new_val)}


@router.delete("/history/{req_id}")
def delete_history(req_id: int) -> dict[str, object]:
    """删除一条历史记录。"""
    with connection() as conn:
        conn.execute("DELETE FROM http_requests WHERE id = ?", (req_id,))
        conn.commit()
    return {"ok": True}

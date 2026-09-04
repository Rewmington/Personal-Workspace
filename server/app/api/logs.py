from __future__ import annotations
import json
from datetime import date, timedelta
from fastapi import APIRouter, HTTPException, Query
from ..database import connection, json_load, row_dict, utc_now
from ..schemas import DevLog, DevLogUpdate
router = APIRouter(prefix="/api/logs", tags=["logs"])

def _log(row):
    data = row_dict(row); data["tags"] = json_load(data["tags"], []); data["commits"] = json_load(data["commits"], []); return DevLog.model_validate(data)
def _get(day: str):
    with connection() as conn:
        row = conn.execute("SELECT * FROM dev_logs WHERE date=?", (day,)).fetchone()
        if row is None:
            now=utc_now(); cur=conn.execute("INSERT INTO dev_logs(date,created_at,updated_at) VALUES(?,?,?)", (day,now,now)); row=conn.execute("SELECT * FROM dev_logs WHERE id=?", (cur.lastrowid,)).fetchone()
    return _log(row)
@router.get("/today", response_model=DevLog)
def today(): return _get(date.today().isoformat())
@router.get("", response_model=DevLog)
def by_date(date_value: str = Query(..., alias="date")): return _get(date_value)
@router.put("/{log_id}", response_model=DevLog)
def update(log_id: int, payload: DevLogUpdate):
    with connection() as conn:
        if conn.execute("SELECT 1 FROM dev_logs WHERE id=?", (log_id,)).fetchone() is None: raise HTTPException(404,"日志不存在")
        conn.execute("UPDATE dev_logs SET content=?,mood=?,tags=?,commits=?,updated_at=? WHERE id=?", (payload.content,payload.mood,json.dumps(payload.tags,ensure_ascii=False),json.dumps(payload.commits,ensure_ascii=False),utc_now(),log_id))
        return _log(conn.execute("SELECT * FROM dev_logs WHERE id=?", (log_id,)).fetchone())
@router.get("/calendar")
def calendar(year: int, month: int):
    prefix=f"{year:04d}-{month:02d}"; with_conn=[]
    with connection() as conn: rows=conn.execute("SELECT date,length(content) length FROM dev_logs WHERE date LIKE ?", (prefix+"%",)).fetchall()
    return [dict(r) for r in rows]
@router.get("/streak")
def streak():
    with connection() as conn: rows=conn.execute("SELECT date FROM dev_logs WHERE length(content)>0 ORDER BY date DESC").fetchall()
    count=0; expected=date.today()
    for row in rows:
        d=date.fromisoformat(row["date"])
        if d != expected: break
        count += 1; expected -= timedelta(days=1)
    return {"streak": count}
@router.delete("/{log_id}", status_code=204)
def delete(log_id: int):
    with connection() as conn: cur=conn.execute("DELETE FROM dev_logs WHERE id=?", (log_id,))
    if cur.rowcount==0: raise HTTPException(404,"日志不存在")

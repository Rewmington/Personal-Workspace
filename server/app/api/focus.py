from __future__ import annotations
from datetime import datetime, timedelta
from fastapi import APIRouter, HTTPException, Query
from ..database import connection, row_dict, utc_now
from ..schemas import FocusStart, FocusStop
router = APIRouter(prefix="/api/focus", tags=["focus"])

@router.post("/start")
def start(payload: FocusStart):
    with connection() as conn:
        cur = conn.execute("INSERT INTO focus_sessions(task_id,task_title,duration,started_at) VALUES(?,?,?,?)", (payload.task_id,payload.task_title,payload.duration,utc_now()))
        return dict(conn.execute("SELECT * FROM focus_sessions WHERE id=?", (cur.lastrowid,)).fetchone())

def _finish(session_id: int, status: str, actual: int | None):
    with connection() as conn:
        row = conn.execute("SELECT * FROM focus_sessions WHERE id=?", (session_id,)).fetchone()
        if row is None: raise HTTPException(404, "专注记录不存在")
        if actual is None:
            try: actual = max(0, int((datetime.now().astimezone() - datetime.fromisoformat(row["started_at"])).total_seconds()))
            except ValueError: actual = row["duration"]
        conn.execute("UPDATE focus_sessions SET status=?,actual=?,ended_at=? WHERE id=?", (status, actual, utc_now(), session_id))
        return dict(conn.execute("SELECT * FROM focus_sessions WHERE id=?", (session_id,)).fetchone())

@router.put("/{session_id}/stop")
def stop(session_id: int, payload: FocusStop = FocusStop()): return _finish(session_id, "completed", payload.actual)
@router.put("/{session_id}/interrupt")
def interrupt(session_id: int, payload: FocusStop = FocusStop()): return _finish(session_id, "interrupted", payload.actual)
@router.get("/today")
def today():
    day = datetime.now().astimezone().date().isoformat()
    with connection() as conn:
        rows = conn.execute("SELECT * FROM focus_sessions WHERE started_at LIKE ? ORDER BY id DESC", (day+"%",)).fetchall()
    return {"count": len(rows), "total_minutes": sum(r["actual"] for r in rows)//60, "sessions": [dict(r) for r in rows]}
@router.get("/stats")
def stats(period: str = Query("week", pattern="^(week|month)$")):
    days = 7 if period == "week" else 30; start = (datetime.now().astimezone() - timedelta(days=days-1)).date().isoformat()
    with connection() as conn: rows = conn.execute("SELECT substr(started_at,1,10) date, count(*) count, sum(actual) actual FROM focus_sessions WHERE started_at>=? GROUP BY date ORDER BY date", (start,)).fetchall()
    return {"period": period, "days": [dict(r) for r in rows], "total": sum((r["actual"] or 0) for r in rows)}
@router.get("/history")
def history(limit: int = Query(50, ge=1, le=200)):
    with connection() as conn: rows = conn.execute("SELECT * FROM focus_sessions ORDER BY id DESC LIMIT ?", (limit,)).fetchall()
    return [dict(r) for r in rows]

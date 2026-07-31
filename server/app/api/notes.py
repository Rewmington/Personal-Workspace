from __future__ import annotations

import json

from fastapi import APIRouter, HTTPException, Query

from ..database import connection, json_load, row_dict, utc_now
from ..schemas import Note, NoteCreate, NoteUpdate
from ..websocket.manager import manager


router = APIRouter(prefix="/api/notes", tags=["notes"])


def _note(row) -> Note:
    data = row_dict(row)
    data["tags"] = json_load(data["tags"], [])
    return Note.model_validate(data)


@router.get("", response_model=list[Note])
def list_notes(search: str | None = Query(default=None), tag: str | None = Query(default=None)) -> list[Note]:
    query = "SELECT * FROM notes"
    clauses = []
    values: list[str] = []
    if search:
        clauses.append("(title LIKE ? OR content LIKE ?)")
        values.extend([f"%{search}%", f"%{search}%"])
    if tag:
        clauses.append("tags LIKE ?")
        values.append(f'%"{tag}"%')
    if clauses:
        query += " WHERE " + " AND ".join(clauses)
    query += " ORDER BY updated_at DESC, id DESC"
    with connection() as conn:
        rows = conn.execute(query, values).fetchall()
    return [_note(row) for row in rows]


@router.post("", response_model=Note, status_code=201)
async def create_note(payload: NoteCreate) -> Note:
    now = utc_now()
    with connection() as conn:
        cursor = conn.execute(
            "INSERT INTO notes(title, content, tags, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
            (payload.title, payload.content, json.dumps(payload.tags, ensure_ascii=False), now, now),
        )
        row = conn.execute("SELECT * FROM notes WHERE id = ?", (cursor.lastrowid,)).fetchone()
    result = _note(row)
    await manager.broadcast({"type": "note_created", "data": result.model_dump(mode="json")})
    return result


@router.get("/{note_id}", response_model=Note)
def get_note(note_id: int) -> Note:
    with connection() as conn:
        row = conn.execute("SELECT * FROM notes WHERE id = ?", (note_id,)).fetchone()
    if row is None:
        raise HTTPException(status_code=404, detail="笔记不存在")
    return _note(row)


@router.put("/{note_id}", response_model=Note)
async def update_note(note_id: int, payload: NoteUpdate) -> Note:
    changes = payload.model_dump(exclude_unset=True)
    with connection() as conn:
        if conn.execute("SELECT 1 FROM notes WHERE id = ?", (note_id,)).fetchone() is None:
            raise HTTPException(status_code=404, detail="笔记不存在")
        fields = []
        values = []
        for field, value in changes.items():
            if field == "tags":
                value = json.dumps(value, ensure_ascii=False)
            fields.append(f"{field} = ?")
            values.append(value)
        if fields:
            fields.append("updated_at = ?")
            values.extend([utc_now(), note_id])
            conn.execute(f"UPDATE notes SET {', '.join(fields)} WHERE id = ?", values)
        row = conn.execute("SELECT * FROM notes WHERE id = ?", (note_id,)).fetchone()
    result = _note(row)
    await manager.broadcast({"type": "note_updated", "data": result.model_dump(mode="json")})
    return result


@router.delete("/{note_id}", status_code=204)
async def delete_note(note_id: int) -> None:
    with connection() as conn:
        cursor = conn.execute("DELETE FROM notes WHERE id = ?", (note_id,))
    if cursor.rowcount == 0:
        raise HTTPException(status_code=404, detail="笔记不存在")
    await manager.broadcast({"type": "note_deleted", "data": {"id": note_id}})

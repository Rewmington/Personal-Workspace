from __future__ import annotations
import json
from fastapi import APIRouter, HTTPException, Query
from ..database import connection, json_load, row_dict, utc_now
from ..schemas import Snippet, SnippetCreate, SnippetUpdate
from ..websocket.manager import manager

router = APIRouter(prefix="/api/snippets", tags=["snippets"])

def _item(row) -> Snippet:
    data = row_dict(row); data["tags"] = json_load(data.get("tags"), [])
    return Snippet.model_validate(data)

@router.get("", response_model=list[Snippet])
def list_snippets(q: str | None = None, language: str | None = None, tag: str | None = None,
                  page: int = Query(1, ge=1), page_size: int = Query(100, ge=1, le=100)):
    clauses, values = [], []
    if q: clauses.append("(title LIKE ? OR code LIKE ? OR description LIKE ?)"); values += [f"%{q}%"] * 3
    if language: clauses.append("language = ?"); values.append(language)
    if tag: clauses.append("tags LIKE ?"); values.append(f'%"{tag}"%')
    where = (" WHERE " + " AND ".join(clauses)) if clauses else ""
    values += [(page - 1) * page_size, page_size]
    with connection() as conn:
        rows = conn.execute(f"SELECT * FROM snippets{where} ORDER BY updated_at DESC, id DESC LIMIT ? OFFSET ?", values).fetchall()
    return [_item(row) for row in rows]

@router.get("/meta")
def snippet_meta():
    with connection() as conn:
        rows = conn.execute("SELECT language, tags FROM snippets").fetchall()
    languages, tags = {}, {}
    for row in rows:
        languages[row["language"]] = languages.get(row["language"], 0) + 1
        for item in json_load(row["tags"], []): tags[item] = tags.get(item, 0) + 1
    return {"languages": languages, "tags": tags}

@router.post("", response_model=Snippet, status_code=201)
async def create_snippet(payload: SnippetCreate):
    now = utc_now()
    with connection() as conn:
        cur = conn.execute("INSERT INTO snippets(title,code,language,tags,description,created_at,updated_at) VALUES (?,?,?,?,?,?,?)",
                           (payload.title, payload.code, payload.language, json.dumps(payload.tags, ensure_ascii=False), payload.description, now, now))
        row = conn.execute("SELECT * FROM snippets WHERE id=?", (cur.lastrowid,)).fetchone()
    result = _item(row); await manager.broadcast({"type": "snippet_created", "data": result.model_dump(mode="json")}); return result

@router.get("/{snippet_id}", response_model=Snippet)
def get_snippet(snippet_id: int):
    with connection() as conn: row = conn.execute("SELECT * FROM snippets WHERE id=?", (snippet_id,)).fetchone()
    if row is None: raise HTTPException(404, "代码片段不存在")
    return _item(row)

@router.put("/{snippet_id}", response_model=Snippet)
async def update_snippet(snippet_id: int, payload: SnippetUpdate):
    changes = payload.model_dump(exclude_unset=True)
    with connection() as conn:
        if conn.execute("SELECT 1 FROM snippets WHERE id=?", (snippet_id,)).fetchone() is None: raise HTTPException(404, "代码片段不存在")
        fields, values = [], []
        for key, value in changes.items():
            if key == "tags": value = json.dumps(value, ensure_ascii=False)
            fields.append(f"{key}=?"); values.append(value)
        if fields:
            fields.append("updated_at=?"); values += [utc_now(), snippet_id]
            conn.execute(f"UPDATE snippets SET {','.join(fields)} WHERE id=?", values)
        row = conn.execute("SELECT * FROM snippets WHERE id=?", (snippet_id,)).fetchone()
    result = _item(row); await manager.broadcast({"type": "snippet_updated", "data": result.model_dump(mode="json")}); return result

@router.delete("/{snippet_id}", status_code=204)
async def delete_snippet(snippet_id: int):
    with connection() as conn: cur = conn.execute("DELETE FROM snippets WHERE id=?", (snippet_id,))
    if cur.rowcount == 0: raise HTTPException(404, "代码片段不存在")
    await manager.broadcast({"type": "snippet_deleted", "data": {"id": snippet_id}})

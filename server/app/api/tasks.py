from __future__ import annotations

from fastapi import APIRouter, HTTPException, Query

from ..database import connection, row_dict, utc_now
from ..schemas import Board, BoardCreate, Column, ColumnCreate, Task, TaskCreate, TaskUpdate


router = APIRouter(prefix="/api", tags=["tasks"])


def _task(row) -> Task:
    data = row_dict(row)
    return Task.model_validate(data)


@router.get("/boards", response_model=list[Board])
def list_boards() -> list[Board]:
    with connection() as conn:
        rows = conn.execute("SELECT * FROM boards ORDER BY position, id").fetchall()
    return [Board.model_validate(row_dict(row)) for row in rows]


@router.post("/boards", response_model=Board, status_code=201)
def create_board(payload: BoardCreate) -> Board:
    with connection() as conn:
        position = conn.execute("SELECT COALESCE(MAX(position), -1) + 1 FROM boards").fetchone()[0]
        cursor = conn.execute(
            "INSERT INTO boards(name, position, created_at) VALUES (?, ?, ?)",
            (payload.name, position, utc_now()),
        )
        row = conn.execute("SELECT * FROM boards WHERE id = ?", (cursor.lastrowid,)).fetchone()
    return Board.model_validate(row_dict(row))


@router.get("/boards/{board_id}/tasks", response_model=list[Column])
def get_board_tasks(board_id: int) -> list[Column]:
    with connection() as conn:
        if conn.execute("SELECT 1 FROM boards WHERE id = ?", (board_id,)).fetchone() is None:
            raise HTTPException(status_code=404, detail="看板不存在")
        columns = conn.execute("SELECT * FROM columns WHERE board_id = ? ORDER BY position, id", (board_id,)).fetchall()
        result = []
        for column in columns:
            tasks = conn.execute(
                "SELECT * FROM tasks WHERE column_id = ? ORDER BY position, id", (column["id"],)
            ).fetchall()
            item = row_dict(column)
            item["tasks"] = [_task(row) for row in tasks]
            result.append(Column.model_validate(item))
    return result


@router.post("/boards/{board_id}/columns", response_model=Column, status_code=201)
def create_column(board_id: int, payload: ColumnCreate) -> Column:
    with connection() as conn:
        if conn.execute("SELECT 1 FROM boards WHERE id = ?", (board_id,)).fetchone() is None:
            raise HTTPException(status_code=404, detail="看板不存在")
        position = payload.position
        if position == 0:
            position = conn.execute("SELECT COALESCE(MAX(position), -1) + 1 FROM columns WHERE board_id = ?", (board_id,)).fetchone()[0]
        cursor = conn.execute(
            "INSERT INTO columns(board_id, name, position) VALUES (?, ?, ?)",
            (board_id, payload.name, position),
        )
        row = conn.execute("SELECT * FROM columns WHERE id = ?", (cursor.lastrowid,)).fetchone()
    item = row_dict(row)
    item["tasks"] = []
    return Column.model_validate(item)


@router.get("/tasks", response_model=list[Task])
def list_tasks(column_id: int | None = Query(default=None)) -> list[Task]:
    with connection() as conn:
        if column_id is None:
            rows = conn.execute("SELECT * FROM tasks ORDER BY column_id, position, id").fetchall()
        else:
            rows = conn.execute("SELECT * FROM tasks WHERE column_id = ? ORDER BY position, id", (column_id,)).fetchall()
    return [_task(row) for row in rows]


@router.post("/tasks", response_model=Task, status_code=201)
def create_task(payload: TaskCreate) -> Task:
    now = utc_now()
    with connection() as conn:
        if conn.execute("SELECT 1 FROM columns WHERE id = ?", (payload.column_id,)).fetchone() is None:
            raise HTTPException(status_code=404, detail="任务列不存在")
        position = conn.execute("SELECT COALESCE(MAX(position), -1) + 1 FROM tasks WHERE column_id = ?", (payload.column_id,)).fetchone()[0]
        cursor = conn.execute(
            """INSERT INTO tasks(column_id,title,description,priority,due_date,position,created_at,updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
            (payload.column_id, payload.title, payload.description, payload.priority, payload.due_date.isoformat() if payload.due_date else None, position, now, now),
        )
        row = conn.execute("SELECT * FROM tasks WHERE id = ?", (cursor.lastrowid,)).fetchone()
    return _task(row)


@router.get("/tasks/{task_id}", response_model=Task)
def get_task(task_id: int) -> Task:
    with connection() as conn:
        row = conn.execute("SELECT * FROM tasks WHERE id = ?", (task_id,)).fetchone()
    if row is None:
        raise HTTPException(status_code=404, detail="任务不存在")
    return _task(row)


@router.put("/tasks/{task_id}", response_model=Task)
def update_task(task_id: int, payload: TaskUpdate) -> Task:
    changes = payload.model_dump(exclude_unset=True)
    if not changes:
        return get_task(task_id)
    with connection() as conn:
        current = conn.execute("SELECT * FROM tasks WHERE id = ?", (task_id,)).fetchone()
        if current is None:
            raise HTTPException(status_code=404, detail="任务不存在")
        if "column_id" in changes and conn.execute("SELECT 1 FROM columns WHERE id = ?", (changes["column_id"],)).fetchone() is None:
            raise HTTPException(status_code=404, detail="目标任务列不存在")
        fields = []
        values = []
        for field, value in changes.items():
            if field == "due_date":
                value = value.isoformat() if value else None
            fields.append(f"{field} = ?")
            values.append(value)
        fields.append("updated_at = ?")
        values.extend([utc_now(), task_id])
        conn.execute(f"UPDATE tasks SET {', '.join(fields)} WHERE id = ?", values)
        row = conn.execute("SELECT * FROM tasks WHERE id = ?", (task_id,)).fetchone()
    return _task(row)


@router.delete("/tasks/{task_id}", status_code=204)
def delete_task(task_id: int) -> None:
    with connection() as conn:
        cursor = conn.execute("DELETE FROM tasks WHERE id = ?", (task_id,))
    if cursor.rowcount == 0:
        raise HTTPException(status_code=404, detail="任务不存在")


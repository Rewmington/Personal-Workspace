from __future__ import annotations

import json
import shutil
import sqlite3
import tempfile
from datetime import datetime
from pathlib import Path
from typing import Any

from ..config import settings


BACKUP_FORMAT_VERSION = 1


def backup_directory() -> Path:
    directory = settings.database_path.parent / "backups"
    directory.mkdir(parents=True, exist_ok=True)
    return directory


def _timestamp() -> str:
    return datetime.now().strftime("%Y%m%d_%H%M%S_%f")


def _backup_path(suffix: str) -> Path:
    return backup_directory() / f"workstation_backup_{_timestamp()}{suffix}"


def _user_tables(conn: sqlite3.Connection) -> list[str]:
    rows = conn.execute(
        "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name"
    ).fetchall()
    return [row[0] for row in rows]


def _validate_sqlite(path: Path) -> None:
    with sqlite3.connect(path) as conn:
        result = conn.execute("PRAGMA integrity_check").fetchone()[0]
    if result != "ok":
        raise ValueError(f"备份文件完整性校验失败: {result}")


def create_sqlite_backup() -> Path:
    destination = _backup_path(".db")
    with sqlite3.connect(settings.database_path) as source, sqlite3.connect(destination) as target:
        source.backup(target)
    _validate_sqlite(destination)
    return destination


def export_sqlite_bytes() -> bytes:
    path = create_sqlite_backup()
    try:
        return path.read_bytes()
    finally:
        path.unlink(missing_ok=True)


def export_json_payload() -> dict[str, Any]:
    with sqlite3.connect(settings.database_path) as conn:
        conn.row_factory = sqlite3.Row
        tables = {
            table: [dict(row) for row in conn.execute(f'SELECT * FROM "{table}"').fetchall()]
            for table in _user_tables(conn)
        }
    return {
        "format": "personal-workstation-backup",
        "version": BACKUP_FORMAT_VERSION,
        "exported_at": datetime.now().astimezone().isoformat(),
        "tables": tables,
    }


def backup_history() -> list[dict[str, Any]]:
    return [
        {
            "filename": path.name,
            "size": path.stat().st_size,
            "created_at": datetime.fromtimestamp(path.stat().st_mtime).astimezone().isoformat(),
            "format": "sqlite" if path.suffix == ".db" else "json",
        }
        for path in sorted(backup_directory().glob("workstation_backup_*"), key=lambda item: item.stat().st_mtime, reverse=True)
        if path.is_file()
    ]


def _restore_sqlite(source: Path) -> None:
    _validate_sqlite(source)
    temporary = settings.database_path.with_suffix(".restore.tmp")
    shutil.copyfile(source, temporary)
    _validate_sqlite(temporary)
    temporary.replace(settings.database_path)


def _restore_json(payload: dict[str, Any], mode: str) -> dict[str, int]:
    if payload.get("format") != "personal-workstation-backup" or not isinstance(payload.get("tables"), dict):
        raise ValueError("不是有效的个人工作台 JSON 备份")
    tables = payload["tables"]
    imported: dict[str, int] = {}
    with sqlite3.connect(settings.database_path) as conn:
        conn.row_factory = sqlite3.Row
        current_tables = set(_user_tables(conn))
        selected = [(name, rows) for name, rows in tables.items() if name in current_tables and isinstance(rows, list)]
        conn.execute("PRAGMA foreign_keys = OFF")
        if mode == "replace":
            for table in reversed(_user_tables(conn)):
                conn.execute(f'DELETE FROM "{table}"')
        for table, rows in selected:
            count = 0
            for row in rows:
                if not isinstance(row, dict) or not row:
                    continue
                columns = list(row)
                marks = ", ".join("?" for _ in columns)
                statement = "INSERT OR REPLACE" if mode == "merge" else "INSERT"
                conn.execute(
                    f'{statement} INTO "{table}" ({", ".join(f"\"{column}\"" for column in columns)}) VALUES ({marks})',
                    [row[column] for column in columns],
                )
                count += 1
            imported[table] = count
        conn.execute("PRAGMA foreign_keys = ON")
    return imported


def restore_backup(filename: str, content: bytes, mode: str) -> dict[str, Any]:
    if mode not in {"replace", "merge"}:
        raise ValueError("恢复模式必须为 replace 或 merge")
    if not content:
        raise ValueError("备份文件为空")
    if len(content) > 100 * 1024 * 1024:
        raise ValueError("备份文件不能超过 100MB")

    safety_backup = create_sqlite_backup()
    suffix = Path(filename).suffix.lower()
    try:
        if suffix == ".db":
            if mode != "replace":
                raise ValueError("SQLite 备份仅支持替换恢复")
            with tempfile.NamedTemporaryFile(suffix=".db", delete=False) as handle:
                handle.write(content)
                source = Path(handle.name)
            try:
                _restore_sqlite(source)
                result: dict[str, Any] = {"mode": mode, "format": "sqlite"}
            finally:
                source.unlink(missing_ok=True)
        else:
            payload = json.loads(content.decode("utf-8"))
            result = {"mode": mode, "format": "json", "tables": _restore_json(payload, mode)}
    except Exception:
        _restore_sqlite(safety_backup)
        raise
    return {"ok": True, "safety_backup": safety_backup.name, **result}

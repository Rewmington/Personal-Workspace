from __future__ import annotations

import json
import sqlite3
from contextlib import contextmanager
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterator

from .config import settings


SCHEMA = """
CREATE TABLE IF NOT EXISTS boards (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL,
  position INTEGER NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS columns (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  board_id INTEGER NOT NULL REFERENCES boards(id) ON DELETE CASCADE,
  name TEXT NOT NULL,
  position INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS tasks (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  column_id INTEGER NOT NULL REFERENCES columns(id) ON DELETE CASCADE,
  title TEXT NOT NULL,
  description TEXT NOT NULL DEFAULT '',
  priority TEXT NOT NULL DEFAULT 'medium',
  due_date TEXT,
  position INTEGER NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS notes (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  title TEXT NOT NULL,
  content TEXT NOT NULL DEFAULT '',
  tags TEXT NOT NULL DEFAULT '[]',
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS github_repos (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL UNIQUE,
  owner TEXT NOT NULL,
  html_url TEXT NOT NULL DEFAULT '',
  description TEXT NOT NULL DEFAULT '',
  language TEXT NOT NULL DEFAULT '',
  stars INTEGER NOT NULL DEFAULT 0,
  open_issues INTEGER NOT NULL DEFAULT 0,
  pushed_at TEXT,
  last_fetch_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS github_commits (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  repo_id INTEGER NOT NULL REFERENCES github_repos(id) ON DELETE CASCADE,
  sha TEXT NOT NULL UNIQUE,
  message TEXT NOT NULL,
  author TEXT NOT NULL DEFAULT '',
  pushed_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS github_events (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  repo_id INTEGER REFERENCES github_repos(id) ON DELETE SET NULL,
  type TEXT NOT NULL,
  actor TEXT NOT NULL DEFAULT '',
  payload TEXT NOT NULL DEFAULT '{}',
  created_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_columns_board ON columns(board_id, position);
CREATE INDEX IF NOT EXISTS idx_tasks_column ON tasks(column_id, position);
CREATE INDEX IF NOT EXISTS idx_commits_pushed ON github_commits(pushed_at);
CREATE INDEX IF NOT EXISTS idx_events_created ON github_events(created_at);
CREATE TABLE IF NOT EXISTS snippets (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  title TEXT NOT NULL,
  code TEXT NOT NULL,
  language TEXT NOT NULL DEFAULT 'plain',
  tags TEXT NOT NULL DEFAULT '[]',
  description TEXT NOT NULL DEFAULT '',
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_snippets_language ON snippets(language);
CREATE INDEX IF NOT EXISTS idx_snippets_updated ON snippets(updated_at DESC);
CREATE TABLE IF NOT EXISTS git_repos_local (
  id INTEGER PRIMARY KEY AUTOINCREMENT, path TEXT NOT NULL UNIQUE, name TEXT NOT NULL,
  branch TEXT NOT NULL DEFAULT '', ahead INTEGER NOT NULL DEFAULT 0, behind INTEGER NOT NULL DEFAULT 0,
  changed INTEGER NOT NULL DEFAULT 0, staged INTEGER NOT NULL DEFAULT 0, untracked INTEGER NOT NULL DEFAULT 0,
  last_commit TEXT NOT NULL DEFAULT '', last_commit_at TEXT NOT NULL DEFAULT '', last_scan TEXT NOT NULL DEFAULT '', status TEXT NOT NULL DEFAULT 'ok'
);
CREATE TABLE IF NOT EXISTS focus_sessions (
  id INTEGER PRIMARY KEY AUTOINCREMENT, task_id INTEGER, task_title TEXT NOT NULL DEFAULT '',
  duration INTEGER NOT NULL, actual INTEGER NOT NULL DEFAULT 0, status TEXT NOT NULL DEFAULT 'running',
  started_at TEXT NOT NULL, ended_at TEXT NOT NULL DEFAULT ''
);
CREATE TABLE IF NOT EXISTS dev_logs (
  id INTEGER PRIMARY KEY AUTOINCREMENT, date TEXT NOT NULL UNIQUE, content TEXT NOT NULL DEFAULT '',
  mood TEXT NOT NULL DEFAULT '', tags TEXT NOT NULL DEFAULT '[]', commits TEXT NOT NULL DEFAULT '[]',
  created_at TEXT NOT NULL, updated_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_dev_logs_date ON dev_logs(date);
CREATE TABLE IF NOT EXISTS http_requests (
  id              INTEGER PRIMARY KEY AUTOINCREMENT,
  method          TEXT NOT NULL DEFAULT 'GET',
  url             TEXT NOT NULL,
  headers         TEXT NOT NULL DEFAULT '[]',
  body            TEXT NOT NULL DEFAULT '',
  content_type    TEXT NOT NULL DEFAULT 'json',
  response_status INTEGER,
  response_time_ms INTEGER,
  response_headers TEXT NOT NULL DEFAULT '{}',
  response_body   TEXT NOT NULL DEFAULT '',
  is_favorite     INTEGER NOT NULL DEFAULT 0,
  created_at      TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_http_requests_created ON http_requests(created_at DESC);
"""


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def get_connection() -> sqlite3.Connection:
    settings.database_path.parent.mkdir(parents=True, exist_ok=True)
    connection = sqlite3.connect(settings.database_path, check_same_thread=False)
    connection.row_factory = sqlite3.Row
    connection.execute("PRAGMA foreign_keys = ON")
    return connection


@contextmanager
def connection() -> Iterator[sqlite3.Connection]:
    conn = get_connection()
    try:
        yield conn
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


def init_db() -> None:
    with connection() as conn:
        conn.executescript(SCHEMA)
        if conn.execute("SELECT COUNT(*) FROM boards").fetchone()[0] == 0:
            now = utc_now()
            board_id = conn.execute(
                "INSERT INTO boards(name, position, created_at) VALUES (?, ?, ?)",
                ("个人工作台 Sprint", 0, now),
            ).lastrowid
            columns = [("待办", 0), ("进行中", 1), ("已完成", 2), ("已归档", 3)]
            column_ids = []
            for name, position in columns:
                column_ids.append(conn.execute(
                    "INSERT INTO columns(board_id, name, position) VALUES (?, ?, ?)",
                    (board_id, name, position),
                ).lastrowid)
            demo_tasks = [
                (column_ids[0], "搭建服务端 API", "完成 FastAPI 和 SQLite 基础骨架", "high"),
                (column_ids[0], "整理项目笔记", "补齐 MVP 的数据结构说明", "medium"),
                (column_ids[1], "实现 GitHub 同步", "接入缓存和手动刷新接口", "medium"),
                (column_ids[2], "设计移动端原型", "完成 Compose 页面导航草图", "low"),
            ]
            for position, (column_id, title, description, priority) in enumerate(demo_tasks):
                conn.execute(
                    """INSERT INTO tasks(column_id,title,description,priority,position,created_at,updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)""",
                    (column_id, title, description, priority, position, now, now),
                )
            conn.execute(
                "INSERT INTO notes(title, content, tags, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                ("欢迎使用个人工作台", "这是你的本地知识与任务空间。", json.dumps(["入门", "MVP"], ensure_ascii=False), now, now),
            )


def row_dict(row: sqlite3.Row | None) -> dict[str, Any] | None:
    return dict(row) if row is not None else None


def json_load(value: str, fallback: Any) -> Any:
    try:
        return json.loads(value)
    except (TypeError, json.JSONDecodeError):
        return fallback

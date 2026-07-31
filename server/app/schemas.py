from __future__ import annotations

from datetime import date
from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field


Priority = Literal["low", "medium", "high"]


class BoardCreate(BaseModel):
    name: str = Field(min_length=1, max_length=100)


class Board(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: int
    name: str
    position: int
    created_at: str


class ColumnCreate(BaseModel):
    name: str = Field(min_length=1, max_length=100)
    position: int = 0


class Column(BaseModel):
    id: int
    board_id: int
    name: str
    position: int
    tasks: list["Task"] = []


class TaskCreate(BaseModel):
    column_id: int
    title: str = Field(min_length=1, max_length=200)
    description: str = ""
    priority: Priority = "medium"
    due_date: date | None = None


class TaskUpdate(BaseModel):
    column_id: int | None = None
    title: str | None = Field(default=None, min_length=1, max_length=200)
    description: str | None = None
    priority: Priority | None = None
    due_date: date | None = None
    position: int | None = Field(default=None, ge=0)


class Task(BaseModel):
    id: int
    column_id: int
    title: str
    description: str
    priority: Priority
    due_date: date | None = None
    position: int
    created_at: str
    updated_at: str


class NoteCreate(BaseModel):
    title: str = Field(min_length=1, max_length=200)
    content: str = ""
    tags: list[str] = []


class NoteUpdate(BaseModel):
    title: str | None = Field(default=None, min_length=1, max_length=200)
    content: str | None = None
    tags: list[str] | None = None


class Note(BaseModel):
    id: int
    title: str
    content: str
    tags: list[str]
    created_at: str
    updated_at: str


class GithubSettings(BaseModel):
    username: str = Field(default="", max_length=100)
    token: str | None = Field(default=None, max_length=500)


class GithubSettingsStatus(BaseModel):
    username: str = ""
    token_configured: bool = False


class Profile(BaseModel):
    display_name: str = "Liu Developer"
    github_username: str = ""


class ProfileUpdate(BaseModel):
    display_name: str = Field(min_length=1, max_length=100)
    github_username: str = Field(default="", max_length=100)

class GithubRefreshResponse(BaseModel):
    ok: bool
    fetched_repositories: int
    fetched_commits: int
    fetched_events: int
    message: str


class GithubRepo(BaseModel):
    id: int
    name: str
    owner: str
    html_url: str
    description: str
    language: str
    stars: int
    open_issues: int
    pushed_at: str | None
    last_fetch_at: str


class GithubActivity(BaseModel):
    id: int
    type: str
    actor: str
    payload: dict[str, Any]
    created_at: str

class SnippetCreate(BaseModel):
    title: str = Field(min_length=1, max_length=200)
    code: str = Field(min_length=1, max_length=5000)
    language: str = Field(default="plain", max_length=40)
    tags: list[str] = []
    description: str = Field(default="", max_length=1000)

class SnippetUpdate(BaseModel):
    title: str | None = Field(default=None, min_length=1, max_length=200)
    code: str | None = Field(default=None, max_length=5000)
    language: str | None = Field(default=None, max_length=40)
    tags: list[str] | None = None
    description: str | None = Field(default=None, max_length=1000)

class Snippet(SnippetCreate):
    id: int
    created_at: str
    updated_at: str

class FocusStart(BaseModel):
    task_id: int | None = None
    task_title: str = ""
    duration: int = Field(default=1500, ge=60, le=86400)

class FocusStop(BaseModel):
    actual: int | None = Field(default=None, ge=0)

class DevLogUpdate(BaseModel):
    content: str = ""
    mood: str = ""
    tags: list[str] = []
    commits: list[str] = []

class DevLog(BaseModel):
    id: int
    date: str
    content: str
    mood: str
    tags: list[str]
    commits: list[str]
    created_at: str
    updated_at: str



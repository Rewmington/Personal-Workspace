from __future__ import annotations

import json
import os
import tempfile
from dataclasses import dataclass
from pathlib import Path
from typing import Any


ROOT_DIR = Path(__file__).resolve().parents[1]
DATA_DIR = Path(os.getenv("WORKSTATION_DATA_DIR", str(ROOT_DIR / "data")))
LOCAL_CONFIG_PATH = DATA_DIR / "settings.json"


def _read_local_config() -> dict[str, Any]:
    try:
        value = json.loads(LOCAL_CONFIG_PATH.read_text(encoding="utf-8"))
        return value if isinstance(value, dict) else {}
    except (FileNotFoundError, OSError, json.JSONDecodeError):
        return {}


@dataclass
class Settings:
    host: str
    port: int
    database_path: Path
    github_token: str | None
    github_username: str | None
    github_fetch_timeout: float
    display_name: str

    def save_github(self, username: str | None, token: str | None) -> None:
        self.github_username = username or None
        if token is not None:
            self.github_token = token or None

        values = _read_local_config()
        values["github_username"] = self.github_username or ""
        if self.github_token:
            values["github_token"] = self.github_token
        else:
            values.pop("github_token", None)

        LOCAL_CONFIG_PATH.parent.mkdir(parents=True, exist_ok=True)
        fd, temp_name = tempfile.mkstemp(prefix="settings-", suffix=".json", dir=LOCAL_CONFIG_PATH.parent)
        try:
            with os.fdopen(fd, "w", encoding="utf-8") as handle:
                json.dump(values, handle, ensure_ascii=False, indent=2)
                handle.write("\n")
            os.replace(temp_name, LOCAL_CONFIG_PATH)
        finally:
            try:
                Path(temp_name).unlink(missing_ok=True)
            except OSError:
                pass

    def save_profile(self, display_name: str) -> None:
        self.display_name = display_name.strip()
        values = _read_local_config()
        values["display_name"] = self.display_name
        fd, temp_name = tempfile.mkstemp(prefix="settings-", suffix=".json", dir=LOCAL_CONFIG_PATH.parent)
        try:
            with os.fdopen(fd, "w", encoding="utf-8") as handle:
                json.dump(values, handle, ensure_ascii=False, indent=2)
                handle.write("\n")
            os.replace(temp_name, LOCAL_CONFIG_PATH)
        finally:
            try:
                Path(temp_name).unlink(missing_ok=True)
            except OSError:
                pass


_local = _read_local_config()
settings = Settings(
    host=os.getenv("WORKSTATION_HOST", "0.0.0.0"),
    port=int(os.getenv("WORKSTATION_PORT", "8080")),
    database_path=Path(os.getenv("WORKSTATION_DB", str(DATA_DIR / "workstation.db"))),
    github_token=os.getenv("GITHUB_TOKEN") or _local.get("github_token") or None,
    github_username=os.getenv("GITHUB_USERNAME") or _local.get("github_username") or None,
    github_fetch_timeout=float(os.getenv("GITHUB_FETCH_TIMEOUT", "15")),
    display_name=str(os.getenv("WORKSTATION_DISPLAY_NAME") or _local.get("display_name") or "Liu Developer"),
)


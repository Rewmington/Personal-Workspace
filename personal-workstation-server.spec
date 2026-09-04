# -*- mode: python ; coding: utf-8 -*-
from __future__ import annotations

import sys
from pathlib import Path

_HERE = Path(SPECPATH)  # PyInstaller provides SPECPATH as the directory containing the spec file
_SERVER = _HERE / "server"

a = Analysis(
    [str(_SERVER / "run_server.py")],
    pathex=[str(_SERVER)],
    binaries=[],
    datas=[
        (str(_SERVER / "app"), "app"),
        (str(_HERE / "web"), "web"),
    ],
    hiddenimports=[
        "app.api.backup",
        "app.api.connect",
        "app.api.dashboard",
        "app.api.github",
        "app.api.http_client",
        "app.api.notes",
        "app.api.profile",
        "app.api.tasks",
        "app.api.snippets",
        "app.api.git",
        "app.api.focus",
        "app.api.logs",
        "app.config",
        "app.database",
        "app.mdns_broadcaster",
        "app.udp_discovery",
        "app.services.github",
        "app.websocket.handler",
    ],
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[],
    noarchive=False,
    optimize=0,
)

pyz = PYZ(a.pure)

exe = EXE(
    pyz,
    a.scripts,
    a.binaries,
    a.datas,
    [],
    name="personal-workstation-server",
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    upx_exclude=[],
    runtime_tmpdir=None,
    console=True,
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
    icon=None,
)

# -*- mode: python ; coding: utf-8 -*-
"""PyInstaller build spec (Faz 10 — plan madde 23-25'te Nuitka tercih
edilmişti, ancak bu makinede C derleyicisi kurulu olmadığı için
kullanıcı onayıyla PyInstaller'a geçildi; PyInstaller salt Python,
derleyici gerektirmiyor).

Derlemek için:
    .venv\\Scripts\\pyinstaller.exe SmartLights.spec
"""

from pathlib import Path

import PySide6

block_cipher = None

pyside6_dir = Path(PySide6.__file__).parent
qml_dir = pyside6_dir / "qml"

a = Analysis(
    ["main.py"],
    pathex=[],
    binaries=[],
    datas=[
        ("ui", "ui"),
        (str(qml_dir), "PySide6/qml"),
    ],
    hiddenimports=[
        "PySide6.QtQml",
        "PySide6.QtQuick",
        "PySide6.QtQuickControls2",
    ],
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[],
    noarchive=False,
    optimize=0,
)

pyz = PYZ(a.pure, cipher=block_cipher)

exe = EXE(
    pyz,
    a.scripts,
    a.binaries,
    a.datas,
    [],
    name="SmartLights",
    version="version_info.txt",
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=False,
    upx_exclude=[],
    runtime_tmpdir=None,
    console=False,
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
)

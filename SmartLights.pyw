"""SmartLights'i kaynak koddan, konsol penceresi olmadan başlatır.

Dosya çift tıklanarak sistem Python'u ile açılırsa proje sanal ortamındaki
pythonw.exe'ye geçer. Böylece uygulama her zaman doğru bağımlılıklarla çalışır.
"""

from __future__ import annotations

import os
import subprocess
import sys
from pathlib import Path


PROJECT_DIR = Path(__file__).resolve().parent
VENV_PYTHONW = PROJECT_DIR / ".venv" / "Scripts" / "pythonw.exe"


def _running_from_project_venv() -> bool:
    return Path(sys.executable).resolve() == VENV_PYTHONW.resolve()


def _restart_with_project_venv() -> None:
    if not VENV_PYTHONW.is_file():
        raise RuntimeError(
            "SmartLights sanal ortamı bulunamadı. Önce README'deki kaynak "
            "kurulum adımlarını uygulayın."
        )

    subprocess.Popen(  # noqa: S603 - hedef, projedeki sabit Python yorumlayıcısıdır
        [str(VENV_PYTHONW), str(Path(__file__).resolve()), *sys.argv[1:]],
        cwd=str(PROJECT_DIR),
        creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0),
        close_fds=True,
    )


if __name__ == "__main__":
    os.chdir(PROJECT_DIR)

    if not _running_from_project_venv():
        try:
            _restart_with_project_venv()
        except Exception as exc:  # Başlatma hatası GUI üzerinden gösterilsin.
            import ctypes

            ctypes.windll.user32.MessageBoxW(
                None,
                str(exc),
                "SmartLights başlatılamadı",
                0x10,
            )
            raise SystemExit(1) from exc
        raise SystemExit(0)

    from main import main

    raise SystemExit(main())

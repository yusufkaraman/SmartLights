"""Windows açılışında otomatik başlatma (plan madde 20).

`HKEY_CURRENT_USER\\...\\Run` altına bir değer yazar/siler — yönetici
izni gerektirmez, sadece mevcut kullanıcı için geçerlidir.
"""

from __future__ import annotations

import sys
import winreg
from pathlib import Path

_RUN_KEY = r"Software\Microsoft\Windows\CurrentVersion\Run"
_VALUE_NAME = "SmartLights"


def _is_frozen() -> bool:
    """Nuitka ile derlenmiş SmartLights.exe olarak mı, yoksa `python main.py`
    ile mi çalıştığımızı ayırt eder. Nuitka `sys.frozen` set etmiyor
    (PyInstaller'ın aksine); bunun yerine çalıştırılan yorumlayıcının adına
    bakıyoruz — derlenmiş .exe'de sys.executable doğrudan SmartLights.exe'dir.
    """
    return not Path(sys.executable).stem.lower().startswith("python")


def _launch_command(start_minimized: bool) -> str:
    if _is_frozen():
        command = f'"{sys.executable}"'
    else:
        main_path = Path(__file__).resolve().parent.parent / "main.py"
        command = f'"{sys.executable}" "{main_path}"'
    if start_minimized:
        command += " --minimized"
    return command


def is_autostart_enabled() -> bool:
    try:
        with winreg.OpenKey(winreg.HKEY_CURRENT_USER, _RUN_KEY, 0, winreg.KEY_READ) as key:
            winreg.QueryValueEx(key, _VALUE_NAME)
            return True
    except FileNotFoundError:
        return False
    except OSError:
        return False


def set_autostart_enabled(enabled: bool, start_minimized: bool = False) -> None:
    """Otomatik başlatmayı açar/kapatır.

    `start_minimized=True` ise başlatma komutuna `--minimized` eklenir;
    main.py bunu görünce pencereyi göstermeden tray'de başlar (plan madde
    20 — manuel açılışta pencere yine normal şekilde görünür, bu bayrak
    sadece Windows'un kendi başlattığı örnek için geçerlidir).
    """
    try:
        with winreg.OpenKey(winreg.HKEY_CURRENT_USER, _RUN_KEY, 0, winreg.KEY_SET_VALUE) as key:
            if enabled:
                winreg.SetValueEx(
                    key, _VALUE_NAME, 0, winreg.REG_SZ, _launch_command(start_minimized)
                )
            else:
                try:
                    winreg.DeleteValue(key, _VALUE_NAME)
                except FileNotFoundError:
                    pass
    except OSError:
        pass  # Registry'ye yazılamazsa (nadir) sessizce başarısız ol, uygulama çökmesin.

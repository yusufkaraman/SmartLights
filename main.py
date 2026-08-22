"""SmartLights - gerçek uygulamanın giriş noktası (plan madde 27, 28).

CLI proof-of-concept (Faz 1-2) artık cli.py'de duruyor, adapter/model
katmanını UI olmadan test etmek için saklanıyor.

Faz 7: sistem tray'i için QGuiApplication yerine QApplication (QtWidgets)
kullanıyoruz — QSystemTrayIcon bunu gerektiriyor; QML/QQmlApplicationEngine
QApplication ile de sorunsuz çalışır (QApplication zaten bir QGuiApplication).
"""

from __future__ import annotations

import logging
import sys
from pathlib import Path

from PySide6.QtQml import QQmlApplicationEngine
from PySide6.QtQuickControls2 import QQuickStyle
from PySide6.QtWidgets import QApplication

from core.app_settings import AppSettingsController
from core.async_worker import AsyncWorker
from core.device_manager import DeviceListModel
from core.tray import create_tray_icon

logging.basicConfig(level=logging.WARNING, format="%(levelname)s: %(message)s")

# PyInstaller onefile derlemesinde (Faz 10) uygulama her açılışta kendini
# `sys._MEIPASS` altındaki geçici bir klasöre açar; `ui/` klasörü de oraya
# data olarak gömülür (bkz. SmartLights.spec). Geliştirme ortamında ise
# normal dosya yolu kullanılır.
if getattr(sys, "frozen", False):
    BASE_DIR = Path(sys._MEIPASS)  # type: ignore[attr-defined]
else:
    BASE_DIR = Path(__file__).parent

UI_DIR = BASE_DIR / "ui"


def main() -> int:
    # Windows 11 görünümüne yakın resmi Qt Quick Controls stili (plan madde 9-10).
    QQuickStyle.setStyle("FluentWinUI3")

    app = QApplication(sys.argv)
    app.setOrganizationName("SmartLights")
    app.setApplicationName("SmartLights")
    # Pencere kapatılınca uygulama çıkmasın, tray'de kalsın (plan madde 19).
    # Main.qml'deki onClosing handler'ı kapatma yerine hide() çağırıyor;
    # bu satır olmadan Qt son pencere kapanınca otomatik quit() çağırırdı.
    app.setQuitOnLastWindowClosed(False)

    worker = AsyncWorker()
    device_model = DeviceListModel(worker)
    app_settings = AppSettingsController()

    # Windows başlangıcında `--minimized` ile çağrıldıysak pencere hiç
    # görünmeden tray'de başlar (plan madde 20). Manuel açılışta bu bayrak
    # yok, pencere normal şekilde görünür.
    start_minimized = "--minimized" in sys.argv

    engine = QQmlApplicationEngine()
    engine.addImportPath(str(UI_DIR))
    engine.rootContext().setContextProperty("deviceModel", device_model)
    engine.rootContext().setContextProperty("appSettings", app_settings)
    engine.rootContext().setContextProperty("startMinimized", start_minimized)
    engine.load(UI_DIR / "Main.qml")

    if not engine.rootObjects():
        logging.error("QML yüklenemedi, çıkılıyor.")
        return -1

    window = engine.rootObjects()[0]
    tray = create_tray_icon(app, window, device_model)  # noqa: F841 - referans canlı tutulmalı

    exit_code = app.exec()
    worker.stop()
    return exit_code


if __name__ == "__main__":
    sys.exit(main())

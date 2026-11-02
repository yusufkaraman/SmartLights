"""Sistem tray simgesi ve menüsü (plan madde 19).

Ana pencere kapatılınca uygulama tamamen çıkmaz; tray'e küçülür. Buradan
tüm ışıkları aç/kapat, sahneleri uygula, pencereyi göster veya uygulamadan
tamamen çıkılabilir.
"""

from __future__ import annotations

import logging

from PySide6.QtCore import QObject
from PySide6.QtGui import QColor, QIcon, QPainter, QPixmap
from PySide6.QtWidgets import QApplication, QMenu, QSystemTrayIcon

logger = logging.getLogger(__name__)


def _build_icon() -> QIcon:
    # assets/SmartLights.ico henüz yok (plan madde 27); basit bir ampul
    # simgesi çizip kullanıyoruz, ileride gerçek .ico ile değiştirilebilir.
    pixmap = QPixmap(64, 64)
    pixmap.fill(QColor(0, 0, 0, 0))
    painter = QPainter(pixmap)
    painter.setRenderHint(QPainter.Antialiasing)
    painter.setBrush(QColor("#ffd43b"))
    painter.setPen(QColor("#f5a623"))
    painter.drawEllipse(8, 4, 48, 48)
    painter.end()
    return QIcon(pixmap)


class TrayIcon(QObject):
    def __init__(
        self,
        app: QApplication,
        window: object,
        device_model: object,
        app_settings: object,
        parent: QObject | None = None,
    ) -> None:
        super().__init__(parent)
        self._app = app
        self._window = window
        self._device_model = device_model
        self._app_settings = app_settings

        self._tray = QSystemTrayIcon(_build_icon(), self)
        self._tray.setToolTip("SmartLights")

        self._menu = QMenu()
        self._menu.aboutToShow.connect(self._rebuild_menu)
        self._tray.setContextMenu(self._menu)
        self._tray.activated.connect(self._on_activated)
        self._tray.show()

    def _rebuild_menu(self) -> None:
        self._menu.clear()

        self._menu.addAction("Tümünü Aç", self._device_model.turnOnAll)
        self._menu.addAction("Tümünü Kapat", self._device_model.turnOffAll)

        scenes = self._device_model.scenes
        if scenes:
            self._menu.addSeparator()
            for scene in scenes:
                scene_id = scene["id"]
                label = f"{scene['icon']} {scene['name']}"
                self._menu.addAction(
                    label, lambda checked=False, sid=scene_id: self._device_model.applyScene(sid)
                )

        self._menu.addSeparator()
        self._menu.addAction("Mini paneli göster", self._app_settings.showMiniWidget)
        self._menu.addAction("Uygulamayı Aç", self._show_window)
        self._menu.addAction("Çıkış", self._app.quit)

    def _show_window(self) -> None:
        self._window.show()
        self._window.raise_()
        self._window.requestActivate()

    def _on_activated(self, reason: QSystemTrayIcon.ActivationReason) -> None:
        if reason in (QSystemTrayIcon.Trigger, QSystemTrayIcon.DoubleClick):
            if self._window.isVisible():
                self._window.hide()
            else:
                self._show_window()


def create_tray_icon(
    app: QApplication,
    window: object,
    device_model: object,
    app_settings: object,
) -> TrayIcon | None:
    """Tray simgesini oluşturur; sistem tray'i yoksa None döner, çökmez."""
    if not QSystemTrayIcon.isSystemTrayAvailable():
        logger.warning("Sistem tray'i bu ortamda mevcut değil, tray özelliği atlandı.")
        return None
    return TrayIcon(app, window, device_model, app_settings)

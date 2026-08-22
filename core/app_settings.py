"""Windows başlangıcı ayarları için QML köprüsü (plan madde 20).

Tema zaten QML tarafında `Settings` (QtCore, bkz. ui/theme/Theme.qml) ile
tutuluyor. Bu sınıf sadece native Windows API (registry) gerektiren
otomatik başlatma ayarı için — QML'de doğrudan winreg çağrılamaz.
"""

from __future__ import annotations

from PySide6.QtCore import Property, QObject, Qt, QSettings, Signal, Slot
from PySide6.QtGui import QGuiApplication

from core import autostart

_START_MINIMIZED_KEY = "general/startMinimized"


class AppSettingsController(QObject):
    autostartChanged = Signal()
    startMinimizedChanged = Signal()

    def __init__(self, parent: QObject | None = None) -> None:
        super().__init__(parent)
        self._settings = QSettings()

    def _get_autostart(self) -> bool:
        return autostart.is_autostart_enabled()

    def _set_autostart(self, value: bool) -> None:
        autostart.set_autostart_enabled(bool(value), start_minimized=self._get_start_minimized())
        self.autostartChanged.emit()

    autostartEnabled = Property(bool, _get_autostart, _set_autostart, notify=autostartChanged)

    def _get_start_minimized(self) -> bool:
        return self._settings.value(_START_MINIMIZED_KEY, False, type=bool)

    def _set_start_minimized(self, value: bool) -> None:
        self._settings.setValue(_START_MINIMIZED_KEY, bool(value))
        if self._get_autostart():
            # Otomatik başlatma zaten açıksa, kayıtlı komutu güncelle.
            autostart.set_autostart_enabled(True, start_minimized=bool(value))
        self.startMinimizedChanged.emit()

    startMinimized = Property(
        bool, _get_start_minimized, _set_start_minimized, notify=startMinimizedChanged
    )

    @Slot(str)
    def applyThemeMode(self, mode: str) -> None:  # noqa: N802 - QML slot adı
        """Tema modunu uygular (plan madde 21).

        `QStyleHints.unsetColorScheme()`, QML JS'ten çağrılabilir bir
        fonksiyon olarak dışa açılmıyor (metaobject'te sadece property
        "reset" fonksiyonu olarak kayıtlı, JS metodu değil) — bu yüzden bu
        işlem Python tarafında yapılıyor; Python C++ binding'i tüm
        metodlara erişebiliyor.
        """
        hints = QGuiApplication.styleHints()
        if mode == "dark":
            hints.setColorScheme(Qt.ColorScheme.Dark)
        elif mode == "light":
            hints.setColorScheme(Qt.ColorScheme.Light)
        else:
            hints.unsetColorScheme()

"""Windows başlangıcı ayarları için QML köprüsü (plan madde 20).

Tema zaten QML tarafında `Settings` (QtCore, bkz. ui/theme/Theme.qml) ile
tutuluyor. Bu sınıf sadece native Windows API (registry) gerektiren
otomatik başlatma ayarı için — QML'de doğrudan winreg çağrılamaz.
"""

from __future__ import annotations

import json

from PySide6.QtCore import Property, QObject, Qt, QSettings, Signal, Slot
from PySide6.QtGui import QGuiApplication

from core import autostart

_START_MINIMIZED_KEY = "general/startMinimized"
_MINI_WIDGET_VISIBLE_KEY = "miniWidget/visible"
_MINI_WIDGET_ALWAYS_ON_TOP_KEY = "miniWidget/alwaysOnTop"
_MINI_WIDGET_DEVICE_IDS_KEY = "miniWidget/deviceIds"


class AppSettingsController(QObject):
    autostartChanged = Signal()
    startMinimizedChanged = Signal()
    miniWidgetVisibleChanged = Signal()
    miniWidgetAlwaysOnTopChanged = Signal()
    widgetDeviceIdsChanged = Signal()

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

    def _get_mini_widget_visible(self) -> bool:
        return self._settings.value(_MINI_WIDGET_VISIBLE_KEY, False, type=bool)

    def _set_mini_widget_visible(self, value: bool) -> None:
        value = bool(value)
        if value == self._get_mini_widget_visible():
            return
        self._settings.setValue(_MINI_WIDGET_VISIBLE_KEY, value)
        self.miniWidgetVisibleChanged.emit()

    miniWidgetVisible = Property(  # noqa: N815 - QML property adı
        bool,
        _get_mini_widget_visible,
        _set_mini_widget_visible,
        notify=miniWidgetVisibleChanged,
    )

    def _get_mini_widget_always_on_top(self) -> bool:
        return self._settings.value(_MINI_WIDGET_ALWAYS_ON_TOP_KEY, True, type=bool)

    def _set_mini_widget_always_on_top(self, value: bool) -> None:
        value = bool(value)
        if value == self._get_mini_widget_always_on_top():
            return
        self._settings.setValue(_MINI_WIDGET_ALWAYS_ON_TOP_KEY, value)
        self.miniWidgetAlwaysOnTopChanged.emit()

    miniWidgetAlwaysOnTop = Property(  # noqa: N815 - QML property adı
        bool,
        _get_mini_widget_always_on_top,
        _set_mini_widget_always_on_top,
        notify=miniWidgetAlwaysOnTopChanged,
    )

    def _get_widget_device_ids(self) -> list[str]:
        raw = self._settings.value(_MINI_WIDGET_DEVICE_IDS_KEY, "[]", type=str)
        try:
            values = json.loads(raw)
        except (TypeError, json.JSONDecodeError):
            return []
        if not isinstance(values, list):
            return []
        return list(dict.fromkeys(str(value) for value in values if value))

    @Property("QVariantList", notify=widgetDeviceIdsChanged)
    def widgetDeviceIds(self) -> list[str]:  # noqa: N802 - QML property adı
        return self._get_widget_device_ids()

    @Slot(str, result=bool)
    def isWidgetDeviceSelected(self, device_id: str) -> bool:  # noqa: N802
        return device_id in self._get_widget_device_ids()

    @Slot(str, bool)
    def setWidgetDeviceSelected(self, device_id: str, selected: bool) -> None:  # noqa: N802
        device_id = device_id.strip()
        if not device_id:
            return
        device_ids = self._get_widget_device_ids()
        if selected and device_id not in device_ids:
            device_ids.append(device_id)
        elif not selected and device_id in device_ids:
            device_ids.remove(device_id)
        else:
            return
        self._settings.setValue(_MINI_WIDGET_DEVICE_IDS_KEY, json.dumps(device_ids))
        self.widgetDeviceIdsChanged.emit()

    @Slot()
    def showMiniWidget(self) -> None:  # noqa: N802
        self._set_mini_widget_visible(True)

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

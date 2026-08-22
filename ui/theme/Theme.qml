pragma Singleton
import QtQuick
import QtCore

// Tema seçimi: Sistem / Açık / Koyu (plan madde 21).
//
// `Application.styleHints.colorScheme` HEM bizim özel renklerimizi HEM DE
// FluentWinUI3 stilinin native kontrollerini (Slider, Switch, Button...)
// aynı anda yönetir — FluentWinUI3'ün kendi Config.qml'i tam olarak bu
// property'yi okuyor. Bu yüzden burada kendi ayrı bir "isDark" mantığı
// kurup native kontrollerle senkronizasyonu kaçırmak yerine, doğrudan bu
// tek kaynağı set/unset ediyoruz — ikisi de otomatik uyumlu kalıyor.
QtObject {
    id: root

    property Settings settings: Settings {
        category: "appearance"
        property string mode: "system"  // "system" | "light" | "dark"
    }

    readonly property string mode: settings.mode

    function setMode(newMode) {
        settings.mode = newMode
        applyMode()
    }

    // `Application.styleHints.unsetColorScheme()` QML JS'ten çağrılabilir
    // bir fonksiyon olarak dışa açılmıyor (metaobject'te property "reset"
    // fonksiyonu olarak kayıtlı, gerçek bir JS metodu değil — "is not a
    // function" hatası verir). Bu yüzden uygulama Python tarafında
    // (core/app_settings.py) yapılıyor; Python C++ binding'i tüm
    // metodlara erişebiliyor.
    function applyMode() {
        appSettings.applyThemeMode(settings.mode)
    }

    readonly property bool isDark: Application.styleHints.colorScheme === Qt.ColorScheme.Dark

    Component.onCompleted: applyMode()

    // ---- Koyu palet ----
    readonly property color _darkBackground: "#1a1a1e"
    readonly property color _darkSurface: "#252529"
    readonly property color _darkSurfaceBorder: "#35353a"
    readonly property color _darkTextPrimary: "#f5f5f7"
    readonly property color _darkTextSecondary: "#9a9aa2"
    readonly property color _darkOfflineDot: "#6b6b72"

    // ---- Açık palet ----
    readonly property color _lightBackground: "#f2f2f5"
    readonly property color _lightSurface: "#ffffff"
    readonly property color _lightSurfaceBorder: "#dcdce2"
    readonly property color _lightTextPrimary: "#1a1a1e"
    readonly property color _lightTextSecondary: "#68686f"
    readonly property color _lightOfflineDot: "#b0b0b8"

    readonly property color background: isDark ? _darkBackground : _lightBackground
    readonly property color surface: isDark ? _darkSurface : _lightSurface
    readonly property color surfaceBorder: isDark ? _darkSurfaceBorder : _lightSurfaceBorder
    readonly property color textPrimary: isDark ? _darkTextPrimary : _lightTextPrimary
    readonly property color textSecondary: isDark ? _darkTextSecondary : _lightTextSecondary
    readonly property color offlineDot: isDark ? _darkOfflineDot : _lightOfflineDot

    // Marka/durum renkleri her iki temada da sabit (plan madde 22).
    readonly property color accent: "#4dabf7"
    readonly property color onlineDot: "#51cf66"
    readonly property color danger: "#ff6b6b"
}

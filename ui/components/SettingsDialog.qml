import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import "../theme"

// Ayarlar diyaloğu: tema seçimi (plan madde 21) + Windows başlangıcı
// (plan madde 20).
Dialog {
    id: root

    modal: true
    focus: true
    anchors.centerIn: parent
    width: 320
    title: "Ayarlar"

    background: Rectangle {
        color: Theme.surface
        border.color: Theme.surfaceBorder
        radius: 12
    }

    contentItem: ColumnLayout {
        spacing: 20

        ColumnLayout {
            Layout.fillWidth: true
            spacing: 6

            Text {
                text: "Tema"
                color: Theme.textPrimary
                font.pixelSize: 14
                font.bold: true
            }

            ButtonGroup {
                id: themeGroup
            }

            RadioButton {
                text: "Sistem"
                checked: Theme.mode === "system"
                ButtonGroup.group: themeGroup
                onClicked: Theme.setMode("system")
            }
            RadioButton {
                text: "Açık"
                checked: Theme.mode === "light"
                ButtonGroup.group: themeGroup
                onClicked: Theme.setMode("light")
            }
            RadioButton {
                text: "Koyu"
                checked: Theme.mode === "dark"
                ButtonGroup.group: themeGroup
                onClicked: Theme.setMode("dark")
            }
        }

        ColumnLayout {
            Layout.fillWidth: true
            spacing: 6

            Text {
                text: "Windows başlangıcı"
                color: Theme.textPrimary
                font.pixelSize: 14
                font.bold: true
            }

            CheckBox {
                id: autostartCheck
                text: "Windows açıldığında SmartLights'i başlat"
                checked: appSettings.autostartEnabled
                onToggled: appSettings.autostartEnabled = checked
            }
            CheckBox {
                text: "Başlangıçta pencereyi göster"
                enabled: autostartCheck.checked
                checked: !appSettings.startMinimized
                onToggled: appSettings.startMinimized = !checked
            }
        }
    }

    footer: DialogButtonBox {
        Button {
            text: "Kapat"
            DialogButtonBox.buttonRole: DialogButtonBox.AcceptRole
        }
    }
}

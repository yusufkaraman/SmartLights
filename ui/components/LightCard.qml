import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import "../theme"

// Faz 3: kart, toggle, brightness. Faz 4: karta tıklanınca renk/Kelvin
// paneli açılır (plan madde 10) — bu bileşen sadece açılış sinyalini
// fırlatır, panelin kendisi DevicePanel.qml'de.
Rectangle {
    id: root

    property string deviceName: ""
    property string brand: ""
    property string modelName: ""
    property bool online: true
    property bool power: false
    property int brightness: 0
    property int kelvin: 4000
    property int minKelvin: 2200
    property int maxKelvin: 6500
    property bool capBrightness: true
    property bool capRgb: false
    property bool capKelvin: false

    signal togglePower()
    signal brightnessRequested(int value)
    signal openDetails()
    signal editRequested()

    radius: 14
    color: Theme.surface
    border.color: power ? Theme.accent : Theme.surfaceBorder
    border.width: power ? 2 : 1
    clip: true

    // Kartın boş alanına tıklayınca detay panelini aç. Slider/Switch
    // kendi input alanlarını üstlerinde (ColumnLayout sonra tanımlandığı
    // için üstte) yakaladığından burada çakışma olmaz.
    MouseArea {
        anchors.fill: parent
        onClicked: root.openDetails()
    }

    ColumnLayout {
        anchors.fill: parent
        anchors.margins: 12
        spacing: 4

        RowLayout {
            Layout.fillWidth: true
            spacing: 6

            Rectangle {
                width: 8
                height: 8
                radius: 4
                color: root.online ? Theme.onlineDot : Theme.offlineDot
            }
            Text {
                text: root.deviceName
                color: Theme.textPrimary
                font.pixelSize: 14
                font.bold: true
                elide: Text.ElideRight
                Layout.fillWidth: true
            }
        }

        Text {
            text: root.brand.toUpperCase() + " · " + root.modelName
            color: Theme.textSecondary
            font.pixelSize: 11
            elide: Text.ElideRight
            Layout.fillWidth: true
        }

        Item { Layout.fillHeight: true; Layout.minimumHeight: 4 }

        Text {
            text: "💡"
            font.pixelSize: 28
            opacity: root.power ? 1.0 : 0.25
            Layout.alignment: Qt.AlignHCenter

            Behavior on opacity {
                NumberAnimation { duration: 150 }
            }
        }

        Item { Layout.fillHeight: true; Layout.minimumHeight: 4 }

        Slider {
            id: brightnessSlider
            Layout.fillWidth: true
            Layout.preferredHeight: 24
            from: 1
            to: 100
            value: root.brightness
            enabled: root.capBrightness && root.power

            // Throttle (plan madde 15): DEBOUNCE değil — sürüklerken
            // sessizlik beklemez, en fazla 60ms'de bir canlı komut gönderir.
            // Klasik debounce (her hareketle timer'ı sıfırlama) kullanıcı
            // parmağını/mouse'u bırakana kadar hiç komut göndermiyordu.
            property var pendingValue: undefined
            property bool cooling: false

            function requestUpdate(value) {
                pendingValue = value
                if (!cooling)
                    sendPending()
            }

            function sendPending() {
                if (pendingValue === undefined)
                    return
                var v = pendingValue
                pendingValue = undefined
                cooling = true
                throttle.restart()
                root.brightnessRequested(Math.round(v))
            }

            Timer {
                id: throttle
                interval: 60
                onTriggered: {
                    brightnessSlider.cooling = false
                    brightnessSlider.sendPending()
                }
            }

            onMoved: requestUpdate(brightnessSlider.value)
        }

        RowLayout {
            Layout.fillWidth: true

            Text {
                text: root.brightness + "%"
                color: Theme.textSecondary
                font.pixelSize: 11
            }
            Item { Layout.fillWidth: true }
            Switch {
                checked: root.power
                onToggled: root.togglePower()
            }
        }
    }

    // İsim/oda düzenleme (kalem) düğmesi — sağ üst köşe. ColumnLayout'tan
    // sonra tanımlandığı için z-sırasında üstte, kartın geri kalanını
    // kaplayan arka plan MouseArea'sından önce tıklamayı yakalar.
    Rectangle {
        width: 26
        height: 26
        radius: 13
        anchors.top: parent.top
        anchors.right: parent.right
        anchors.margins: 6
        color: editArea.containsMouse ? Theme.surfaceBorder : "transparent"

        Text {
            anchors.centerIn: parent
            text: "✎"
            font.pixelSize: 13
            color: Theme.textSecondary
        }

        MouseArea {
            id: editArea
            anchors.fill: parent
            hoverEnabled: true
            onClicked: root.editRequested()
        }
    }
}

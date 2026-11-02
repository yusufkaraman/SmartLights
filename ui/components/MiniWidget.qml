import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import QtQuick.Window
import "../theme"

Window {
    id: root
    objectName: "miniWidget"

    width: 300
    height: 410
    minimumWidth: 280
    minimumHeight: 390
    visible: appSettings.miniWidgetVisible
    title: "SmartLights Mini Panel"
    color: "transparent"
    flags: Qt.Tool | Qt.FramelessWindowHint
           | (appSettings.miniWidgetAlwaysOnTop ? Qt.WindowStaysOnTopHint : 0)

    property string activeDeviceId: ""
    property var selectedDevices: {
        deviceModel.dataVersion
        var ids = appSettings.widgetDeviceIds
        var result = []
        for (var i = 0; i < ids.length; ++i) {
            var device = deviceModel.deviceById(ids[i])
            if (device && device.id)
                result.push(device)
        }
        return result
    }
    property var activeDevice: {
        deviceModel.dataVersion
        return activeDeviceId ? deviceModel.deviceById(activeDeviceId) : ({})
    }
    readonly property int activeRow: activeDevice && activeDevice.index !== undefined
                                     ? activeDevice.index : -1

    function ensureActiveDevice() {
        var found = false
        for (var i = 0; i < selectedDevices.length; ++i) {
            if (selectedDevices[i].id === activeDeviceId) {
                found = true
                break
            }
        }
        if (!found)
            activeDeviceId = selectedDevices.length ? selectedDevices[0].id : ""
    }

    onSelectedDevicesChanged: ensureActiveDevice()
    onVisibleChanged: {
        if (visible)
            ensureActiveDevice()
    }
    onClosing: (closeEvent) => {
        closeEvent.accepted = false
        appSettings.miniWidgetVisible = false
    }

    Rectangle {
        anchors.fill: parent
        radius: 16
        color: Theme.surface
        border.color: Theme.surfaceBorder
        border.width: 1

        ColumnLayout {
            anchors.fill: parent
            anchors.margins: 10
            spacing: 4

            RowLayout {
                Layout.fillWidth: true
                spacing: 6

                MouseArea {
                    Layout.fillWidth: true
                    Layout.preferredHeight: 30
                    cursorShape: Qt.SizeAllCursor
                    onPressed: root.startSystemMove()

                    RowLayout {
                        anchors.fill: parent
                        spacing: 8

                        Text { text: "💡"; font.pixelSize: 16 }
                        Text {
                            text: "SmartLights"
                            color: Theme.textPrimary
                            font.pixelSize: 15
                            font.bold: true
                            Layout.fillWidth: true
                        }
                    }
                }

                ToolButton {
                    text: appSettings.miniWidgetAlwaysOnTop ? "📌" : "○"
                    ToolTip.visible: hovered
                    ToolTip.text: appSettings.miniWidgetAlwaysOnTop
                                  ? "Üstte tutmayı kapat" : "Her zaman üstte tut"
                    onClicked: appSettings.miniWidgetAlwaysOnTop =
                               !appSettings.miniWidgetAlwaysOnTop
                }
                ToolButton {
                    text: "⚙"
                    ToolTip.visible: hovered
                    ToolTip.text: "Widget ışıklarını seç"
                    onClicked: devicePicker.open()
                }
                ToolButton {
                    text: "✕"
                    ToolTip.visible: hovered
                    ToolTip.text: "Mini paneli gizle"
                    onClicked: appSettings.miniWidgetVisible = false
                }
            }

            Rectangle {
                Layout.fillWidth: true
                Layout.preferredHeight: 1
                color: Theme.surfaceBorder
            }

            StackLayout {
                Layout.fillWidth: true
                Layout.fillHeight: true
                currentIndex: root.selectedDevices.length === 0 ? 0 : 1

                ColumnLayout {
                    spacing: 10

                    Item { Layout.fillHeight: true }
                    Text {
                        text: "Mini panel için henüz ışık seçilmedi."
                        color: Theme.textSecondary
                        font.pixelSize: 13
                        wrapMode: Text.WordWrap
                        horizontalAlignment: Text.AlignHCenter
                        Layout.fillWidth: true
                    }
                    Button {
                        text: "Işıkları Seç"
                        Layout.alignment: Qt.AlignHCenter
                        onClicked: devicePicker.open()
                    }
                    Item { Layout.fillHeight: true }
                }

                ColumnLayout {
                    spacing: 5

                ComboBox {
                    id: deviceSelector
                    Layout.fillWidth: true
                    model: root.selectedDevices
                    textRole: "name"

                    function syncIndex() {
                        for (var i = 0; i < root.selectedDevices.length; ++i) {
                            if (root.selectedDevices[i].id === root.activeDeviceId) {
                                currentIndex = i
                                return
                            }
                        }
                        currentIndex = root.selectedDevices.length ? 0 : -1
                    }

                    onActivated: (index) => {
                        if (index >= 0 && index < root.selectedDevices.length)
                            root.activeDeviceId = root.selectedDevices[index].id
                    }
                    Component.onCompleted: syncIndex()
                    Connections {
                        target: root
                        function onActiveDeviceIdChanged() { deviceSelector.syncIndex() }
                        function onSelectedDevicesChanged() { deviceSelector.syncIndex() }
                    }
                }

                RowLayout {
                    Layout.fillWidth: true

                    ColumnLayout {
                        Layout.fillWidth: true
                        spacing: 1
                        Text {
                            text: root.activeDevice.name || "Işık"
                            color: Theme.textPrimary
                            font.pixelSize: 15
                            font.bold: true
                            elide: Text.ElideRight
                            Layout.fillWidth: true
                        }
                        Text {
                            text: root.activeDevice.online ? "Çevrimiçi" : "Çevrimdışı"
                            color: root.activeDevice.online ? Theme.onlineDot : Theme.offlineDot
                            font.pixelSize: 11
                        }
                    }

                    Button {
                        text: root.activeDevice.power ? "KAPAT" : "AÇ"
                        enabled: root.activeRow >= 0 && root.activeDevice.online
                        highlighted: root.activeDevice.power || false
                        onClicked: deviceModel.toggle(root.activeRow)
                    }
                }

                ColumnLayout {
                    Layout.fillWidth: true
                    spacing: 2
                    enabled: root.activeDevice.capBrightness && root.activeDevice.online

                    RowLayout {
                        Layout.fillWidth: true
                        Text { text: "Parlaklık"; color: Theme.textPrimary; font.pixelSize: 12 }
                        Item { Layout.fillWidth: true }
                        Text {
                            text: (root.activeDevice.brightness || 0) + "%"
                            color: Theme.textSecondary
                            font.pixelSize: 12
                        }
                    }

                    Slider {
                        id: brightnessSlider
                        Layout.fillWidth: true
                        from: 1
                        to: 100
                        value: root.activeDevice.brightness || 1

                        property var pendingValue: undefined
                        property bool cooling: false

                        function requestUpdate(v) {
                            pendingValue = v
                            if (!cooling)
                                sendPending()
                        }
                        function sendPending() {
                            if (pendingValue === undefined || root.activeRow < 0)
                                return
                            var valueToSend = pendingValue
                            pendingValue = undefined
                            cooling = true
                            brightnessThrottle.restart()
                            deviceModel.setBrightness(root.activeRow, Math.round(valueToSend))
                        }
                        onMoved: requestUpdate(value)
                        Timer {
                            id: brightnessThrottle
                            interval: 60
                            onTriggered: {
                                brightnessSlider.cooling = false
                                brightnessSlider.sendPending()
                            }
                        }
                    }
                }

                ColumnLayout {
                    Layout.fillWidth: true
                    spacing: 2
                    visible: root.activeDevice.capKelvin || false
                    enabled: root.activeDevice.online

                    RowLayout {
                        Layout.fillWidth: true
                        Text { text: "Beyaz sıcaklığı"; color: Theme.textPrimary; font.pixelSize: 12 }
                        Item { Layout.fillWidth: true }
                        Text {
                            text: (root.activeDevice.kelvin || root.activeDevice.minKelvin || 2200) + " K"
                            color: Theme.textSecondary
                            font.pixelSize: 12
                        }
                    }

                    Slider {
                        id: kelvinSlider
                        Layout.fillWidth: true
                        from: root.activeDevice.minKelvin || 2200
                        to: root.activeDevice.maxKelvin || 6500
                        value: root.activeDevice.kelvin || from

                        property real pendingKelvin: NaN
                        property bool cooling: false

                        function requestUpdate(v) {
                            pendingKelvin = v
                            if (!cooling)
                                sendPending()
                        }
                        function sendPending() {
                            if (isNaN(pendingKelvin) || root.activeRow < 0)
                                return
                            var valueToSend = pendingKelvin
                            pendingKelvin = NaN
                            cooling = true
                            kelvinThrottle.restart()
                            deviceModel.setKelvin(root.activeRow, Math.round(valueToSend))
                        }
                        onMoved: requestUpdate(value)
                        Timer {
                            id: kelvinThrottle
                            interval: 80
                            onTriggered: {
                                kelvinSlider.cooling = false
                                kelvinSlider.sendPending()
                            }
                        }
                    }
                }

                ColumnLayout {
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    visible: root.activeDevice.capRgb || false
                    enabled: root.activeDevice.online
                    spacing: 4

                    Text {
                        text: "Renk"
                        color: Theme.textPrimary
                        font.pixelSize: 12
                    }

                    ColorWheel {
                        id: colorWheel
                        Layout.alignment: Qt.AlignHCenter
                        Layout.preferredWidth: 160
                        Layout.preferredHeight: 160

                        property var pendingColor: null
                        property bool cooling: false

                        onColorPicked: (r, g, b) => {
                            pendingColor = [r, g, b]
                            if (!cooling)
                                sendPending()
                        }
                        function sendPending() {
                            if (!pendingColor || root.activeRow < 0)
                                return
                            var valueToSend = pendingColor
                            pendingColor = null
                            cooling = true
                            colorThrottle.restart()
                            deviceModel.setRgb(
                                root.activeRow,
                                valueToSend[0], valueToSend[1], valueToSend[2]
                            )
                        }
                        Timer {
                            id: colorThrottle
                            interval: 60
                            onTriggered: {
                                colorWheel.cooling = false
                                colorWheel.sendPending()
                            }
                        }
                    }
                }

                Item {
                    Layout.fillHeight: !(root.activeDevice.capRgb || false)
                }
            }
            }
        }
    }

    Dialog {
        id: devicePicker
        parent: Overlay.overlay
        modal: true
        focus: true
        width: Math.min(280, root.width - 20)
        height: Math.min(400, root.height - 40)
        x: (root.width - width) / 2
        y: (root.height - height) / 2
        title: "Mini panel ışıkları"
        standardButtons: Dialog.Close

        background: Rectangle {
            color: Theme.surface
            border.color: Theme.surfaceBorder
            radius: 12
        }

        contentItem: ScrollView {
            clip: true

            ColumnLayout {
                width: devicePicker.availableWidth
                spacing: 4

                Label {
                    visible: deviceModel.rowCount() === 0
                    text: "Önce cihazları tarayın."
                    color: Theme.textSecondary
                    wrapMode: Text.WordWrap
                    Layout.fillWidth: true
                }

                Repeater {
                    model: {
                        deviceModel.roomStructure
                        return deviceModel.rowCount()
                    }

                    delegate: CheckBox {
                        Layout.fillWidth: true
                        property var device: {
                            deviceModel.dataVersion
                            return deviceModel.deviceAt(index)
                        }
                        text: device.name + (device.room ? " · " + device.room : "")
                        checked: {
                            appSettings.widgetDeviceIds
                            return appSettings.isWidgetDeviceSelected(device.id)
                        }
                        onToggled: appSettings.setWidgetDeviceSelected(device.id, checked)
                    }
                }
            }
        }
    }
}

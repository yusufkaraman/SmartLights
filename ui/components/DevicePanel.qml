import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import "../theme"

// Kart tıklanınca sağdan açılan detay paneli (plan madde 10, 11, 12).
// Beyaz sekmesindeki Kelvin sınırları cihazın kendi capability'sinden
// gelir, sabit 2200-6500 varsayılmaz (plan madde 12, 13).
Popup {
    id: root

    modal: true
    focus: true
    closePolicy: Popup.CloseOnEscape | Popup.CloseOnPressOutside
    width: 320
    padding: 20

    property int rowIndex: -1
    property string deviceName: ""
    property string brand: ""
    property string modelName: ""
    property bool capRgb: false
    property bool capKelvin: false
    property int minKelvin: 2200
    property int maxKelvin: 6500
    property int currentKelvin: 4000

    function openFor(idx, name, brandVal, modelVal, rgbCap, kelvinCap, minK, maxK, kelvinVal) {
        rowIndex = idx
        deviceName = name
        brand = brandVal
        modelName = modelVal
        capRgb = rgbCap
        capKelvin = kelvinCap
        minKelvin = minK
        maxKelvin = maxK
        currentKelvin = kelvinVal
        tabBar.currentIndex = 0
        open()
    }

    background: Rectangle {
        color: Theme.surface
        border.color: Theme.surfaceBorder
        radius: 16
    }

    Component {
        id: colorTabComponent

        ColumnLayout {
            spacing: 16

            ColorWheel {
                id: wheel
                Layout.alignment: Qt.AlignHCenter
                Layout.preferredWidth: 220
                Layout.preferredHeight: 220

                // Throttle (plan madde 15): DEBOUNCE değil — çark üzerinde
                // sürüklerken sessizlik beklemez, en fazla 60ms'de bir canlı
                // komut gönderir.
                property var pendingColor: null
                property bool cooling: false

                onColorPicked: (r, g, b) => {
                    pendingColor = [r, g, b]
                    if (!cooling)
                        sendPending()
                }

                function sendPending() {
                    if (!pendingColor)
                        return
                    var c = pendingColor
                    pendingColor = null
                    cooling = true
                    colorThrottle.restart()
                    if (root.rowIndex >= 0)
                        deviceModel.setRgb(root.rowIndex, c[0], c[1], c[2])
                }

                Timer {
                    id: colorThrottle
                    interval: 60
                    onTriggered: {
                        wheel.cooling = false
                        wheel.sendPending()
                    }
                }
            }

            Text {
                text: "Çark üzerinde sürükleyerek renk seçin"
                color: Theme.textSecondary
                font.pixelSize: 11
                Layout.alignment: Qt.AlignHCenter
            }
        }
    }

    contentItem: ColumnLayout {
        spacing: 16

        RowLayout {
            Layout.fillWidth: true

            ColumnLayout {
                Layout.fillWidth: true
                spacing: 2

                Text {
                    text: root.deviceName
                    color: Theme.textPrimary
                    font.pixelSize: 18
                    font.bold: true
                    elide: Text.ElideRight
                    Layout.fillWidth: true
                }
                Text {
                    text: root.brand.toUpperCase() + " · " + root.modelName
                    color: Theme.textSecondary
                    font.pixelSize: 12
                }
            }

            ToolButton {
                text: "✕"
                onClicked: root.close()
            }
        }

        TabBar {
            id: tabBar
            Layout.fillWidth: true
            TabButton { text: "Renk"; visible: root.capRgb }
            TabButton { text: "Beyaz"; visible: root.capKelvin }
        }

        StackLayout {
            Layout.fillWidth: true
            Layout.preferredHeight: 300
            currentIndex: tabBar.currentIndex

            // ---- Renk sekmesi ----
            // Loader ile TEMBEL oluşturuluyor: Popup'ın contentItem'ı normalde
            // eager (henüz görünmezken) oluşturulur ve QML Canvas, gizliyken
            // oluşturulunca render backend'ini düzgün kuramayıp kalıcı olarak
            // boş kalıyordu — requestPaint() sonradan çağrılsa bile düzelmiyordu.
            // `active: root.visible` ile ColorWheel'i panel gerçekten ekranda
            // görünür olduğu an oluşturuyoruz, bu sorunu kökten çözüyor.
            Loader {
                active: root.visible && root.capRgb
                sourceComponent: colorTabComponent
            }

            // ---- Beyaz sekmesi ----
            ColumnLayout {
                spacing: 16

                Text {
                    text: Math.round(kelvinSlider.value) + " K"
                    color: Theme.textPrimary
                    font.pixelSize: 20
                    font.bold: true
                    Layout.alignment: Qt.AlignHCenter
                }

                // Görsel sıcak/soğuk göstergesi (plan madde 12 mockup'ı:
                // "🔥━━━━━━━━━━━━●━━━━━━━━━━━━━━━❄️") — sadece sayı yeterli
                // değildi, kullanıcı değerin sıcak/soğuk ekseninde nereye
                // düştüğünü göremiyordu.
                RowLayout {
                    Layout.fillWidth: true
                    spacing: 8

                    Text { text: "🔥"; font.pixelSize: 16 }

                    Rectangle {
                        Layout.fillWidth: true
                        height: 6
                        radius: 3
                        gradient: Gradient {
                            orientation: Gradient.Horizontal
                            GradientStop { position: 0.0; color: "#ff8c42" }
                            GradientStop { position: 0.5; color: "#fff4e0" }
                            GradientStop { position: 1.0; color: "#a9d6ff" }
                        }
                    }

                    Text { text: "❄️"; font.pixelSize: 16 }
                }

                Slider {
                    id: kelvinSlider
                    Layout.fillWidth: true
                    from: root.minKelvin
                    to: root.maxKelvin
                    value: root.currentKelvin

                    // Throttle (plan madde 15): sürüklerken periyodik canlı
                    // komut, sadece bırakınca değil.
                    property real pendingKelvin: NaN
                    property bool cooling: false

                    function requestUpdate(v) {
                        pendingKelvin = v
                        if (!cooling)
                            sendPending()
                    }

                    function sendPending() {
                        if (isNaN(pendingKelvin))
                            return
                        var v = pendingKelvin
                        pendingKelvin = NaN
                        cooling = true
                        kelvinThrottle.restart()
                        if (root.rowIndex >= 0)
                            deviceModel.setKelvin(root.rowIndex, Math.round(v))
                    }

                    Timer {
                        id: kelvinThrottle
                        interval: 80
                        onTriggered: {
                            kelvinSlider.cooling = false
                            kelvinSlider.sendPending()
                        }
                    }
                    onMoved: requestUpdate(kelvinSlider.value)
                }

                RowLayout {
                    Layout.fillWidth: true
                    Text { text: root.minKelvin + "K"; color: Theme.textSecondary; font.pixelSize: 10 }
                    Item { Layout.fillWidth: true }
                    Text { text: root.maxKelvin + "K"; color: Theme.textSecondary; font.pixelSize: 10 }
                }

                GridLayout {
                    Layout.fillWidth: true
                    columns: 3
                    columnSpacing: 8
                    rowSpacing: 8

                    Repeater {
                        // Hazır presetler (plan madde 12): sadece cihazın
                        // desteklediği aralığa girenler gösterilir.
                        model: [
                            { label: "Mum", k: 2200 },
                            { label: "Sıcak", k: 2700 },
                            { label: "Normal", k: 4000 },
                            { label: "Gün Işığı", k: 5000 },
                            { label: "Soğuk", k: 6500 },
                        ]
                        delegate: Button {
                            Layout.fillWidth: true
                            text: modelData.label + "\n" + modelData.k + "K"
                            visible: modelData.k >= root.minKelvin && modelData.k <= root.maxKelvin
                            onClicked: {
                                kelvinSlider.value = modelData.k
                                if (root.rowIndex >= 0)
                                    deviceModel.setKelvin(root.rowIndex, modelData.k)
                            }
                        }
                    }
                }
            }
        }
    }
}

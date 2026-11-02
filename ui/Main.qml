import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import "components"
import "theme"

// Faz 6 (plan madde 28): odalar + sahneler. Cihaz kartları GridView'ın
// native `section` mekanizmasıyla odalara göre gruplanıyor (aşağıdaki
// GridView'daki nota bakın); global "Tüm Işıklar" çubuğu ve "Hızlı
// Sahneler" satırı eklendi (plan madde 10, 18).
ApplicationWindow {
    id: window
    objectName: "mainWindow"
    width: 1100
    height: 720
    // Windows başlangıcında `--minimized` ile açıldıysa pencere hiç
    // görünmeden tray'de başlar (plan madde 20); manuel açılışta normal
    // şekilde görünür.
    visible: !startMinimized
    title: "Smart Lights"
    color: Theme.background

    // Faz 7 (plan madde 19): pencere kapatılınca uygulama çıkmaz, tray'e
    // küçülür. main.py'de app.setQuitOnLastWindowClosed(False) ile birlikte
    // çalışır.
    onClosing: (closeEvent) => {
        closeEvent.accepted = false
        window.hide()
    }

    header: ToolBar {
        background: Rectangle { color: Theme.background }

        RowLayout {
            anchors.fill: parent
            anchors.leftMargin: 20
            anchors.rightMargin: 20

            Text {
                text: "Evim"
                color: Theme.textPrimary
                font.pixelSize: 20
                font.bold: true
            }

            Item { Layout.fillWidth: true }

            Text {
                text: deviceModel.scanning
                    ? "Taranıyor…"
                    : (deviceModel.rowCount() + " cihaz çevrimiçi")
                color: Theme.textSecondary
                font.pixelSize: 13
            }

            ToolButton {
                text: "🔑"
                font.pixelSize: 14
                ToolTip.visible: hovered
                ToolTip.text: "Tapo hesabı"
                onClicked: tapoCredentialsDialog.openWithMessage(
                    "Tapo cihazlarınızı bulabilmek için TP-Link hesap bilgilerinizi girin."
                )
            }

            ToolButton {
                text: "⟳"
                font.pixelSize: 16
                enabled: !deviceModel.scanning
                onClicked: deviceModel.refresh()
            }

            ToolButton {
                text: "▣"
                font.pixelSize: 16
                ToolTip.visible: hovered
                ToolTip.text: "Mini paneli göster"
                onClicked: appSettings.miniWidgetVisible = true
            }

            ToolButton {
                text: "⚙"
                font.pixelSize: 16
                ToolTip.visible: hovered
                ToolTip.text: "Ayarlar"
                onClicked: settingsDialog.open()
            }
        }
    }

    ScrollView {
        anchors.fill: parent
        anchors.margins: 20
        clip: true
        contentWidth: availableWidth

        ColumnLayout {
            width: window.width - 40
            spacing: 28

            // ---- Tüm Işıklar (plan madde 10 mockup) ----
            Rectangle {
                Layout.fillWidth: true
                height: 56
                radius: 12
                color: Theme.surface
                border.color: Theme.surfaceBorder

                RowLayout {
                    anchors.fill: parent
                    anchors.leftMargin: 16
                    anchors.rightMargin: 16

                    Text {
                        text: "Tüm Işıklar"
                        color: Theme.textPrimary
                        font.pixelSize: 15
                        font.bold: true
                    }
                    Item { Layout.fillWidth: true }
                    Button { text: "AÇ"; onClicked: deviceModel.turnOnAll() }
                    Button { text: "KAPAT"; onClicked: deviceModel.turnOffAll() }
                }
            }

            // ---- Hızlı Sahneler (plan madde 18) ----
            ColumnLayout {
                Layout.fillWidth: true
                spacing: 8

                Text {
                    text: "Hızlı Sahneler"
                    color: Theme.textPrimary
                    font.pixelSize: 15
                    font.bold: true
                }

                Flow {
                    Layout.fillWidth: true
                    spacing: 8

                    Repeater {
                        model: deviceModel.scenes
                        delegate: SceneChip {
                            icon: modelData.icon
                            label: modelData.name
                            onClicked: deviceModel.applyScene(modelData.id)
                            onRemoveRequested: deviceModel.deleteScene(modelData.id)
                        }
                    }

                    SceneChip {
                        icon: "+"
                        label: "Sahne Ekle"
                        onClicked: newSceneDialog.openFresh()
                    }
                }
            }

            // ---- Odalara göre cihazlar (plan madde 17) ----
            //
            // NOT: GridView'ın (ListView'ın aksine) native `section` desteği
            // bu Qt sürümünde YOK (metadata'dan doğrulandı). Onun yerine iki
            // ayrı, farklı sıklıkta değişen kaynak kullanılıyor:
            //   - `deviceModel.roomStructure` — hangi oda hangi index'leri
            //     içeriyor; SADECE yapısal değişikliklerde (tarama, oda
            //     değiştiren rename) güncellenir. Bu dış Repeater'ın modeli
            //     olduğu için ODA/KART SAYISI değişmedikçe hiç yeniden
            //     kurulmaz.
            //   - `deviceModel.deviceAt(index)` + `deviceModel.dataVersion`
            //     — kartın gerçek verisi. `dataVersion`'ı bir property
            //     binding içinde OKUYUP (kullanmadan) bağımlılık kurarak,
            //     sadece o kartın property'leri yeniden değerlendirilir.
            //
            // Önceki tasarımda TEK bir "gruplanmış liste" her optimistic
            // güncellemede (örn. slider sürüklenirken ~60ms'de bir) yeniden
            // hesaplanıp QML'e YENİ bir liste olarak veriliyordu; Repeater
            // bunu her seferinde TÜM kartları yok edip yeniden oluşturarak
            // karşılıyordu — sürüklenmekte olan Slider da dahil. "Hareket
            // edip sonra takılma" bug'ının asıl sebebi buydu.
            Repeater {
                model: deviceModel.roomStructure

                delegate: ColumnLayout {
                    Layout.fillWidth: true
                    spacing: 10

                    RoomHeader {
                        Layout.fillWidth: true
                        roomName: modelData.room
                    }

                    Flow {
                        Layout.fillWidth: true
                        spacing: 16

                        Repeater {
                            model: modelData.indices

                            delegate: LightCard {
                                width: 224
                                height: 204

                                // `dataVersion` okunuyor (kullanılmıyor) ki bu
                                // binding her veri değişiminde yeniden
                                // değerlendirilsin — ama Repeater'ın modeli
                                // (yukarıdaki indices dizisi) SABİT kaldığı
                                // için bu kart yeniden OLUŞTURULMAZ.
                                property var device: {
                                    deviceModel.dataVersion
                                    return deviceModel.deviceAt(modelData)
                                }

                                deviceName: device.name
                                brand: device.brand
                                modelName: device.modelName
                                online: device.online
                                power: device.power
                                brightness: device.brightness
                                kelvin: device.kelvin
                                minKelvin: device.minKelvin
                                maxKelvin: device.maxKelvin
                                capBrightness: device.capBrightness
                                capRgb: device.capRgb
                                capKelvin: device.capKelvin

                                onTogglePower: deviceModel.toggle(modelData)
                                onBrightnessRequested: (value) => deviceModel.setBrightness(modelData, value)
                                onOpenDetails: devicePanel.openFor(
                                    modelData, device.name, device.brand, device.modelName,
                                    device.capRgb, device.capKelvin,
                                    device.minKelvin, device.maxKelvin, device.kelvin
                                )
                                onEditRequested: renameDialog.openFor(
                                    modelData, device.name, device.room === "Diğer" ? "" : device.room
                                )
                            }
                        }
                    }
                }
            }

            Label {
                visible: deviceModel.rowCount() === 0 && !deviceModel.scanning
                text: "Hiç cihaz bulunamadı. ⟳ ile yeniden tarayın."
                color: Theme.textSecondary
                Layout.alignment: Qt.AlignHCenter
            }

            Item { Layout.preferredHeight: 20 }
        }
    }

    // Hata bildirimi (plan madde 16: "⚠ Salon Lambası yanıt vermedi").
    Rectangle {
        id: toast
        visible: opacity > 0
        opacity: 0
        anchors.horizontalCenter: parent.horizontalCenter
        anchors.bottom: parent.bottom
        anchors.bottomMargin: 24
        radius: 8
        color: Theme.danger
        width: toastText.implicitWidth + 32
        height: toastText.implicitHeight + 16

        Text {
            id: toastText
            anchors.centerIn: parent
            color: "#ffffff"
            font.pixelSize: 13
        }

        Behavior on opacity { NumberAnimation { duration: 200 } }

        Timer {
            id: hideTimer
            interval: 3000
            onTriggered: toast.opacity = 0
        }
    }

    Connections {
        target: deviceModel
        function onErrorOccurred(message) {
            toastText.text = "⚠ " + message
            toast.opacity = 1
            hideTimer.restart()
        }
        // Plan madde 9: Tapo kimlik hatası tespit edilince kullanıcıyı bir
        // konsol istemi yerine doğrudan bu GUI diyaloğuyla karşıla.
        function onTapoCredentialsNeeded(message) {
            tapoCredentialsDialog.openWithMessage(message)
        }
    }

    DevicePanel {
        id: devicePanel
        objectName: "devicePanel"
        x: window.width - width - 20
        y: 60
    }

    RenameDialog {
        id: renameDialog
    }

    SettingsDialog {
        id: settingsDialog
    }

    TapoCredentialsDialog {
        id: tapoCredentialsDialog
    }

    NewSceneDialog {
        id: newSceneDialog
    }

    Component.onCompleted: deviceModel.refresh()
}

import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import "../theme"

// "+" ile yeni sahne oluşturma: o an tüm çevrimiçi cihazların durumunu
// (renk/kelvin/parlaklık/aç-kapa) yakalayıp isim vererek kaydeder
// (plan madde 18).
Dialog {
    id: root

    modal: true
    focus: true
    anchors.centerIn: parent
    width: 300
    title: "Yeni sahne"

    background: Rectangle {
        color: Theme.surface
        border.color: Theme.surfaceBorder
        radius: 12
    }

    contentItem: ColumnLayout {
        spacing: 12

        Text {
            text: "Mevcut tüm ışıkların şu anki durumu bu isimle kaydedilecek."
            color: Theme.textSecondary
            font.pixelSize: 11
            wrapMode: Text.WordWrap
            Layout.fillWidth: true
        }

        TextField {
            id: nameField
            Layout.fillWidth: true
            placeholderText: "Örn. Film Gecesi"
        }
    }

    footer: DialogButtonBox {
        Button {
            text: "İptal"
            DialogButtonBox.buttonRole: DialogButtonBox.RejectRole
        }
        Button {
            text: "Kaydet"
            highlighted: true
            DialogButtonBox.buttonRole: DialogButtonBox.AcceptRole
        }
    }

    function openFresh() {
        nameField.text = ""
        open()
    }

    onAccepted: {
        if (nameField.text.trim().length > 0)
            deviceModel.createSceneFromCurrent(nameField.text)
    }
}

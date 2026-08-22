import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import "../theme"

// Cihaz ismi ve oda ataması için basit düzenleme diyaloğu (plan madde 17:
// oda ataması "oda oluşturma" işlevi görür — herhangi bir yeni oda ismi
// yazmak o odayı otomatik oluşturur, ayrı bir "oda yönetimi" ekranına
// gerek kalmaz).
Dialog {
    id: root

    modal: true
    focus: true
    anchors.centerIn: parent
    width: 320
    title: "Cihazı düzenle"

    property int rowIndex: -1

    function openFor(idx, currentName, currentRoom) {
        rowIndex = idx
        nameField.text = currentName
        roomField.text = currentRoom
        open()
    }

    background: Rectangle {
        color: Theme.surface
        border.color: Theme.surfaceBorder
        radius: 12
    }

    contentItem: ColumnLayout {
        spacing: 12

        Text {
            text: "İsim"
            color: Theme.textSecondary
            font.pixelSize: 11
        }
        TextField {
            id: nameField
            Layout.fillWidth: true
            placeholderText: "Örn. Salon Lambası"
        }

        Text {
            text: "Oda"
            color: Theme.textSecondary
            font.pixelSize: 11
        }
        TextField {
            id: roomField
            Layout.fillWidth: true
            placeholderText: "Örn. Salon (boş bırakılırsa \"Diğer\")"
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

    onAccepted: {
        if (root.rowIndex >= 0)
            deviceModel.renameDevice(root.rowIndex, nameField.text, roomField.text)
    }
}

import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import "../theme"

// GridView.section.delegate: aynı odadaki kartlardan önce bir kez gösterilir.
// Odaya özel "Tümünü Aç/Kapat" burada (plan madde 17).
RowLayout {
    id: root

    property string roomName: ""

    height: 40

    Text {
        text: root.roomName.toUpperCase()
        color: Theme.textPrimary
        font.pixelSize: 13
        font.bold: true
    }
    Item { Layout.fillWidth: true }
    Button {
        text: "Tümünü Aç"
        flat: true
        onClicked: deviceModel.turnOnRoom(root.roomName)
    }
    Button {
        text: "Tümünü Kapat"
        flat: true
        onClicked: deviceModel.turnOffRoom(root.roomName)
    }
}

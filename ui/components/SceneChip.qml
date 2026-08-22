import QtQuick
import QtQuick.Controls
import "../theme"

// Hızlı sahne düğmesi (plan madde 18 mockup: "[ ☀ Günlük ] [ 🌙 Gece ] ...").
Rectangle {
    id: root

    property string icon: "✨"
    property string label: ""

    signal clicked()
    signal removeRequested()

    implicitWidth: rowContent.implicitWidth + 28
    implicitHeight: 36
    radius: height / 2
    color: mouseArea.containsMouse ? Theme.surfaceBorder : Theme.surface
    border.color: Theme.surfaceBorder

    Row {
        id: rowContent
        anchors.centerIn: parent
        spacing: 6

        Text { text: root.icon; font.pixelSize: 15 }
        Text { text: root.label; color: Theme.textPrimary; font.pixelSize: 13 }
    }

    MouseArea {
        id: mouseArea
        anchors.fill: parent
        hoverEnabled: true
        acceptedButtons: Qt.LeftButton | Qt.RightButton
        onClicked: (mouse) => {
            if (mouse.button === Qt.RightButton)
                root.removeRequested()
            else
                root.clicked()
        }
    }
}

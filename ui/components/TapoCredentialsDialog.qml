import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import "../theme"

// Tapo hesap bilgisi giriş/düzeltme diyaloğu (plan madde 9: Tapo auth
// fail'in konsol yerine GUI'de düzgün ele alınması). `deviceModel`
// bunu hem kullanıcı manuel açtığında hem de kimlik doğrulama
// başarısız olduğunda (tapoCredentialsNeeded sinyali) otomatik açar.
Dialog {
    id: root

    modal: true
    focus: true
    anchors.centerIn: parent
    width: 320
    title: "Tapo hesabı"
    closePolicy: Popup.CloseOnEscape

    property string infoMessage: "Tapo cihazlarınızı bulabilmek için TP-Link hesap bilgilerinizi girin."

    function openWithMessage(message) {
        infoMessage = message
        emailField.text = ""
        passwordField.text = ""
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
            text: root.infoMessage
            color: Theme.textSecondary
            font.pixelSize: 12
            wrapMode: Text.WordWrap
            Layout.fillWidth: true
        }

        Text {
            text: "E-posta"
            color: Theme.textSecondary
            font.pixelSize: 11
        }
        TextField {
            id: emailField
            Layout.fillWidth: true
            placeholderText: "tapo-hesap@example.com"
        }

        Text {
            text: "Şifre"
            color: Theme.textSecondary
            font.pixelSize: 11
        }
        TextField {
            id: passwordField
            Layout.fillWidth: true
            echoMode: TextInput.Password
        }

        Text {
            text: "Bilgileriniz Windows Credential Locker'da saklanır, düz metin olarak hiçbir yere yazılmaz."
            color: Theme.textSecondary
            font.pixelSize: 10
            wrapMode: Text.WordWrap
            Layout.fillWidth: true
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
            enabled: emailField.text.length > 0 && passwordField.text.length > 0
            DialogButtonBox.buttonRole: DialogButtonBox.AcceptRole
        }
    }

    onAccepted: deviceModel.setTapoCredentials(emailField.text, passwordField.text)
}

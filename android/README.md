# SmartLights Android

Native Android app for local WiZ and Tapo lights. Build with Android Studio or Gradle 8.14, JDK 17 or newer and Android SDK 36. The included `app/libs/bridge.aar` is the arm64 bridge for the connected Pixel. Rebuild it with Go 1.26, Android NDK and `gomobile bind -target=android/arm64 -androidapi 26 -o ../app/libs/bridge.aar .` from `bridge/` after editing the bridge. The Go bridge uses github.com/cB-Sigma/tplink v0.4.0 (Apache-2.0) for Tapo local protocols.

Tapo credentials are encrypted with Android Keystore and backup is disabled. Discovery, commands and the widget need the phone on the same local network as the lights. Enter the Tapo account in Settings. If discovery is blocked, add a device IP manually. The widget controls all saved devices. Scenes are stored on the phone and do not sync with Windows.

This initial build cannot guarantee compatibility with every model, firmware or network. Initial bulb setup, firmware updates, desktop rooms and WiZ effects are not included.

## İki ışıklı widget

Ana ekranda widget listesinden **İki ışık kontrolü** seçin ve bir veya iki kayıtlı ışığı işaretleyin. Her ışığın parlaklığı %10, renk sıcaklığı 250 K adımlarla ayarlanır. Dişli düğmesi seçimi değiştirir. Değerler son bilinen durumdur; uygulama tarama yaptığında güncellenir. Widget ve uygulama telefonun açık/koyu temasını izler.

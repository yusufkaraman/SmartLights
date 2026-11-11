# SmartLights Android

Native Android app for local WiZ and Tapo lights. Build with Android Studio or Gradle 8.14, JDK 17 or newer and Android SDK 36. The release APK is about 9.5 MB and must stay under 50 MB. The command bar on the home screen uses Gemini Nano through AICore (ML Kit Prompt API) when the phone has it, and a Turkish keyword parser otherwise.

The included `app/libs/bridge.aar` is the arm64 Go bridge for Tapo. Rebuild it with Go 1.26, Android NDK and this command from `bridge/` after editing the bridge:

```
gomobile bind -target=android/arm64 -androidapi 26 -ldflags="-s -w" -o ../app/libs/bridge.aar .
```

The Go bridge uses github.com/cB-Sigma/tplink v0.4.0 (Apache-2.0) for Tapo local protocols. It keeps one session per bulb, so only the first request performs the handshake. Requests to the same bulb run one at a time. Failed requests are tagged `[auth]` (wrong account), `[locked]` (too many failed logins), `[net]` (no answer) or `[dev]` (the bulb rejected the request).

Tapo credentials are encrypted with Android Keystore and backup is disabled. Discovery, commands and the widget need the phone on the same local network as the lights. If discovery is blocked, add a device IP manually. The widget controls all saved devices. Scenes are stored on the phone and do not sync with Windows.

This build cannot guarantee compatibility with every model, firmware or network. Initial bulb setup, firmware updates, desktop rooms and WiZ effects are not included.

## Tüm ışıklar

Ana ekranda iki veya daha fazla ışık varsa listenin başında **Tüm ışıklar** kartı görünür. Kart, tek ışığın detay ekranıyla aynı ekranı açar: parlaklık, beyaz ton, renk, dinamik sahneler ve zamanlayıcı. Burada yapılan her değişiklik bağlı ışıkların hepsine aynı anda gönderilir. Kelvin değeri her ışığın kendi aralığına sığdırılır (Tapo 2500–6500 K, WiZ 2200–6500 K). Enerji kartı yalnızca Tapo ampullerin toplamını gösterir.

## Yumuşak geçiş

Detay ekranındaki **Zamanlayıcı** bölümünde **Yumuşak geçiş** seçilir: Anında, 1 sn veya 2 sn. Ayar tüm ışıklar içindir. Parlaklık düğmeleri, ton ve renk seçimleri, sahneler ve açma/kapama bu sürede kademeli uygulanır; kaydırıcıyı sürüklerken ışık parmağı anında izler.

- **Tapo:** açma/kapama geçişini ampulün kendi ayarı (`set_on_off_gradually_info`) yapar, bu yüzden widget ve Tapo uygulamasından açıp kapatırken de geçerlidir. Parlaklık, ton ve renk uygulama tarafından adım adım gönderilir.
- **WiZ:** ampul bu ayarı yerel ağdan kabul etmez, bu yüzden her şeyi uygulama adım adım gönderir. Kapatırken parlaklık %1'e inip ampul kapanır; açarken kayıtlı parlaklığa geri çıkar. Duvar anahtarı veya WiZ uygulamasıyla açılırsa ampul %1'de yanabilir.

## Tapo hesabı

\+ menüsündeki **Tapo hesabı** satırı hesabın durumunu gösterir: bağlı, şifre yanlış, kilitli, doğrulanmadı veya girilmedi. Pencerede son doğrulama zamanı, ampul bağlantısının durumu ve son Tapo kayıtları yer alır. Yeni hesap kaydedilmeden önce bir Tapo ampulde denenir. Ampul hesabı reddederse bilgiler kaydedilmez ve önceki çalışan hesap korunur.

## Kayıtlar

Ana ekrandaki ⓘ düğmesi kayıt ekranını açar. Tarama, ışık durumları, komutlar, Tapo oturumu, WiZ yeniden denemeleri, otomasyon ve çökmeler kaydedilir. Her kayıtta okunabilir bir Türkçe cümle ve `light=tapo:192.168.1.7 ms=116 proto=KLAP` gibi anahtar=değer alanları vardır.

- Son 4.000 kayıt `files/logs/log.jsonl` dosyasında saklanır, böylece önceki açılışların kayıtları da görünür.
- **Sorunlar** yalnızca uyarı ve hataları, **Bu açılış** yalnızca geçerli oturumu gösterir.
- **Kopyala** son 1.500 kaydı okunabilir metin olarak panoya alır.
- **Paylaş** tüm kayıtları JSON Lines dosyası olarak gönderir. İlk satır uygulama sürümünü, cihazı, Tapo hesap durumunu ve ışık listesini içerir. Tapo'nun ham cihaz bilgisi (Wi-Fi adı, konum) kayıtlara yazılmaz.

## İki ışıklı widget

Ana ekranda widget listesinden **İki ışık kontrolü** seçin ve bir veya iki kayıtlı ışığı işaretleyin. Her ışığın parlaklığı %10, renk sıcaklığı 250 K adımlarla ayarlanır. Dişli düğmesi seçimi değiştirir. Değerler son bilinen durumdur; uygulama tarama yaptığında güncellenir. Widget ve uygulama telefonun açık/koyu temasını izler.

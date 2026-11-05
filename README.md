<div align="center">

# 💡 SmartLights

**A native Windows app to control WiZ and Tapo smart lights — no cloud, no Home Assistant, just your PC and your LAN.**

[🇬🇧 English](#-english) · [🇹🇷 Türkçe](#-türkçe)

</div>

---

## 🇬🇧 English

SmartLights is a Windows desktop application (PySide6 + Qt Quick/QML) that discovers and controls **WiZ** and **Tapo** smart bulbs directly over your local network — no cloud round-trip, no third-party hub required.

### Features

- 🔍 **Automatic discovery** of WiZ (UDP broadcast) and Tapo (LAN) bulbs, matched by stable ID (MAC / device ID) so a DHCP-assigned IP change never loses track of a device
- 💡 Per-bulb control: on/off, brightness, RGB color wheel, white color temperature (Kelvin), with live updates while dragging (throttled, not laggy)
- 🏠 **Rooms** — group bulbs by room, bulk on/off per room or globally
- 🎬 **Scenes** — capture the current state of all bulbs into a named scene and re-apply it later, brand-independent
- 🖥️ System tray integration — closing the window keeps the app running in the tray; control lights from the tray menu
- 🎨 Light / Dark / System theme, following Windows' own color scheme
- 🔐 Tapo credentials are stored in the **Windows Credential Locker** (via `keyring`), never written to disk in plain text
- ⚙️ Optional autostart with Windows, with a "start minimized to tray" option
- 🛡️ Graceful error handling — Wi-Fi down, firewall blocking UDP broadcast, wrong Tapo credentials, etc. all surface as clear, actionable messages instead of silent failures or crashes
- 📱 **Android app** (~8.6 MB, in [`android/`](android/README.md)) — the same local control on the phone, an "All lights" screen that changes every bulb at once, Tapo account status, an in-app log console and home-screen widgets

### Tech stack

| Layer | Choice |
|---|---|
| UI | PySide6 (Qt Quick / QML) with the `FluentWinUI3` style |
| WiZ | [`pywizlight`](https://pypi.org/project/pywizlight/) |
| Tapo | [`tapo`](https://pypi.org/project/tapo/) |
| Credentials | `keyring` → Windows Credential Locker |
| Persistence | plain JSON files under `%APPDATA%\SmartLights\` |
| Packaging | PyInstaller (onefile) |

### Project structure

```
SmartLights/
├── main.py            # GUI entry point
├── cli.py             # console debug tool (no UI)
├── core/               # device manager, async worker, credentials, tray, settings
├── devices/            # WiZ / Tapo adapters behind a common LightController interface
├── models/             # Pydantic data models (LightDevice, Scene, ...)
└── ui/                 # QML: Main window + reusable components
```

### Running from source

```bash
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
python main.py
```

To launch the graphical application without a console window, double-click
`SmartLights.pyw`. It automatically uses the project's virtual environment.

### Desktop mini panel

Use the square-panel button in the main toolbar, the Settings dialog, or the
SmartLights system-tray menu to show the desktop mini panel. Open the panel's
gear menu to choose which lights appear. The selector at the top switches the
single control surface between those lights; power, brightness, Kelvin and the
shared color wheel always target the currently selected light.

The first launch will ask for your Tapo account email/password (only if you have Tapo devices) — for WiZ, make sure **"Allow local communication"** is enabled in the WiZ app's security settings.

### Building the .exe

```bash
.venv\Scripts\pyinstaller.exe SmartLights.spec --noconfirm
```

The resulting standalone `SmartLights.exe` is written to `dist/`.

#### Signed Windows build (required by Smart App Control)

Smart App Control does not trust an unknown unsigned executable. Build and sign
the release with an RSA code-signing certificate issued by a provider in the
Microsoft Trusted Root Program:

```powershell
.\build-signed.ps1 -CertificatePath C:\secure\codesigning.pfx
```

The certificate password is requested securely. The script validates the
certificate, builds the executable, adds an SHA-256 Authenticode signature and
timestamp, and fails unless Windows verifies the final signature as valid.
Certificates and passwords must never be committed to this repository.

---

## 🇹🇷 Türkçe

SmartLights, **WiZ** ve **Tapo** akıllı ampullerini doğrudan yerel ağınız üzerinden bulup kontrol eden yerel bir Windows masaüstü uygulamasıdır (PySide6 + Qt Quick/QML) — bulut bağlantısı ya da Home Assistant gibi ek bir katman gerektirmez.

### Özellikler

- 🔍 WiZ (UDP broadcast) ve Tapo (LAN) ampullerini **otomatik keşif** — kararlı kimlik (MAC / cihaz ID) ile eşleştirme sayesinde DHCP'nin IP değiştirmesi cihazı kaybetmez
- 💡 Ampul başına kontrol: aç/kapat, parlaklık, RGB renk çarkı, beyaz ışık sıcaklığı (Kelvin) — sürüklerken donmadan canlı güncelleme
- 🏠 **Odalar** — ampulleri odalara göre grupla, oda bazında veya global toplu aç/kapat
- 🎬 **Sahneler** — tüm ampullerin o anki durumunu isimlendirip kaydet, marka fark etmeksizin istediğin an tekrar uygula
- 🖥️ Sistem tray entegrasyonu — pencereyi kapatmak uygulamayı kapatmaz, tray'den kontrol devam eder
- 🎨 Açık / Koyu / Sistem teması, Windows'un kendi renk şemasını takip eder
- 🔐 Tapo hesap bilgileri **Windows Credential Locker**'da saklanır (`keyring` ile), asla düz metin olarak diske yazılmaz
- ⚙️ İsteğe bağlı Windows başlangıcında otomatik başlatma, "tray'de küçültülmüş başlat" seçeneğiyle birlikte
- 🛡️ Düzgün hata yönetimi — Wi-Fi yok, güvenlik duvarı UDP yayınını engelliyor, yanlış Tapo şifresi gibi durumlar sessizce başarısız olmak yerine anlaşılır, aksiyon alınabilir mesajlarla bildirilir
- 📱 **Android uygulaması** (~8.6 MB, [`android/`](android/README.md) klasöründe) — telefonda aynı yerel kontrol, tüm ampulleri birden değiştiren "Tüm ışıklar" ekranı, Tapo hesap durumu, uygulama içi kayıt konsolu ve ana ekran widget'ları

### Teknoloji seçimleri

| Katman | Seçim |
|---|---|
| Arayüz | PySide6 (Qt Quick / QML), `FluentWinUI3` stili |
| WiZ | [`pywizlight`](https://pypi.org/project/pywizlight/) |
| Tapo | [`tapo`](https://pypi.org/project/tapo/) |
| Kimlik bilgisi | `keyring` → Windows Credential Locker |
| Kalıcı veri | `%APPDATA%\SmartLights\` altında düz JSON dosyaları |
| Paketleme | PyInstaller (tek dosya) |

### Kaynaktan çalıştırma

```bash
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
python main.py
```

Grafik uygulamasını konsol penceresi olmadan başlatmak için `SmartLights.pyw`
dosyasına çift tıklayın. Dosya otomatik olarak projenin sanal ortamını kullanır.

### Masaüstü mini paneli

Mini paneli ana araç çubuğundaki kare panel düğmesinden, Ayarlar penceresinden
veya SmartLights sistem tepsisi menüsünden açabilirsiniz. Paneldeki dişli
menüsünden gösterilecek ışıkları seçin. Üstteki seçim kutusu tek kontrol alanını
bu ışıklar arasında değiştirir; açma/kapatma, parlaklık, Kelvin ve ortak renk
çarkı her zaman o anda seçili olan ışığı kontrol eder.

İlk açılışta (yalnızca Tapo cihazınız varsa) Tapo hesap bilgilerinizi soracak — WiZ tarafında ise WiZ uygulamasının güvenlik ayarlarında **"Allow local communication"** seçeneğinin açık olması gerekir.

### `.exe` oluşturma

```bash
.venv\Scripts\pyinstaller.exe SmartLights.spec --noconfirm
```

Oluşan tek dosyalık `SmartLights.exe`, `dist/` klasörüne yazılır.

#### İmzalı Windows derlemesi (Smart App Control için gerekli)

Smart App Control, tanınmayan ve imzasız bir çalıştırılabilir dosyaya güvenmez.
Dağıtım sürümünü Microsoft Trusted Root Program kapsamındaki bir sağlayıcıdan
alınmış RSA kod imzalama sertifikasıyla derleyip imzalayın:

```powershell
.\build-signed.ps1 -CertificatePath C:\guvenli\kod-imzalama.pfx
```

Sertifika parolası güvenli biçimde sorulur. Betik sertifikayı denetler, uygulamayı
derler, SHA-256 Authenticode imzası ve zaman damgası ekler ve Windows son imzayı
geçerli olarak doğrulamadıkça başarısız olur. Sertifika ve parola kesinlikle bu
depoya eklenmemelidir.

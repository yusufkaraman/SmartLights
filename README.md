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

The first launch will ask for your Tapo account email/password (only if you have Tapo devices) — for WiZ, make sure **"Allow local communication"** is enabled in the WiZ app's security settings.

### Building the .exe

```bash
.venv\Scripts\pyinstaller.exe SmartLights.spec --noconfirm
```

The resulting standalone `SmartLights.exe` is written to `dist/`.

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

İlk açılışta (yalnızca Tapo cihazınız varsa) Tapo hesap bilgilerinizi soracak — WiZ tarafında ise WiZ uygulamasının güvenlik ayarlarında **"Allow local communication"** seçeneğinin açık olması gerekir.

### `.exe` oluşturma

```bash
.venv\Scripts\pyinstaller.exe SmartLights.spec --noconfirm
```

Oluşan tek dosyalık `SmartLights.exe`, `dist/` klasörüne yazılır.

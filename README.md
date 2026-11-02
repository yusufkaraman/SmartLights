<div align="center">

# 💡 SmartLights

**Local Network Controller for WiZ and Tapo Smart Bulbs — PC (Windows) & Android**
*Kontrolkera Tora Herêmî ji bo Çirayên Aqilmend ên WiZ û Tapo — PC (Windows) & Android*
*WiZ ve Tapo Akıllı Ampuller İçin Yerel Ağ Kontrolcüsü — PC (Windows) ve Android*

[English](#english) | [Türkçe](#türkçe) | [Kurdî](#kurdî)

</div>

---

<div id="english"></div>

# 🇬🇧 English

SmartLights is an open-source local-network application developed for Windows (PC) and Android to discover and control **WiZ** and **TP-Link Tapo** smart lighting devices directly without cloud servers, external bridges, or home automation hubs.

> **Disclaimer:** SmartLights is an independent community project. It is not affiliated with, endorsed by, or an official product of WiZ Connected, Signify, or TP-Link Tapo.

---

### 1. About the Project
SmartLights provides unified control over smart lights from different manufacturers on your local Wi-Fi / Ethernet network. By communicating directly with bulb hardware using native network protocols, it eliminates cloud latency, prevents telemetry tracking, and keeps your lighting controls fully operational even if your internet connection goes offline.

---

### 2. Features

- 🔍 **Automatic Device Discovery:** Scans the local network via UDP broadcast to detect WiZ bulbs and Tapo devices. Devices are identified and tracked by persistent hardware IDs (MAC address for WiZ, `device_id` for Tapo) so that router IP reassignment (DHCP) never breaks connectivity.
- ➕ **Manual Device Addition:** On Android, lights can be added directly by specifying their local IP address if broadcast discovery is blocked by network isolation or AP client filtering.
- ⚡ **Instant On / Off & Optimistic UI:** State changes are reflected instantly in the user interface and dispatched asynchronously in the background. If a device fails to respond, the UI cleanly rolls back and alerts the user.
- 🔆 **Brightness Control:** Continuous smooth dimming control (1% to 100%) with network request throttling/debouncing to prevent packet flooding.
- 🎨 **RGB Color Wheel & Presets:** Full 360° color wheel, preset color palettes, and saturation control.
- 🌡️ **Color Temperature (Kelvin):** Dynamic white spectrum slider calibrated to each bulb's actual hardware range (e.g., 2200K–6500K for WiZ, 2500K–6500K for Tapo).
- 🖼️ **Photo Color Extractor (Android):** Pick any photo from your gallery to automatically extract dominant color swatches and apply them to your lights.
- 💫 **Dynamic Scenes & Effects:**
  - *WiZ:* Native hardware effects and built-in animation scenes.
  - *Tapo:* Static fallback rendering matching the scene's primary tone.
- 🏠 **Grouping & Rooms (PC):** Organize lights into rooms; execute synchronized room-level or global "All On" / "All Off" commands in parallel using `asyncio.gather`.
- 🎬 **Custom Saved Scenes:** Capture the real-time state of all active bulbs (power, brightness, color/Kelvin) into a saved profile and recall it with one tap.
- 🖥️ **Desktop Mini Panel & System Tray (PC):**
  - Compact floating desktop mini-panel with quick-switcher for pinned lights.
  - System tray icon with background minimize and quick toggle actions.
- 📱 **Android Widgets & Quick Settings:**
  - Home screen app widgets: All Lights (`LightsWidget`), Dual Lights (`DualLightsWidget`), Single Light (`SingleLightWidget`), Scene Strip (`ScenesWidget`), and Room Panel (`RoomWidget`).
  - Quick Settings pull-down tile (`LightsTile`) for toggling all lights from anywhere in the OS.
- ⏰ **Timers & Circadian Rhythm (Android):**
  - Sleep / turn-off timers (15 min, 30 min, 1 hr).
  - Background Circadian Rhythm scheduler adjusting white temperature throughout the day (warm at morning/evening, crisp cool at midday).
- 📊 **Energy & Diagnostic Analytics (Android):**
  - Real-time Wi-Fi signal strength (RSSI in dBm) and ping response latency test.
  - Device energy consumption stats (runtime, kWh consumed, and power saved) for supported Tapo models.

---

### 3. Compatibility & Platform Comparison

#### Bulb Brand Support
- **WiZ:** All WiZ Connected Wi-Fi bulbs, lamps, and downlights supporting the local UDP JSON protocol on port `38899`.
- **TP-Link Tapo:** Supported models include color bulbs (**L530, L535, L630**), RGB light strips, and dimmable white models (**L510, L520, L610**). Non-lighting devices (plugs, smart cameras) are filtered out.

> **Note:** Initial Wi-Fi provisioning and firmware updates must be completed through the official WiZ or Tapo mobile apps. SmartLights manages daily local operations.

#### Platform Feature Comparison

| Feature | Windows PC | Android |
|---|---|---|
| **Programming Language / UI** | Python 3.13 + PySide6 (Qt Quick / QML) | Kotlin + Jetpack Compose (Material 3 Expressive) |
| **Local Discovery** | Automatic (UDP Broadcast) | Automatic + Manual IP input |
| **WiZ Communication** | Native Python (`pywizlight`) | Native UDP Datagram Socket (Port 38899) |
| **Tapo Communication** | Native Python (`tapo` library) | Go Bridge (`bridge.aar` using `cB-Sigma/tplink`) |
| **Credentials Security** | Windows Credential Locker (`keyring`) | Android Keystore (AES-GCM-256 encrypted) |
| **Power / Brightness / Kelvin** | ✅ Full support | ✅ Full support |
| **Color Wheel & Swatches** | ✅ Full support | ✅ Full support + Photo color picker |
| **Dynamic Vendor Effects** | ✅ WiZ hardware effects | ✅ WiZ effects (Tapo static fallback) |
| **Rooms & Grouping** | ✅ Configurable rooms & bulk controls | ⚠️ Saved list & favorites (No desktop room sync) |
| **Custom Scenes** | ✅ JSON stored (`%APPDATA%`) | ✅ Local phone storage (`SharedPreferences`) |
| **Quick Access Utilities** | Desktop Mini Panel + System Tray | 5 Home Screen Widgets + Quick Settings Tile |
| **Timers & Automations** | ❌ Not available | ✅ Sleep timer & Circadian Rhythm |
| **Diagnostics & Energy** | ❌ Basic status | ✅ Ping / RSSI test + Tapo energy stats |

---

### 4. Screens and Usage Flow

#### Windows PC Application
1. **Home Screen:** Displays room groupings, active scene chips, global power controls, and interactive bulb cards.
2. **Device Control Panel:** Clicking a bulb card reveals a sliding panel with tabs for **Color** (wheel + HEX/RGB inputs), **White** (Kelvin slider + temperature presets), and **Effects** (vendor effects).
3. **Mini Panel:** Click the square panel icon in the main toolbar or tray to summon a compact, floating light switcher window.
4. **Settings Dialog:** Configure application startup behavior ("Launch with Windows", "Start minimized to tray"), change theme (Light / Dark / Follow System), or manage Tapo account credentials.

#### Android Application
1. **Main Feed:** Displays the lights list with smooth animated level bands, favorites, global toggles, and a horizontal carousel of ready & custom scenes. Pull down to trigger a local network refresh.
2. **Detail Sheet:** Tap any card to access brightness sliders, 12 curated color swatches, photo color sampling, and tabs for:
   - **White:** Kelvin slider with Warm / Natural / Daylight presets.
   - **Dynamic:** Effect presets.
   - **Timer:** Sleep timers (15 min, 30 min, 1 hour).
   - **Auto:** Circadian rhythm scheduling.
   - **Energy:** Power consumption analytics (Tapo).
   - **Connection:** Live latency and RSSI diagnostic test.
3. **Add Light / Management Menu:** Use the bottom Floating Action Button (FAB) to:
   - Add a device manually via IP address.
   - Save the current lighting state as a new scene.
   - Configure Tapo login credentials.
   - Restore hidden devices.

---

### 5. Connection Architecture & Network Protocols

```
┌─────────────────────────────────────────────────────────────┐
│                     Local Wi-Fi Network                     │
│                                                             │
│   Windows PC (SmartLights)          Android (SmartLights)   │
│   ├── pywizlight (UDP)              ├── UDP Socket          │
│   └── tapo library (TCP/HTTP)       └── Go Bridge (TCP/HTTP)│
│              │                               │              │
└──────────────┼───────────────────────────────┼──────────────┘
               ▼                               ▼
     ┌───────────────────┐           ┌───────────────────┐
     │  WiZ Smart Bulbs  │           │ Tapo Smart Bulbs  │
     │  UDP Port 38899   │           │ KLAP / Local HTTP │
     │  No auth required │           │ Local device auth │
     └───────────────────┘           └───────────────────┘
```

- **WiZ Protocol:** Direct UDP datagram packets on port `38899` using JSON payloads (`getSystemConfig`, `getPilot`, `setPilot`). No username, password, or cloud token is needed.
- **Tapo Protocol:** Secure local network connection using TP-Link's local protocol (KLAP / AES handshake over HTTP). Requires your TP-Link Tapo cloud account email and password solely to generate the local authentication session. Communication stays 100% within your local subnet.
- **Internet Requirement:** SmartLights functions without an active internet connection once installed. Devices and the host computer/phone must be connected to the **same Wi-Fi subnet**.
- **Important Device Settings:**
  - WiZ: Enable **"Allow local communication"** in the WiZ mobile app under *Settings → Security*.
  - Router: Ensure client isolation / AP isolation is **disabled** so local devices can communicate with each other.

---

### 6. Technical Architecture

#### Codebase Layout
```
SmartLights/
├── main.py                  # Windows entry point (PySide6 QML application)
├── cli.py                   # Console debugging tool for discovery and control
├── SmartLights.pyw          # Headless GUI launcher (no console window)
├── build-signed.ps1         # Windows code-signing release build script
├── core/                    # Core backend logic
│   ├── async_worker.py      # Background asyncio worker thread for non-blocking I/O
│   ├── config_store.py      # Atomic JSON persistence (config.json, cache)
│   ├── credentials.py       # Secure credential management via keyring
│   ├── device_manager.py    # QAbstractListModel, optimistic updates, and group actions
│   ├── autostart.py         # Windows registry autostart integration
│   └── tray.py              # System tray icon and tray context menu
├── devices/                 # Common LightController interface adapters
│   ├── base.py              # Abstract LightController base class
│   ├── wiz_adapter.py       # WiZ adapter implementation via pywizlight
│   └── tapo_adapter.py      # Tapo adapter implementation via tapo-py
├── models/                  # Pydantic schemas (LightDevice, Capabilities, Scene)
├── ui/                      # Qt Quick / QML frontend
│   ├── Main.qml             # Main dashboard window
│   ├── components/          # Reusable components (LightCard, ColorWheel, DevicePanel, etc.)
│   └── theme/               # FluentWinUI3 palette & theme styling
└── android/                 # Android Native App
    ├── bridge/              # Go bridge wrapper for Tapo protocol (cB-Sigma/tplink)
    └── app/                 # Kotlin Android application with Jetpack Compose
```

---

### 7. Installation and Setup

#### Windows PC

##### Running from Source
1. **Prerequisites:** Python 3.11+ (Python 3.13 recommended) and Git.
2. Clone repository and create a virtual environment:
   ```bash
   git clone <repo_url>
   cd SmartLights
   python -m venv .venv
   .venv\Scripts\activate
   pip install -r requirements.txt
   ```
3. Run the application:
   ```bash
   python main.py
   # Or launch without terminal:
   pythonw SmartLights.pyw
   ```

##### Building the Standalone Executable
- Generate single-file `dist/SmartLights.exe`:
  ```bash
  .venv\Scripts\pyinstaller.exe SmartLights.spec --noconfirm
  ```
- To build and sign for Windows Smart App Control:
  ```powershell
  .\build-signed.ps1 -CertificatePath C:\path\to\codesigning.pfx
  ```

---

#### Android

##### Building from Source
1. **Prerequisites:** JDK 17+, Android SDK 36, and Gradle 8.13+.
2. **Pre-built Bridge:** The repository includes a pre-built arm64 bridge artifact at `android/app/libs/bridge.aar`.
3. **Build APK via Gradle:**
   ```bash
   cd android
   ./gradlew assembleRelease
   ```
   The built APK will be located under `android/app/build/outputs/apk/release/`.

##### (Optional) Recompiling the Go Bridge
To modify `android/bridge/bridge.go` and recompile `bridge.aar`:
1. Install Go 1.26+ and Android NDK.
2. Install `gomobile`:
   ```bash
   go install golang.org/x/mobile/cmd/gomobile@latest
   gomobile init
   ```
3. Compile the AAR from `android/bridge`:
   ```bash
   cd android/bridge
   gomobile bind -target=android/arm64 -androidapi 26 -o ../app/libs/bridge.aar .
   ```

---

### 8. Configuration & Permissions

#### Windows Configuration
- **Application Data:** Stored in `%APPDATA%\SmartLights\` (`config.json` for custom names, rooms, and scenes; `devices_cache.json` for device cache).
- **Credentials:** Tapo email and password are stored securely in **Windows Credential Locker** using the Python `keyring` package. Credentials are never written to plain-text files.

#### Android Configuration & Permissions
- **Encrypted Keystore:** Tapo credentials are encrypted using AES-GCM-256 via the hardware-backed `AndroidKeyStore` (`Store.kt`) and stored in private SharedPreferences.
- **App Permissions (`AndroidManifest.xml`):**
  - `android.permission.INTERNET`: Local socket communication.
  - `android.permission.ACCESS_NETWORK_STATE` & `ACCESS_WIFI_STATE`: Network interface inspection.
  - `android.permission.CHANGE_WIFI_MULTICAST_STATE`: Sending UDP discovery broadcasts.
  - `android.permission.RECEIVE_BOOT_COMPLETED`: Rescheduling circadian alarms after phone reboot.

---

### 9. Troubleshooting

- **Bulbs Not Discovered Automatically:**
  - Ensure the PC or phone is connected to the exact same Wi-Fi network / subnet as your lights (2.4 GHz band).
  - Verify that your Wi-Fi router does not have "AP Isolation" or "Guest Network Isolation" enabled.
  - On Windows, verify that Windows Defender Firewall is not blocking UDP broadcast packets.
  - On Android, use the **"+" → "IP ile ışık ekle"** menu option to add the bulb directly by its local IP.
- **WiZ Bulbs Not Responding:**
  - Open the official WiZ smartphone app, navigate to **Settings → Security**, and ensure **"Allow local communication"** is toggled ON.
- **Tapo Authentication Error:**
  - Ensure your TP-Link Tapo cloud email and password are typed correctly. If changed recently, open Settings and re-enter them.
  - Ensure the Tapo device has completed initial setup and is reachable over LAN.
- **App Lag or Unresponsive Devices:**
  - DHCP may have assigned a new IP while a device was asleep. Click the refresh button / pull down on Android to refresh network mappings.

---

### 10. Development Status & License

- **Current Implementation Status:**
  - ✅ Working: Full local discovery, manual IP addition (Android), power, brightness, RGB, Kelvin, custom scenes, rooms (PC), desktop mini panel, Android widgets, sleep timer, and circadian scheduler.
  - ⚠️ Partial: Tapo dynamic scenes emulate effects by falling back to the primary scene color; Android scenes are stored locally and do not sync with PC.
  - ⏳ In Development / Future: Custom effect creation, voice integration, and sunrise/sunset triggers.
- **Third-Party Component Licenses:**
  - `tplink` Go library: [Apache-2.0 License](https://github.com/cB-Sigma/tplink)
  - `pywizlight`: MIT License
  - `tapo` Python library: MIT / Apache-2.0
- **Repository License:** No explicit top-level license file is currently provided. All rights reserved by the original authors unless stated otherwise.

---

<div id="türkçe"></div>

# 🇹🇷 Türkçe

SmartLights, **WiZ** ve **TP-Link Tapo** akıllı aydınlatma cihazlarını bulut sunucularına, harici bir köprüye veya ev otomasyon hub'ına ihtiyaç duymadan doğrudan yerel ağınız üzerinden bulup kontrol eden, Windows (PC) ve Android için geliştirilmiş açık kaynaklı bir uygulamadır.

> **Yasal Uyarı:** SmartLights bağımsız bir topluluk projesidir. WiZ Connected, Signify veya TP-Link Tapo'nun resmi bir uygulaması veya onaylı bir ürünü değildir.

---

### 1. Proje Tanıtımı
SmartLights, farklı markalara ait akıllı ampulleri yerel Wi-Fi / Ethernet ağınızda tek bir çatı altında toplar. Doğrudan ampullerin donanım protokolleriyle konuştuğu için bulut gecikmesini ortadan kaldırır, veri takibini engeller ve internet bağlantınız kesilse dahi lambalarınızı kesintisiz kontrol etmenizi sağlar.

---

### 2. Özellikler

- 🔍 **Otomatik Cihaz Keşfi:** Yerel ağda UDP yayını (broadcast) yaparak WiZ ve Tapo cihazlarını otomatik bulur. Cihazları kararlı donanım kimlikleriyle (WiZ için MAC adresi, Tapo için `device_id`) takip eder; bu sayede modemin DHCP üzerinden IP değiştirmesi cihaz bağlantısını koparmaz.
- ➕ **Manuel IP ile Cihaz Ekleme:** Android uygulamasında, ağ yalıtımı veya misafir ağı kısıtlamaları nedeniyle otomatik keşif engellendiğinde ampulün IP adresi elle girilerek eklenebilir.
- ⚡ **Anında Açma / Kapatma ve İyimser Arayüz (Optimistic UI):** Butona basıldığında arayüz anında güncellenir, komut arka planda asenkron olarak gönderilir. Cihaz yanıt vermezse arayüz eski durumuna geri döner ve kullanıcı uyarılır.
- 🔆 **Parlaklık Kontrolü:** Ağ paket trafiğini şişirmeyen yumuşak filtreleme (throttling/debouncing) mekanizması ile %1'den %100'e kesintisiz parlaklık ayarı.
- 🎨 **RGB Renk Çarkı ve Hazır Renkler:** 360 derecelik renk çarkı, hazır renk paleti ve doygunluk ayarı.
- 🌡️ **Renk Sıcaklığı (Kelvin):** Cihazın gerçek donanım sınırlarına göre dinamik olarak ayarlanan beyaz ışık sürgüsü (örn. WiZ için 2200K–6500K, Tapo için 2500K–6500K).
- 🖼️ **Fotoğraftan Renk Çıkarma (Android):** Galerinizden seçeceğiniz herhangi bir fotoğraftaki baskın renk tonlarını çıkarıp doğrudan ışıklara uygulama.
- 💫 **Dinamik Sahneler ve Efektler:**
  - *WiZ:* Donanımın yerleşik animasyon efektlerini ve hazır sahnelerini çalıştırma.
  - *Tapo:* Efektler, sahnenin ana rengi baz alınarak sabit ışık şeklinde uygulanır.
- 🏠 **Odalar ve Gruplama (PC):** Lambaları odalara ayırma; oda düzeyinde veya tek tıkla tüm ışıkları `asyncio.gather` ile paralel olarak açıp kapatma.
- 🎬 **Kayıtlı Özel Sahneler:** Tüm lambaların o anki durumunu (açık/kapalı, parlaklık, renk veya Kelvin) isimlendirip tek tıkla geri yükleyebilme.
- 🖥️ **Masaüstü Mini Paneli ve Sistem Tepsisi (PC):**
  - Seçili ışıkları hızlıca değiştirebileceğiniz kompakt kayan masaüstü mini kontrol paneli.
  - Arka planda sistem tepsisinde (tray) çalışma ve sağ tık menüsünden hızlı kontrol.
- 📱 **Android Widget'ları ve Hızlı Ayarlar:**
  - 5 farklı ana ekran widget'ı: Tüm Işıklar (`LightsWidget`), İki Işık (`DualLightsWidget`), Tek Işık (`SingleLightWidget`), Sahne Şeridi (`ScenesWidget`) ve Oda Paneli (`RoomWidget`).
  - Bildirim panelinden tek dokunuşla tüm ışıkları açıp kapatabilen Hızlı Ayarlar kutucuğu (`LightsTile`).
- ⏰ **Zamanlayıcı ve Sirkadiyen Ritim (Android):**
  - Kapanma zamanlayıcısı (15 dk, 30 dk, 1 saat).
  - Günün saatine göre beyaz ışık sıcaklığını otomatik ayarlayan Sirkadiyen Ritim desteği (sabah ve akşam sıcak/sarı, öğleyin soğuk/canlı).
- 📊 **Enerji ve Teşhis Analitiği (Android):**
  - Canlı Wi-Fi sinyal gücü (dBm cinsinden RSSI) ve ping gecikme testi.
  - Desteklenen Tapo modellerinde çalışma süresi, tüketilen enerji (kWh) ve tasarruf verileri.

---

### 3. Uyumluluk ve Platform Karşılaştırması

#### Marka ve Model Desteği
- **WiZ:** Port `38899` üzerinden yerel UDP JSON protokolünü destekleyen tüm WiZ Wi-Fi ampul, lamba ve spotları.
- **TP-Link Tapo:** Renkli ampuller (**L530, L535, L630**), RGB ışık şeritleri ve kısılabilir beyaz modeller (**L510, L520, L610**). Işık dışındaki cihazlar (akıllı prizler, kameralar) filtrelenir.

> **Not:** Ampullerin ilk Wi-Fi kurulumu ve donanım yazılımı güncellemeleri resmi WiZ veya Tapo mobil uygulaması üzerinden yapılmalıdır. SmartLights günlük yerel yönetim içindir.

#### Platform Karşılaştırma Tablosu

| Özellik | Windows PC | Android |
|---|---|---|
| **Programlama Dili / UI** | Python 3.13 + PySide6 (Qt Quick / QML) | Kotlin + Jetpack Compose (Material 3 Expressive) |
| **Yerel Keşif** | Otomatik (UDP Broadcast) | Otomatik + Elle IP Ekleme |
| **WiZ İletişimi** | Yerel Python (`pywizlight`) | Yerel UDP Datagram Soketi (Port 38899) |
| **Tapo İletişimi** | Yerel Python (`tapo` kütüphanesi) | Go Köprüsü (`bridge.aar`, `cB-Sigma/tplink`) |
| **Kimlik Güvenliği** | Windows Credential Locker (`keyring`) | Android Keystore (AES-GCM-256 şifreli) |
| **Güç / Parlaklık / Kelvin** | ✅ Tam destek | ✅ Tam destek |
| **Renk Çarkı ve Palet** | ✅ Tam destek | ✅ Tam destek + Fotoğraftan renk alma |
| **Üretici Efektleri** | ✅ WiZ donanım efektleri | ✅ WiZ efektleri (Tapo'da statik renk) |
| **Odalar ve Gruplama** | ✅ Özelleştirilebilir odalar ve toplu işlem | ⚠️ Liste ve favoriler (PC odaları senkronize olmaz) |
| **Özel Sahneler** | ✅ JSON dosyasında saklanır (`%APPDATA%`) | ✅ Telefonda saklanır (`SharedPreferences`) |
| **Hızlı Erişim Araçları** | Masaüstü Mini Paneli + Sistem Tepsisi | 5 Ana Ekran Widget'ı + Hızlı Ayar Kutucuğu |
| **Zamanlayıcı & Otomasyon** | ❌ Bulunmuyor | ✅ Kapanma zamanlayıcısı & Sirkadiyen Ritim |
| **Bağlantı Teşhisi & Enerji** | ❌ Temel durum | ✅ Ping / RSSI testi + Tapo enerji analizi |

---

### 4. Ekranlar ve Kullanım Akışları

#### Windows PC Uygulaması
1. **Ana Ekran:** Oda gruplarını, hızlı sahne düğmelerini, genel güç butonlarını ve lamba kartlarını listeler.
2. **Cihaz Kontrol Paneli:** Bir lambanın kartına tıklandığında sağdan açılan panelde **Renk** (çark ve HEX/RGB değerleri), **Beyaz** (Kelvin sürgüsü ve hazır sıcaklıklar) ve **Efekt** sekmeleri yer alır.
3. **Mini Panel:** Üst araç çubuğundaki kare panel simgesine veya sistem tepsisi menüsüne tıklayarak ekranda sabit duran kompakt denetleyiciyi açabilirsiniz.
4. **Ayarlar:** Windows başlangıcında otomatik açılma, sistem tepsisinde küçültülmüş başlama, açık/koyu tema seçimi ve Tapo hesap bilgilerini yönetme imkânı sunar.

#### Android Uygulaması
1. **Ana Liste:** Işık kartlarını canlı parlaklık şeridiyle gösterir, favorileri ve hazır/özel sahneleri yatay kaydırılabilir şeritle sunar. Aşağı çekerek ağ taraması yenilenir.
2. **Ayrıntı Sayfası:** Bir karta tıklandığında parlaklık sürgüsü, 12 hazır renk paleti, fotoğraftan renk seçme ve alt sekmeler açılır:
   - **Beyaz:** Kelvin sürgüsü ve Sıcak / Doğal / Gün Işığı seçenekleri.
   - **Dinamik:** Efekt listesi.
   - **Zamanlayıcı:** Kapanma geri sayımı (15, 30, 60 dakika).
   - **Otomasyon:** Sirkadiyen ritim açma/kapatma.
   - **Enerji:** Tapo tüketim istatistikleri.
   - **Bağlantı:** Anlık ping ve sinyal testi.
3. **Ekleme Menüsü:** Sağ alttaki yüzen düğme (+) ile IP adresiyle ışık ekleme, sahne kaydetme, Tapo hesabı tanımlama ve gizlenen ışıkları geri getirme işlemleri yapılır.

---

### 5. Bağlantı Yöntemi ve Protokoller

```
┌─────────────────────────────────────────────────────────────┐
│                       Yerel Wi-Fi Ağı                       │
│                                                             │
│   Windows PC (SmartLights)          Android (SmartLights)   │
│   ├── pywizlight (UDP)              ├── UDP Soket           │
│   └── tapo kütüphanesi (TCP/HTTP)   └── Go Köprüsü (TCP/HTTP)│
│              │                               │              │
└──────────────┼───────────────────────────────┼──────────────┘
               ▼                               ▼
     ┌───────────────────┐           ┌───────────────────┐
     │  WiZ Akıllı Lamba │           │ Tapo Akıllı Lamba │
     │  UDP Port 38899   │           │ KLAP / Yerel HTTP │
     │  Şifre istemez    │           │ Yerel cihaz girişi│
     └───────────────────┘           └───────────────────┘
```

- **WiZ Protokolü:** JSON yükleri taşıyan doğrudan UDP datagram paketleri kullanılır (port `38899`). Hesap, parola ya da bulut anahtarı gerekmez.
- **Tapo Protokolü:** TP-Link'in yerel protokolü (KLAP / HTTP üzerinde AES anlaşması) ile iletişim kurulur. Tapo e-posta ve şifreniz yalnızca lamba donanımı üzerinde yerel oturum anahtarı üretmek için kullanılır. Trafik tamamen yerel ağda kalır.
- **İnternet İhtiyacı:** Kurulum tamamlandıktan sonra uygulamanın çalışması için internet bağlantısı gerekmez. Cihazlar ile bilgisayar/telefonun **aynı Wi-Fi ağında (alt ağ)** olması yeterlidir.
- **Gerekli Ayarlar:**
  - WiZ: WiZ mobil uygulamasında *Ayarlar → Güvenlik* altından **"Allow local communication"** (Yerel iletişime izin ver) açık olmalıdır.
  - Modem: Cihazların birbirini görebilmesi için modemde "İstemci Yalıtımı" (AP/Client Isolation) **kapalı** olmalıdır.

---

### 6. Teknik Yapı ve Klasör Düzeni

```
SmartLights/
├── main.py                  # PC grafik arayüz başlatıcısı (PySide6 QML)
├── cli.py                   # Konsoldan tarama ve lamba test aracı
├── SmartLights.pyw          # Konsol penceresi olmadan başlatan Windows betiği
├── build-signed.ps1         # Sertifikalı Windows sürüm derleme betiği
├── core/                    # Arka uç iş mantığı
│   ├── async_worker.py      # UI'ı kilitlemeyen asyncio iş parçacığı
│   ├── config_store.py      # Güvenli atomik JSON kayıt mekanizması
│   ├── credentials.py       # keyring ile şifreli parola yönetimi
│   ├── device_manager.py    # QAbstractListModel, iyimser UI ve toplu komutlar
│   ├── autostart.py         # Windows başlangıç kayıt defteri yönetimi
│   └── tray.py              # Sistem tepsisi ve sağ tık menüsü
├── devices/                 # LightController arayüzünü uygulayan sürücüler
│   ├── base.py              # Temel LightController soyut sınıfı
│   ├── wiz_adapter.py       # pywizlight tabanlı WiZ adaptörü
│   └── tapo_adapter.py      # tapo tabanlı Tapo adaptörü
├── models/                  # Pydantic veri modelleri (LightDevice, Capabilities, Scene)
├── ui/                      # Qt Quick / QML arayüz dosyaları
│   ├── Main.qml             # Ana kontrol penceresi
│   ├── components/          # LightCard, ColorWheel, DevicePanel vb. bileşenler
│   └── theme/               # FluentWinUI3 tema tanımları
└── android/                 # Android Yerel Uygulama
    ├── bridge/              # Go tabanlı Tapo protokol köprüsü (cB-Sigma/tplink)
    └── app/                 # Jetpack Compose ile yazılmış Kotlin uygulaması
```

---

### 7. Kurulum ve Çalıştırma

#### Windows PC

##### Kaynak Koddan Çalıştırma
1. **Gereksinimler:** Python 3.11+ (Python 3.13 önerilir) ve Git.
2. Depoyu klonlayıp sanal ortamı kurun:
   ```bash
   git clone <repo_url>
   cd SmartLights
   python -m venv .venv
   .venv\Scripts\activate
   pip install -r requirements.txt
   ```
3. Uygulamayı başlatın:
   ```bash
   python main.py
   # veya konsol penceresiz:
   pythonw SmartLights.pyw
   ```

##### Tek Dosya `.exe` Derleme
- Bağımsız `dist/SmartLights.exe` oluşturmak için:
  ```bash
  .venv\Scripts\pyinstaller.exe SmartLights.spec --noconfirm
  ```
- Windows Smart App Control için sertifikalı derleme:
  ```powershell
  .\build-signed.ps1 -CertificatePath C:\guvenli\sertifika.pfx
  ```

---

#### Android

##### Kaynak Koddan Derleme
1. **Gereksinimler:** JDK 17+, Android SDK 36 ve Gradle 8.13+.
2. **Hazır Köprü Kütüphanesi:** Projede derlenmiş arm64 köprüsü `android/app/libs/bridge.aar` olarak mevcuttur.
3. **APK Derleme:**
   ```bash
   cd android
   ./gradlew assembleRelease
   ```
   Oluşan APK `android/app/build/outputs/apk/release/` konumunda yer alır.

##### (İsteğe Bağlı) Go Köprüsünü Yeniden Derleme
`android/bridge/bridge.go` dosyasında değişiklik yapıldığında:
1. Go 1.26+ ve Android NDK kurun.
2. `gomobile` aracını yükleyin:
   ```bash
   go install golang.org/x/mobile/cmd/gomobile@latest
   gomobile init
   ```
3. `android/bridge` dizininde AAR çıktısı üretin:
   ```bash
   cd android/bridge
   gomobile bind -target=android/arm64 -androidapi 26 -o ../app/libs/bridge.aar .
   ```

---

### 8. Yapılandırma ve İzinler

#### Windows Ayarları
- **Veri Yolu:** `%APPDATA%\SmartLights\` altında (`config.json` oda/sahne isimleri için, `devices_cache.json` son cihaz listesi için).
- **Kimlik Saklama:** Tapo e-posta ve parolası Python `keyring` kütüphanesi aracılığıyla doğrudan **Windows Credential Locker** üzerinde güvenle tutulur; diske düz metin olarak asla kaydedilmez.

#### Android Ayarları ve İzinleri
- **Şifreli Depolama:** Tapo hesap bilgileri `AndroidKeyStore` donanım anahtarı ve AES-GCM-256 ile şifrelenerek SharedPreferences içinde tutulur.
- **Manifest İzinleri (`AndroidManifest.xml`):**
  - `INTERNET`: Yerel soket iletişimi.
  - `ACCESS_NETWORK_STATE` & `ACCESS_WIFI_STATE`: Ağ bağlantı durumu denetimi.
  - `CHANGE_WIFI_MULTICAST_STATE`: UDP keşif paketleri gönderebilme.
  - `RECEIVE_BOOT_COMPLETED`: Cihaz yeniden başladığında sirkadiyen ritim alarmını sürdürme.

---

### 9. Sorun Giderme

- **Cihazlar Otomatik Bulunamıyor:**
  - Bilgisayar veya telefonun lambalarla **aynı Wi-Fi ağında (2.4 GHz)** olduğundan emin olun.
  - Modeminizde "İstemci Yalıtımı" (AP/Client Isolation) özelliğinin kapalı olduğundan emin olun.
  - Windows Güvenlik Duvarı'nın UDP yayınlarını engellemediğini doğrulayın.
  - Android'de keşif çalışmazsa sağ alttaki **"+" → "IP ile ışık ekle"** seçeneğini kullanarak lamba IP'sini doğrudan girin.
- **WiZ Lambaları Tepki Vermiyor:**
  - Resmi WiZ uygulamasını açıp **Ayarlar → Güvenlik** bölümündeki **"Allow local communication"** (Yerel iletişime izin ver) seçeneğinin açık olduğunu kontrol edin.
- **Tapo Kimlik Hatası:**
  - TP-Link Tapo hesap e-posta veya şifrenizin doğruluğundan emin olun. Değiştirdiyseniz Ayarlar menüsünden yeniden girin.
- **Bağlantı Gecikmesi veya Çevrimdışı Lambalar:**
  - Router yeniden başladığında IP değişmiş olabilir. PC'de yenile butonuna basın veya Android'de listeyi aşağı kaydırarak yeniden tarama yapın.

---

### 10. Geliştirme Durumu ve Lisans

- **Mevcut Geliştirme Durumu:**
  - ✅ Çalışan: Yerel keşif, elle IP ekleme (Android), açma/kapatma, parlaklık, RGB, Kelvin, özel sahneler, odalar (PC), masaüstü mini paneli, Android widget'ları, kapanma zamanlayıcısı ve sirkadiyen ritim.
  - ⚠️ Kısmen Desteklenen: Tapo dinamik efektleri (sahne rengine sabitlenerek simüle edilir); Android sahneleri yereldir ve PC ile senkronize olmaz.
  - ⏳ Planlanan: Özel efekt düzenleyici, sesli komut entegrasyonu, gün doğumu/batımı tetikleyicileri.
- **Üçüncü Taraf Bileşen Lisansları:**
  - `tplink` Go kütüphanesi: [Apache-2.0 Lisansı](https://github.com/cB-Sigma/tplink)
  - `pywizlight`: MIT Lisansı
  - `tapo` Python kütüphanesi: MIT / Apache-2.0
- **Depo Lisansı:** Bu depoda henüz resmi bir üst lisans dosyası belirtilmemiştir. Aksi belirtilmedikçe tüm hakları saklıdır.

---

<div id="kurdî"></div>

# ☀️ Kurdî (Kurmancî)

SmartLights bernamenameyeke çavkanîvekirî (open-source) ye ku ji bo Windows (PC) û Androidê hatiye çêkirin da ku çirayên aqilmend ên **WiZ** û **TP-Link Tapo** rasterast li ser tora herêmî (LAN/Wi-Fi) bibîne û kontrol bike — bêyî pêdiviya servîsên ewrî (cloud), pira derveyî an navendên xweserkirinê (hubs).

> **Hişyariya Hiqûqî:** SmartLights projeyeke serbixwe ya civakê ye. Ew ne sepana fermî ya WiZ Connected, Signify an TP-Link Tapo ye û ji aliyê wan ve nehatiye erêkirin.

---

### 1. Danasîna Projeyê
Armanca SmartLights ew e ku çirayên aqilmend ên markayên cuda di bin yek sîstemê de bîne cem hev. Ji ber ku bi rêya protokolên torê yên resen rasterast bi alavên çirayan re diaxive, derengketina ewrê (cloud lag) ji holê radike, şopandina daneyan asteng dike û heta ku înterneta we qut bibe jî dihêle hûn ronahiyên xwe bi rehetî kontrol bikin.

---

### 2. Taybetmendî

- 🔍 **Dîtina Xweber a Cihazan (Discovery):** Bi weşana UDP (broadcast) li ser tora herêmî çirayên WiZ û Tapo bixweber dibîne. Ji ber ku amûran bi rêya nasnameya neguherbar (ji bo WiZ navnîşana MAC, ji bo Tapo `device_id`) tomar dike, guherîna IPyê ya ji aliyê rûterê (DHCP) ve pêwendiyê qut nake.
- ➕ **Bi IPyê Zêdekirina Destî:** Di sepana Androidê de, heke dîtina xweber ji ber astengiyên torê neyê xebitandin, hûn dikarin navnîşana IPyê ya çirayê binivîsin û rasterast lê zêde bikin.
- ⚡ **Vekirin / Girtina Yekser û Rûyê Hêvîdar (Optimistic UI):** Gava hûn pêl bişkokê dikin, dîmen di cih de tê guhertin û ferman di paşperdeyê de tê şandin. Heke çira bersiv nede, rûyê bernameyê vedigere rewşa berê û peyama şaşiyê nîşan dide.
- 🔆 **Kontrolkirina Ronahiyê (Brightness):** Bi parzûnkirina taybet a torê (debouncing/throttling) ji %1 heta %100 kontrolkirina ronahiyê ya bêqutbûn.
- 🎨 **Çerxa Rengan a RGB û Rengên Amade:** Çerxa rengan a 360 pileyî, paletên rengan ên amade û kontrola têrbûna rengan.
- 🌡️ **Germahiya Rengê Spî (Kelvin):** Li gorî sînorên teknîkî yên her çirayê mîhengkirina germahiya spî (mînak ji bo WiZ 2200K–6500K, ji bo Tapo 2500K–6500K).
- 🖼️ **Derxistina Rengan ji Wêneyan (Android):** Hilbijartina wêneyekî ji galeriya telefonê, derxistina rengên wî yên sereke û sepandina wan li ser çirayan.
- 💫 **Dîmen û Bandorên Dînamîk (Dynamic Effects):**
  - *WiZ:* Xebitandina hemû bandor û anîmasyonên resen ên WiZê.
  - *Tapo:* Ji bo Tapoyê bandor bi şêweyekî rawestayî (rengê sereke yê dîmenê) têne sepandin.
- 🏠 **Ode û Komkirin (PC):** Komkirina çirayan li gorî odeyan; bi rêya `asyncio.gather` vekirin û girtina hemû çirayan bi hev re û paralel.
- 🎬 **Dîmenên Taybet ên Tomarkirî (Custom Scenes):** Tomarkirina rewşa hemû çirayan a wê kêliyê û vegerandina wê rewşê bi yek carê pêldanê.
- 🖥️ **Panela Piçûk a Sermaseyê û Tepsiya Sîstemê (PC):**
  - Panela piçûk a sermaseyê (mini panel) ji bo guhertina bilez a ronahiyan.
  - Xebata di paşperdeyê de li tenişta demjimêra Windowsê (system tray) û menuya kontrolê ya klîka rastê.
- 📱 **Widgetên Androidê û Panela Bilez:**
  - 5 widgetên cihêreng ên ekrana sereke: Hemû Çira (`LightsWidget`), Du Çira (`DualLightsWidget`), Yek Çira (`SingleLightWidget`), Şerîta Sahnayan (`ScenesWidget`) û Panela Odeyê (`RoomWidget`).
  - Çargoşeya Mîhengên Bilez (`LightsTile`) di panela agahdariyê de ji bo vekirin/girtina hemû ronahiyan.
- ⏰ **Demjimêra Vemirandinê û Rîtma Sîrkadyen (Android):**
  - Demjimêra girtinê ya xweber (15 xulek, 30 xulek, 1 saet).
  - Rîtma Sîrkadyen a ku li gorî demjimêra rojê germahiya ronahiya spî sererast dike (serê sibê û êvarê zer/germ, nîvro sar/ronî).
- 📊 **Teşxîsa Tora Wi-Fi û Daneyên Enerjiyê (Android):**
  - Hêza sînyala Wi-Fi (bi dBm) û ceribandina derengketina pingê.
  - Ji bo modelên Tapo yên piştgirîkirî: dema xebatê, enerhiya xerckirî (kWh) û daneyên teserûfê.

---

### 3. Lihevhatin û Berhevdana Platforman

#### Piştgiriya Marka û Modelan
- **WiZ:** Hemû çira û lempeyên WiZ ên ku protokola herêmî ya UDP JSON li ser porta `38899` dixebitînin.
- **TP-Link Tapo:** Modelên çirayên bi reng (**L530, L535, L630**), şerîtên ronahiyê yên RGB û modelên ronahiya spî (**L510, L520, L610**). Amûrên din (prizên aqilmend, kamerayan) nayên lîstekirin.

> **Nîşe:** Pêwendiya pêşîn a Wi-Fi û nûvekirinên nermalavê divê bi sepana fermî ya WiZ an Tapoyê werin kirin. SmartLights ji bo bikaranîna rojane ya herêmî ye.

#### Tabloya Berhevdana PC û Androidê

| Taybetmendî | Windows PC | Android |
|---|---|---|
| **Zimanê Bernamekirinê / Rû** | Python 3.13 + PySide6 (Qt Quick / QML) | Kotlin + Jetpack Compose (Material 3 Expressive) |
| **Dîtina Herêmî** | Xweber (UDP Broadcast) | Xweber + Bi destan lêzêdekirina IPyê |
| **Têkiliya WiZê** | Python a Resen (`pywizlight`) | Soketa UDP Datagram a Resen (Port 38899) |
| **Têkiliya Tapoyê** | Python a Resen (pirtûkxaneya `tapo`) | Pira Go (`bridge.aar`, `cB-Sigma/tplink`) |
| **Ewlehiya Şîfreyan** | Windows Credential Locker (`keyring`) | Android Keystore (bi AES-GCM-256 şîfrekirî) |
| **Vekirin / Ronahî / Kelvin** | ✅ Temam e | ✅ Temam e |
| **Çerxa Rengan û Palet** | ✅ Temam e | ✅ Temam e + Derxistina reng ji wêneyan |
| **Bandorên Anîmasyonê** | ✅ Bandorên çêkirî yên WiZ | ✅ Bandorên WiZ (ji bo Tapo rengê rawestayî) |
| **Ode û Komkirin** | ✅ Odeyên taybet û komên ronahiyê | ⚠️ Lîste û bijare (odeyên PCyê hevdem nabin) |
| **Dîmenên Tomarkirî (Scenes)** | ✅ Di pelê JSON de (`%APPDATA%`) | ✅ Li ser bîra telefonê (`SharedPreferences`) |
| **Amûrên Gihîştina Bilez** | Panela Sermaseyê + Tray Menu | 5 Widgetên Ekrana Sereke + Quick Tile |
| **Demjimêr & Xweserkirin** | ❌ Nîne | ✅ Demjimêra girtinê & Rîtma Sîrkadyen |
| **Teşxîsa Torê & Enerjî** | ❌ Rewşa bingehîn | ✅ Testa Ping / RSSI + Daneyên Tapoyê |

---

### 4. Dîmen û Rêbaza Bikaranînê

#### Bernameya Windows PC
1. **Dîmena Sereke:** Odeyan, bişkokên sahnayan, kontrolên giştî û kartên ronahiyê nîşan dide.
2. **Panela Kontrolê ya Çirayê:** Gava hûn li ser karteke çirayê bitikînin, ji aliyê rastê ve panelek vedibe ku tê de beşên **Reng** (çerx û kodên HEX/RGB), **Spî** (germahiya Kelvinê) û **Bandor** (anîmasyonên WiZ) hene.
3. **Panela Piçûk (Mini Panel):** Bi rêya bişkoka çargoşe ya li ser amûran an ji menuya trayê hûn dikarin pencereyeke piçûk vekin da ku zû çirayan biguherînin.
4. **Mîheng:** Vebûna bi Windowsê re, destpêkirina di tepsiyê de, mijara tarî/ronî û mîhengên hesabê Tapoyê birêve dibe.

#### Sepana Androidê
1. **Lîsteya Sereke:** Kartên ronahiyan bi şerîta wan a ronahiyê nîşan dide, beşa bijareyan û sahnayan dihewîne. Bi kişandina ber bi jêr ve tor tê nûkirin.
2. **Rûpela Kûr (Detail):** Bi tikandina li ser her ronahiyekê, şerîta ronahiyê, 12 rengên amade, rengên ji wêneyan û beşên jêrîn vedibin:
   - **Spî:** Şerîta Kelvin û bijareyên Germ / Xwezayî / Ronahiya Rojê.
   - **Dînamîk:** Lîsteya bandorên çirayê.
   - **Demjimêr:** Vemirandina piştî 15, 30, 60 xulekan.
   - **Xweser:** Çalakkirina Rîtma Sîrkadyen.
   - **Enerjî:** Daneyên xerckirina elektrîkê ji bo Tapoyê.
   - **Pêwendî:** Testa lez û sînyala Wi-Fi.
3. **Menuya Lêzêdekirinê (+):** Bi bişkoka li jêr hûn dikarin bi IPyê çira lê zêde bikin, sahnayek nû tomar bikin, hesabê Tapo binivîsin an çirayên veşartî paşve bînin.

---

### 5. Rêbaza Pêwendiyê û Protokol

```
┌─────────────────────────────────────────────────────────────┐
│                       Tora Herêmî ya Wi-Fi                  │
│                                                             │
│   Windows PC (SmartLights)          Android (SmartLights)   │
│   ├── pywizlight (UDP)              ├── Soketa UDP          │
│   └── pirtûkxaneya tapo (TCP/HTTP)  └── Pira Go (TCP/HTTP)  │
│              │                               │              │
└──────────────┼───────────────────────────────┼──────────────┘
               ▼                               ▼
     ┌───────────────────┐           ┌───────────────────┐
     │  Çirayên WiZê     │           │ Çirayên Tapoyê    │
     │  Porta UDP 38899  │           │ KLAP / HTTP Herêmî│
     │  Bê şîfre dixebite│           │ Têketina herêmî   │
     └───────────────────┘           └───────────────────┘
```

- **Protokola WiZê:** Pakêtên rasterast ên UDP datagram li ser porta `38899` bi şêweya JSON têne şandin. Pêdivî bi hesab, şîfre an destûra ewrê nîne.
- **Protokola Tapoyê:** Bi protokola herêmî ya TP-Linkê (danûstandina ewle ya KLAP / AES li ser HTTP) pêk tê. E-name û şîfreya we ya Tapoyê tenê ji bo çêkirina danişîna herêmî li ser çirayê tê bikaranîn. Hemû agahî di nav tora we ya herêmî de dimîne.
- **Pêdiviya Înternetê:** Piştî sazkirinê, ji bo bikaranîna rojane qet pêdivî bi înternetê nîne. Divê tenê telefon/komputer û çira li ser **heman tora Wi-Fi** bin.
- **Mîhengên Girîng:**
  - WiZ: Di sepana fermî ya WiZê de di bin *Settings → Security* de divê vebijarka **"Allow local communication"** vekirî be.
  - Rûter: Divê taybetmendiya veqetandina cîhazan ("AP Isolation" an "Client Isolation") li ser modema we **girtî** be.

---

### 6. Avahiya Teknîkî û Rêxistina Pelan

```
SmartLights/
├── main.py                  # Xala destpêkê ya PCyê (sepan PySide6 QML)
├── cli.py                   # Amûra termînalê ji bo dîtin û kontrolê
├── SmartLights.pyw          # Ji bo destpêkirina bê pencereya termînalê
├── build-signed.ps1         # Skrîpta avakirina guhertoya fermî ya bi mohr
├── core/                    # Mentiqa sîstemê ya sereke
│   ├── async_worker.py      # asyncio worker thread a ku UI nade rawestandin
│   ├── config_store.py      # Tomarkirina ewle ya pelên JSON
│   ├── credentials.py       # Birêvebirina şîfreyan bi keyring
│   ├── device_manager.py    # QAbstractListModel, optimistic UI û fermanên komî
│   ├── autostart.py         # Destpêkirina bi Windowsê re
│   └── tray.py              # Tepsiya sîstemê û menuya klîka rastê
├── devices/                 # Adapterên ku pîvana LightController pêk tînin
│   ├── base.py              # Çîna bingehîn a razber (LightController)
│   ├── wiz_adapter.py       # Koda kontrola WiZ bi pywizlight
│   └── tapo_adapter.py      # Koda kontrola Tapo bi tapo
├── models/                  # Modelên Pydantic (LightDevice, Capabilities, Scene)
├── ui/                      # Pelên rûyê bernameyê yên Qt Quick / QML
│   ├── Main.qml             # Pencereya sereke
│   ├── components/          # Parçeyên dubarekirî (LightCard, ColorWheel, DevicePanel hwd.)
│   └── theme/               # Şêwaz û rengên FluentWinUI3
└── android/                 # Sepana Xwecihî ya Androidê
    ├── bridge/              # Pira Go ya ji bo Tapoyê (cB-Sigma/tplink)
    └── app/                 # Sepana Kotlin bi Jetpack Compose
```

---

### 7. Sazkirin û Xebitandin

#### Windows PC

##### Ji Çavkaniyê Xebitandin
1. **Pêdivî:** Python 3.11+ (Python 3.13 tê pêşniyarkirin) û Git.
2. Depoyê daxînin û hawirdora serbixwe ava bikin:
   ```bash
   git clone <repo_url>
   cd SmartLights
   python -m venv .venv
   .venv\Scripts\activate
   pip install -r requirements.txt
   ```
3. Bernameyê bidin destpêkirin:
   ```bash
   python main.py
   # An jî bê pencereya reş a termînalê:
   pythonw SmartLights.pyw
   ```

##### Çêkirina Pelê `.exe`
- Ji bo avakirina pelê serbixwe yê `dist/SmartLights.exe`:
  ```bash
  .venv\Scripts\pyinstaller.exe SmartLights.spec --noconfirm
  ```
- Ji bo avakirina bi bawernameya mohrkirî (ji bo Windows Smart App Control):
  ```powershell
  .\build-signed.ps1 -CertificatePath C:\rih\bawername.pfx
  ```

---

#### Android

##### Ji Çavkaniyê Çêkirin
1. **Pêdivî:** JDK 17+, Android SDK 36 û Gradle 8.13+.
2. **Pira Amade:** Pelê pira arm64 di nav depoyê de li `android/app/libs/bridge.aar` amade ye.
3. **Çêkirina APK bi rêya Gradle:**
   ```bash
   cd android
   ./gradlew assembleRelease
   ```
   Pelê APK yê çêbûyî dê li bin `android/app/build/outputs/apk/release/` be.

##### (Bijarte) Ji Nû ve Avakirina Pira Go
Gava ku di `android/bridge/bridge.go` de guhertin çêbibe:
1. Go 1.26+ û Android NDK saz bikin.
2. Amûra `gomobile` bar bikin:
   ```bash
   go install golang.org/x/mobile/cmd/gomobile@latest
   gomobile init
   ```
3. Di peldanka `android/bridge` de pelê AAR ava bikin:
   ```bash
   cd android/bridge
   gomobile bind -target=android/arm64 -androidapi 26 -o ../app/libs/bridge.aar .
   ```

---

### 8. Veavakirin û Destûr

#### Mîhengên Windowsê
- **Cihê Tomarkirinê:** Di bin `%APPDATA%\SmartLights\` de (`config.json` ji bo nav û sahnayan; `devices_cache.json` ji bo navnîşa dawî ya çirayan).
- **Parastina Şîfreyan:** E-name û şîfreya Tapoyê bi rêya pirtûkxaneya `keyring` rasterast di **Windows Credential Locker** de bi ewlehî têne veşartin; ti carî wekî nivîsa vekirî li ser dîskê nayên nivîsandin.

#### Mîheng û Destûrên Androidê
- **Depoya Şîfrekirî:** Şîfreyên Tapoyê bi rêya hardware-backed `AndroidKeyStore` û bi algorîtmaya AES-GCM-256 têne şîfrekirin û di SharedPreferences de têne ragirtin.
- **Destûrên Sepanê (`AndroidManifest.xml`):**
  - `INTERNET`: Ji bo pêwendiya soketan a li ser tora herêmî.
  - `ACCESS_NETWORK_STATE` & `ACCESS_WIFI_STATE`: Ji bo kontrolkirina rewşa tora Wi-Fi.
  - `CHANGE_WIFI_MULTICAST_STATE`: Ji bo şandina pakêtên keşfê yên UDP broadcast.
  - `RECEIVE_BOOT_COMPLETED`: Ji bo domandina alarmên rîtma sîrkadyen gava ku telefon ji nû ve dest pê bike.

---

### 9. Çareserkirina Pirsgirêkan

- **Çira Bixweber Naye Dîtin:**
  - Kontrol bikin ka komputer an telefona we bi heman tora Wi-Fi (banda 2.4 GHz) ya çirayan re girêdayî ye yan na.
  - Kontrol bikin ka vebijarka "AP Isolation" li ser modema we girtî ye yan na.
  - Li ser Windowsê piştrast bikin ku Dîwarê Ewlehiyê (Firewall) pakêtên UDP broadcast asteng nake.
  - Li ser Androidê heke neyê dîtin, bişkoka **"+" → "IP ile ışık ekle"** bikar bînin û navnîşana IPyê bixwe binivîsin.
- **Çirayên WiZê Bersiv Nadin:**
  - Sepana fermî ya WiZê li ser telefonê vekin û di beşa **Settings → Security** de piştrast bikin ku vebijarka **"Allow local communication"** vekirî ye.
- **Şaşiya Hesabê Tapoyê:**
  - Piştrast bin ku we e-name û şîfreya hesabê xwe yê TP-Link Tapoyê rast nivîsiye. Heke we ew guhertibe, ji mîhengan ji nû ve binivîsin.
- **Derengketin an Çirayên Xeybî (Offline):**
  - Dibe ku rûterê IPya çirayê guhertibe. Li ser PCyê pêl bişkoka nûkirinê bikin an li ser Androidê lîsteyê ber bi jêr ve bikşînin da ku tor ji nû ve were şehkirin.

---

### 10. Rewşa Pêşveçûnê û Lîsans

- **Rewşa Niha ya Xebatê:**
  - ✅ Dixebite: Dîtina li ser torê, zêdekirina destî ya bi IPyê (Android), vekirin/girtin, asta ronahiyê, rengên RGB, germahiya Kelvin, sahnayên tomarkirî, ode (PC), panela sermaseyê, widgetên Androidê, demjimêra vemirandinê û rîtma sîrkadyen.
  - ⚠️ Nîvco: Bandorên anîmasyonî yên Tapoyê (wekî rengê rawestayî yê sereke têne nîşandan); sahnayên Androidê herêmî ne û bi PCyê re hevdem nabin.
  - ⏳ Di Planê de: Çêkirina bandorên nû yên taybet, fermanên dengî û xweserkirina li gorî hilatin/avakirina rojê.
- **Lîsansên Parçeyên Sêyemîn:**
  - Pirtûkxaneya `tplink` Go: [Lîsansa Apache-2.0](https://github.com/cB-Sigma/tplink)
  - `pywizlight`: Lîsansa MIT
  - Pirtûkxaneya `tapo` Python: Lîsansa MIT / Apache-2.0
- **Lîsansa Depoyê:** Ji bo vê projeyê heta niha pelekî lîsansê yê serbixwe nehatiye danîn. Heya ku neyê diyarkirin hemû maf parastî ne.

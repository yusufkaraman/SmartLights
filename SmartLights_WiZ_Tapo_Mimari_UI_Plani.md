# SmartLights — WiZ + Tapo Windows Uygulaması Mimari ve UI Planı

Evet. Araştırınca temel fikir doğru çıktı ama **ilk yazdığım mimariyi aynen kullanmazdım**. Özellikle Tapo kütüphanesi, async mimarisi ve `.exe` paketleme tarafında daha iyi seçimler var.

## Sonuç: benim kuracağım mimari

```text
                 SmartLights.exe
                       │
        ┌──────────────┴──────────────┐
        │                             │
   PySide6 / QML                 AppController
   FluentWinUI3                       │
        │                    DeviceManager
        │                             │
        │                  ┌──────────┴──────────┐
        │                  │                     │
        │             WizAdapter            TapoAdapter
        │                  │                     │
        │             pywizlight               tapo
        │                  │                     │
        └────────────── LAN / Wi-Fi ────────────┘
                           │
                    ┌──────┴──────┐
                    │             │
                  WiZ            Tapo
```

### Benim seçtiğim teknoloji seti

| Parça | Seçim | Neden |
|---|---|---|
| WiZ | **`pywizlight`** | Aktif, Temmuz 2026'da 0.6.5 yayımlandı; RGB, Kelvin, brightness, sahne ve UDP discovery destekliyor. |
| Tapo | **`tapo` 0.9.x** | `python-kasa` yerine bunu ana backend yapardım. Nisan 2026 sürümü var; L530/L535/L630 ailesinde renk, HSV, Kelvin ve brightness doğrudan destekleniyor ve cihaz discovery API'si var. |
| Tapo fallback | `python-kasa` | İleride başka TP-Link cihazı çıkarsa iyi yedek. Kapsamı çok geniş ve Home Assistant da bunu kullanıyor. |
| UI | **PySide6 + Qt Quick/QML** | Widgets'tan çok daha modern ve animasyon/responsive kart tasarımı için uygun. |
| Windows görünümü | **FluentWinUI3** | Qt 6.8'den beri var; Windows 11 / WinUI 3 görünümünü hedefliyor. |
| Async | **ayrı asyncio worker thread** | Qt UI thread'i hiçbir LAN sorgusunda beklemeyecek. QtAsyncio hâlâ “technical preview”, o yüzden ana mimariyi ona bağlamam. |
| Şifre | **`keyring` → Windows Credential Locker** | Tapo parolasını JSON/config içine düz yazıyla koymuyoruz. |
| Ayarlar | `QSettings` | Windows'ta Registry altında uygulama ayarlarını doğal biçimde tutabiliyor. |
| `.exe` | **`pyside6-deploy` / Nuitka** | PySide6'nın kendi deployment yolu; varsayılan `onefile` modu gerçekten tek `.exe` üretiyor ve QML pluginlerini analiz edip gereksiz ağır modülleri dışlayabiliyor. |

---

## 1. En önemli değişiklik: Tapo'da `python-kasa` yerine `tapo`

İlk önerimde:

```text
Tapo
 ↓
python-kasa
```

demiştim.

Şimdi bunu:

```text
Tapo
 ↓
tapo
```

yapardım.

Çünkü `tapo` projesi doğrudan **Tapo cihazlarına odaklanıyor** ve güncel 0.9.0 sürümü 25 Nisan 2026'da yayımlanmış. Python API'si de Rust backend'in Python wrapper'ı olarak geliyor.

Özellikle renkli Tapo ışıklarda API şunları destekliyor:

```text
on()
off()

set_brightness()

set_color()
set_hue_saturation()
set_color_temperature()

set(...)
```

L530/L535/L630 grubunda bunların Python desteği açıkça listelenmiş.

Bu bizim için çok iyi. Örneğin kullanıcı renk seçerken:

```text
renk + parlaklık
```

ayrı iki ağ isteği yerine desteklenen durumda tek request'e indirilebilir.

---

## 2. Tapo cihazını IP ile sabitlemeyeceğiz

Bu önemli.

Router yeniden başlayınca:

```text
192.168.1.23
```

olan ampul yarın:

```text
192.168.1.37
```

olabilir.

`tapo` artık LAN discovery yapabiliyor ve discovery sonucundan:

```text
device_id
nickname
model
ip
device_type
```

alabiliyor.

Dolayısıyla bizim veritabanındaki ana kimlik:

**IP olmayacak.**

Şöyle olacak:

```text
Tapo:
    stable_id = device_id

WiZ:
    stable_id = MAC
```

WiZ discovery'si de doğrudan IP + MAC topluyor.

Bu sayede IP değişirse:

```text
Eski:
AA:BB:CC → 192.168.1.22

Yeni discovery:
AA:BB:CC → 192.168.1.41
```

uygulama otomatik düzeltir.

---

## 3. Açılışta discovery'yi bekletmeyeceğiz

Uygulamayı açınca 5-10 saniye:

> Cihazlar aranıyor...

ekranında beklemek kötü UX olur.

WiZ'nin standart discovery süresi varsayılan olarak yaklaşık 5 saniye. Tapo tarafında da discovery timeout mekanizması mevcut.

Bu yüzden açılış algoritması:

```text
SmartLights.exe açılır
        │
        ├── QSettings'ten son cihazları oku
        │
        ├── Kartları ANINDA göster
        │
        ├── son IP'lere paralel ping/status isteği
        │
        └── arkada LAN discovery başlat
                        │
                 IP değişmişse güncelle
```

Yani uygulama ilk 100-300 ms'de görsel olarak açılır; ağ taraması kullanıcıyı bloklamaz.

---

## 4. WiZ discovery'de ekstra koruma koyacağız

`pywizlight` gayet uygun ama discovery'nin bazı koşullarda takılabileceği raporlandığı için kendi timeout katmanımız olacak.

Örneğin:

```python
await asyncio.wait_for(
    discover_wiz(),
    timeout=6
)
```

Discovery başarısız olursa uygulama çökmez:

```text
✓ Tapo bulundu
⚠ WiZ taraması zaman aşımına uğradı
↻ Yeniden tara
```

Bu küçük detay `.exe`'nin günlük kullanımda sağlam hissettirmesi açısından önemli.

---

## 5. WiZ tamamen local

Burada Google Home gerçekten gereksiz.

`pywizlight` UDP kullanıyor ve şu kontrolleri destekliyor:

```text
state
dimming
RGB
cold/warm white
Kelvin
sceneId
scene speed
```

Ayrıca cihazın mevcut durumunu okuyabiliyor.

WiZ tarafında yalnız telefondaki WiZ uygulamasında:

**Settings → Security → Allow local communication**

açık olmalı.

---

## 6. Tapo da kontrol sırasında local

Burada küçük ama önemli bir ayrım var.

Tapo cihazları yerel LAN üzerinden kontrol edilebiliyor fakat yeni Tapo cihazları local protokole giriş yapmak için **TP-Link hesabının kullanıcı adı/parolasını** isteyebiliyor.

Yani:

```text
Google Home ❌
TP-Link cloud üzerinden ışık komutu ❌

PC
 │
 └─ Wi-Fi/LAN
       │
       └─ Tapo ampul ✅
```

Ama oturum açma protokolü için Tapo credential'ı gerekiyor.

---

## 7. Tapo şifresi kesinlikle config.json içinde olmayacak

Şunu yapmayacağız:

```json
{
  "email": "xxx@gmail.com",
  "password": "123456"
}
```

Onun yerine:

```text
Windows Credential Locker
        │
        ├── SmartLights/Tapo
        ├── username
        └── password
```

Python `keyring`, Windows Credential Locker'ı destekliyor.

Sadece küçük UI tercihleri:

```text
tema
son pencere boyutu
son açık sekme
```

`QSettings` ile tutulabilir. Oda isimleri, cihaz sırası ve favori renkler artık aşağıdaki (7.5) yapılandırılmış veri dosyasında tutulacak — bunlar `QSettings`'in registry tabanlı key-value modeline göre fazla "nested" veri.

---

## 7.5. Veri kalıcılığı: JSON dosyası, SQLite değil

Cihaz/oda/sahne listesi nerede saklanacak sorusuna net cevap:

**SQLite değil, düz JSON.**

Gerekçe: bu ölçekte (birkaç düzine cihaz, birkaç oda, birkaç sahne, tek kullanıcı, tek makine) SQLite'ın sağladığı hiçbir şeye (sorgu, transaction, eşzamanlı erişim) ihtiyaç yok. JSON hem nested veriyi (oda → cihazlar → capability) doğal biçimde tutar hem de kullanıcı tarafından incelenebilir/yedeklenebilir kalır.

Konum:

```text
%APPDATA%\SmartLights\
    config.json           ← odalar, cihazlar, sahneler
    devices_cache.json    ← (opsiyonel) son discovery sonucu
```

`QStandardPaths.AppDataLocation` ile alınır, registry'ye hiç girmez.

Şema taslağı:

```json
{
  "version": 1,
  "rooms": [
    { "id": "room_1", "name": "Salon", "order": 0 }
  ],
  "devices": [
    {
      "stable_id": "AA:BB:CC:DD:EE:FF",
      "brand": "wiz",
      "name": "Salon Lambası",
      "room_id": "room_1",
      "last_known_ip": "192.168.1.22",
      "capabilities": { "rgb": true, "kelvin": true, "effects": true }
    }
  ],
  "scenes": [
    {
      "id": "scene_film",
      "name": "Film",
      "actions": [
        { "device_id": "AA:BB:CC:DD:EE:FF", "rgb": [255, 80, 20], "brightness": 20 }
      ]
    }
  ]
}
```

Yazma stratejisi — atomic write:

```text
1. config.json.tmp'ye yaz
2. os.replace(tmp, config.json)   → atomik, yarım yazılmış dosya riski yok
```

Bu, uygulama kapanırken/çökerken bozuk JSON kalmasını engeller.

Kod tarafı: `models/` altındaki `light_device.py`, `room.py`, `scene.py` Pydantic `BaseModel` olarak yazılır; `model_dump_json()` / `model_validate_json()` ile serialize edilir. `"version": 1` alanı ileride şema değişince migration için kullanılır.

Sonuç — üçlü ayrım:

```text
config.json   → yapılandırılmış veri (odalar, cihazlar, sahneler)
QSettings     → küçük UI tercihleri (tema, pencere boyutu, son sekme)
keyring       → Tapo credential'ları (secret)
```

SQLite bu projede gerekmiyor. İleride tarihsel enerji/kullanım logu gibi bir ihtiyaç çıkarsa ayrı bir `usage.db` eklenebilir ama MVP kapsamı dışında.

---

## 8. Async mimariyi değiştirelim

İki cihaz kütüphanesi de async çalışmaya uygun.

Özellikle `pywizlight`, Python'ın `asyncio DatagramTransport` sistemini kullanıyor.

Fakat UI'nin event loop'u ile asyncio'yu doğrudan birbirine bağlamayı ilk sürümde istemem.

Qt'nin kendi:

```text
PySide6.QtAsyncio
```

modülü mevcut fakat hâlâ **technical preview**.

Ben daha sağlam şekilde:

```text
THREAD 1
──────────────
Qt / QML UI
60 FPS
mouse
slider
animations


THREAD 2
──────────────
asyncio event loop
WiZ UDP
Tapo requests
discovery
status polling
```

yapardım.

Aralarındaki iletişim:

```text
Qt Signal / Slot
```

ile olur.

Böylece ampul 3 saniye cevap vermese bile renk çarkı veya pencere **asla donmaz**.

---

## 9. UI için Widgets kullanmayalım

Burada önceki önerimi de geliştiriyorum.

**PySide6 Qt Widgets yerine:**

## PySide6 + Qt Quick + QML

kullanmak daha doğru.

Qt Quick Controls içerisinde Windows 11 görünümüne yakın resmi:

**FluentWinUI3**

stili var.

---

## 10. Ben UI'yi böyle tasarlardım

Ana pencere yaklaşık:

```text
1100 × 720
```

ve şu yapıda:

```text
┌──────────────────────────────────────────────────────────────┐
│  Smart Lights                                    ─  □  ×    │
│                                                              │
│  Evim                                      ● 4 cihaz çevrimiçi│
│                                                              │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  Tüm Işıklar                         [ AÇ ] [ KAPAT ] │  │
│  └────────────────────────────────────────────────────────┘  │
│                                                              │
│  Hızlı Sahneler                                              │
│                                                              │
│  [ ☀ Günlük ] [ 🌙 Gece ] [ 🎬 Film ] [ 🎮 Oyun ] [ + ]   │
│                                                              │
│  SALON                                                       │
│                                                              │
│  ┌────────────────────┐     ┌────────────────────┐           │
│  │ ● Salon Lambası    │     │ ● TV Lambası       │           │
│  │ WiZ                │     │ Tapo · L535        │           │
│  │                    │     │                    │           │
│  │       💡           │     │       💡           │           │
│  │                    │     │                    │           │
│  │ ███████──── 72%    │     │ █████────── 54%    │           │
│  │                    │     │                    │           │
│  │    ON  ●────       │     │    ON  ●────       │           │
│  └────────────────────┘     └────────────────────┘           │
│                                                              │
│  YATAK ODASI                                                 │
│  ...                                                         │
│                                                              │
│  Ev          Sahneler          Cihazlar          Ayarlar     │
└──────────────────────────────────────────────────────────────┘
```

Kartın tamamına tıklayınca sağdan:

```text
┌─────────────────────────────┐
│ Salon Lambası           ×   │
│ WiZ • Online                │
│                             │
│   Renk     Beyaz    Efekt   │
│  ─────                      │
│                             │
│        ╭─────────╮          │
│       ╱  COLOR    ╲         │
│      │ SPECTRUM    │        │
│       ╲           ╱         │
│        ╰─────────╯          │
│                             │
│ Parlaklık                   │
│ ━━━━━━━━━━━●━━━━━━ 72%      │
│                             │
│ Favoriler                   │
│ ● ● ● ● ● ●                │
│                             │
│ Son kullanılan             │
│ ●  #FF8C42                  │
└─────────────────────────────┘
```

şeklinde bir **detail panel** açılır.

---

## 11. Renk seçici nasıl olmalı?

Ana renk ekranında:

```text
       renk çarkı

          ●

   brightness slider
```

yeterli.

Ama altta küçük:

**Gelişmiş**

düğmesi olur.

Basınca:

```text
HEX       #FF8A34
R         255
G         138
B         52

Hue       27°
Sat       80%
```

açılır.

---

## 12. Beyaz ışık ayrı sekme olmalı

RGB ile Kelvin'i aynı ekrana doldurmayalım.

**Beyaz** sekmesi:

```text
Beyaz sıcaklığı

2200K                           6500K
🔥━━━━━━━━━━━━●━━━━━━━━━━━━━━━❄️
              4120 K


Parlaklık

0% ━━━━━━━━━━━━━●━━━━━━━━━━━━ 100%


Hazır

[ Mum ]
2200 K

[ Sıcak ]
2700 K

[ Normal ]
4000 K

[ Gün Işığı ]
5000 K

[ Soğuk ]
6500 K
```

olmalı.

Ama değerleri **sabit 2200–6500 varsaymayacağız**.

Her cihazın capability'sinden:

```text
min_kelvin
max_kelvin
```

alacağız.

Örneğin:

```text
WiZ cihaz A
2200 — 6500

Tapo cihaz B
2500 — 6500
```

gibi dinamik değişecek.

---

## 13. Capability-driven UI yapmalıyız

Bence projenin en önemli yazılım tasarım kararlarından biri bu.

UI'da:

```python
if brand == "wiz":
```

gibi kontroller görmek istemiyorum.

Onun yerine her cihaz şunu sunmalı:

```text
Capabilities

power             true
brightness        true
rgb               true
color_temperature true
effects           true
effect_speed      true/false
```

Sonra QML:

```text
rgb varsa
    Renk sekmesini göster

kelvin varsa
    Beyaz sekmesini göster

effect varsa
    Efekt sekmesini göster
```

der.

Böylece yarın:

```text
Philips Hue
Yeelight
Matter
```

eklemek istediğimizde UI'yi yeniden yazmayız.

---

## 14. Normalized `LightDevice`

Backend'in QML'ye verdiği cihaz yaklaşık böyle görünmeli:

```text
LightDevice

id
name
room

brand
model
ip

online
rssi

power
brightness

color_mode

rgb
hsv

kelvin
min_kelvin
max_kelvin

effects[]

capabilities
```

WiZ veya Tapo olduğunun UI için çoğu zaman önemi kalmaz.

UI:

```text
device.brightness = 70
```

der.

DeviceManager arkada:

```text
WiZ → PilotBuilder
```

veya

```text
Tapo → set_brightness()
```

çevirir.

---

## 15. Slider'da her piksel için ağ isteği göndermeyelim

Kullanıcı:

```text
10 → 11 → 12 → 13 → 14 → ... → 80
```

sürüklerken 70 LAN isteği göndermek gereksiz.

UI anında hareket eder ama network katmanında:

```text
40–80 ms debounce
```

kullanırız.

Mesela:

```text
12
23
38
51
67
80
```

gibi birkaç gerçek komut gider.

---

## 16. Optimistic UI kullanacağız

Kullanıcı ışığı kapatınca:

```text
tık
↓
UI hemen OFF görünür
↓
arka planda request
```

olmalı.

Request başarısız olursa:

```text
⚠ Salon Lambası yanıt vermedi
```

ve kart önceki durumuna döner.

---

## 17. Oda ve grup kontrolü

Bence sadece ampul kontrolü yetersiz kalır.

Örneğin:

```text
SALON

[ Tümünü Aç ]

WiZ
Tapo
Tapo
```

"Oda kapat" dediğinde:

```python
asyncio.gather(
    wiz.off(),
    tapo1.off(),
    tapo2.off()
)
```

mantığıyla paralel çalıştıracağız.

Yani:

```text
WiZ → 80 ms
Tapo → 130 ms
Tapo → 90 ms
```

toplam 300 ms değil, yaklaşık en yavaş cihaz kadar sürer.

---

## 18. Sahneler marka bağımsız olacak

### Film

```text
Salon WiZ
RGB: 255, 80, 20
Brightness: 20%

TV Tapo
RGB: 110, 20, 255
Brightness: 12%

Tavan Tapo
OFF
```

### Çalışma

```text
Tüm salon
4500 K
85%
```

### Gece

```text
Yatak odası
2400–2700K
10%
```

Böylece Tapo'nun kendi scene sistemi ile WiZ'nin kendi scene sistemini kullanıcıya ayrı ayrı göstermek zorunda kalmayız.

Ayrıca bir **Vendor Effects** bölümü olabilir.

---

## 19. Sistem tray'i kesin ekleyelim

Sağ altta:

**💡 SmartLights**

olur.

Sağ tık:

```text
SmartLights
────────────
✓ Salon açık

Tümünü Aç
Tümünü Kapat
────────────
Gece
Film
Çalışma
────────────
Uygulamayı Aç
Çıkış
```

Bu, günlük kullanımda ana pencerenin kendisinden bile kullanışlı olur.

---

## 20. Windows açılışında çalışabilir

Opsiyon:

```text
☑ Windows açıldığında SmartLights'i başlat
☑ Başlangıçta pencereyi gösterme
```

Böylece uygulama:

```text
Windows açılır
↓
tray'e yüklenir
↓
WiZ + Tapo bağlantıları hazırlanır
```

ve istediğin anda sağ alttan kontrol edersin.

---

## 21. Tema

Varsayılan:

**Windows sistem temasını takip et**

Ayarlar:

```text
Tema

● Sistem
○ Açık
○ Koyu
```

Ama ışık uygulamasında **dark mode özellikle güzel olur** çünkü renk swatch'ları daha net görünür.

---

## 22. Marka renklerini abartmayalım

WiZ kartını komple mavi, Tapo kartını komple yeşil yapmak istemem.

Premium görünmesi için:

```text
WiZ
küçük brand badge

Tapo
küçük brand badge
```

yeterli.

Kartın ana rengi ampulün **gerçek mevcut rengine göre hafif glow** verebilir.

---

## 23. `.exe` konusunda PyInstaller yerine `pyside6-deploy`

İlk söylediğim:

> PyInstaller

yerine:

## `pyside6-deploy`

kullanırdım.

Bu Qt'nin kendi deployment aracı ve arkada **Nuitka** kullanıyor.

Daha da önemlisi `pysidedeploy.spec` içinde:

```text
mode = onefile
```

destekleniyor.

Yani sonuç gerçekten:

```text
SmartLights.exe
```

olabilir.

Yanında:

```text
python.exe
Qt6.dll
qml/
plugins/
```

gibi dosyaları kullanıcı görmek zorunda kalmaz.

---

## 24. QML yüzünden `.exe` devasa olmasın

`pyside6-deploy` QML dosyalarını tarayıp kullanılmayan ağır Qt modüllerini dışlayabiliyor.

Biz:

```text
QtQuick
QtQuick.Controls
QtQuick.Layouts
QtQuick.Dialogs
QtCore
QtGui
QtWidgets (tray için)
```

dışında mümkün olduğunca az şey kullanırız.

---

## 25. PyInstaller tamamen kötü değil

PyInstaller da PySide6 ile çalışıyor ve `--onefile` destekliyor.

Ama yeni bir **Qt/QML-first** proje için:

```text
pyside6-deploy
     ↓
Nuitka
```

daha doğal seçim.

İlk build:

```text
standalone
```

olarak debug edilir.

Her şey çalıştıktan sonra:

```text
onefile
```

üretilir.

---

## 26. Home Assistant mı, bizim `.exe` mi?

Home Assistant zaten hem WiZ hem Tapo'yu gayet iyi destekliyor.

Ama sadece:

> Windows PC'den WiZ ve Tapo ışıklarımı güzel bir uygulamayla kontrol edeyim.

hedefinde Home Assistant gereksiz büyük bir katman.

Bizim uygulamada:

```text
PC → ampul
```

olurken HA'da:

```text
PC
↓
Home Assistant
↓
integration
↓
ampul
```

olur.

**İleride 30 cihaz, sensörler, otomasyonlar, kameralar, telefon dashboard'u vs. olacaksa Home Assistant daha mantıklı.**

Ama bu proje için **native SmartLights.exe** daha uygun.

---

## 27. Proje klasörü

```text
SmartLights/
│
├── main.py
├── pyproject.toml
├── pysidedeploy.spec
│
├── core/
│   ├── device_manager.py
│   ├── discovery_manager.py
│   ├── async_worker.py
│   ├── scene_manager.py
│   ├── credentials.py
│   └── config_store.py   # config.json okuma/atomic write (bkz. 7.5)
│
├── devices/
│   ├── base.py
│   ├── wiz_adapter.py
│   ├── tapo_adapter.py
│   └── kasa_adapter.py
│
├── models/
│   ├── light_device.py
│   ├── capabilities.py
│   ├── room.py
│   └── scene.py
│
├── ui/
│   ├── Main.qml
│   ├── pages/
│   │   ├── HomePage.qml
│   │   ├── ScenesPage.qml
│   │   ├── DevicesPage.qml
│   │   └── SettingsPage.qml
│   ├── components/
│   │   ├── LightCard.qml
│   │   ├── DevicePanel.qml
│   │   ├── ColorWheel.qml
│   │   ├── KelvinSlider.qml
│   │   ├── BrightnessSlider.qml
│   │   ├── SceneChip.qml
│   │   └── OnlineIndicator.qml
│   └── theme/
│       ├── Theme.qml
│       └── qmldir
│
├── assets/
│   ├── icons/
│   └── SmartLights.ico
│
└── tests/
    ├── test_wiz.py
    ├── test_tapo.py
    ├── test_discovery.py
    └── mock_devices.py
```

---

## 28. Geliştirme sırası

### Faz 1 — CLI proof-of-concept

Önce UI yok:

```text
WiZ tara
Tapo tara

cihazları yazdır

1 → aç
2 → kapat
3 → RGB
4 → Kelvin
```

Gerçek ampullerle protokolü doğrularız.

### Faz 2 — ortak Device abstraction

```text
WizAdapter
TapoAdapter

↓ aynı API ↓

turn_on()
turn_off()
set_brightness()
set_rgb()
set_kelvin()
get_state()
```

### Faz 3 — QML skeleton

Sadece:

```text
kart
toggle
brightness
```

### Faz 4 — color panel

```text
RGB wheel
Kelvin
presets
```

### Faz 5 — discovery/cache

IP değişikliği, offline detection.

### Faz 6 — rooms + scenes

### Faz 7 — tray

### Faz 8 — credentials

Credential Locker.

### Faz 9 — error handling

```text
Wi-Fi yok
ampul kapalı
IP değişti
Tapo authentication fail
broadcast engellendi
```

### Faz 10 — Nuitka onefile

Final:

```text
SmartLights.exe
```

---

## 29. MVP'de tam olarak bunlar olsun

```text
✓ WiZ otomatik keşif
✓ Tapo otomatik keşif

✓ marka/model gösterme
✓ online/offline

✓ aç/kapat
✓ parlaklık

✓ RGB renk çarkı
✓ Kelvin slider

✓ renk presetleri
✓ oda oluşturma

✓ tüm oda aç/kapat
✓ global tüm ışıklar

✓ kendi sahnelerimiz

✓ sistem tray
✓ dark/light theme

✓ Tapo Credential Locker

✓ IP değişince yeniden bulma

✓ Windows startup

✓ tek SmartLights.exe
```

Şimdilik şunları **koymam**:

```text
✗ kullanıcı hesabı
✗ cloud backend
✗ veritabanı sunucusu
✗ web server
✗ Google Home
✗ Electron
✗ Home Assistant dependency
```

Bunlar bu kullanım için gereksiz karmaşıklık.

---

## Son karar

İlk öneriyi **10 üzerinden 7.5** sayardım.

Araştırma sonrası kurulması gereken versiyon:

> **Python 3.13 + PySide6 + Qt Quick/QML + FluentWinUI3 + `pywizlight` + `tapo` + ayrı asyncio worker + Windows Credential Locker (`keyring`) + QSettings + `pyside6-deploy/Nuitka onefile`.**

Bunu **9/10 seviyesinde doğru mimari** olarak görüyorum.

Özellikle üç değişikliği kesin yapalım:

- **`python-kasa → tapo`** ana Tapo driver'ı
- **Widgets → QML/FluentWinUI3**
- **PyInstaller → pyside6-deploy/Nuitka**

`python-kasa` ise yedek TP-Link adapter olarak projede kalabilir.

Bir sonraki aşama doğrudan kodlamak olmalı: önce **WiZ + Tapo'yu aynı LAN'da otomatik bulup cihaz/model/IP/MAC-ID ve tüm ışık capability'lerini ekrana döken test uygulamasını** kurarız; o test geçtikten sonra bu QML UI'yi üzerine oturtmak en az riskli yol.

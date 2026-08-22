"""Kalıcı veri: cihaz cache'i + yapılandırma, JSON dosyaları, atomic write
(plan madde 3, 5, 7.5).

İki ayrı dosya:
    devices_cache.json  -> son bilinen cihaz durumları (açılışta anında göster)
    config.json          -> kullanıcı verisi: isim/oda override'ları, sahneler

SQLite değil düz JSON — gerekçe plan madde 7.5'te.
"""

from __future__ import annotations

import json
import os
from pathlib import Path

from PySide6.QtCore import QStandardPaths

from models.device_override import DeviceOverride
from models.light_device import LightDevice
from models.scene import Scene

_CACHE_FILENAME = "devices_cache.json"
_CONFIG_FILENAME = "config.json"


def _app_data_dir() -> Path:
    # QStandardPaths.AppDataLocation, QCoreApplication.setApplicationName()
    # çağrılmadıysa çalıştırılan .exe/yorumlayıcının adına göre (örn.
    # "...\AppData\Roaming\python\") döner — "SmartLights" alt klasörünü
    # burada AÇIKÇA ekliyoruz ki plan madde 7.5'teki `%APPDATA%\SmartLights\`
    # yoluyla her koşulda eşleşsin (main.py uygulama adını ayarlasa da).
    app_data = QStandardPaths.writableLocation(QStandardPaths.AppDataLocation)
    base = Path(app_data) if app_data else Path.home() / ".smartlights"
    directory = base / "SmartLights" if base.name.lower() != "smartlights" else base
    directory.mkdir(parents=True, exist_ok=True)
    return directory


def _atomic_write(path: Path, payload: dict) -> None:
    tmp_path = path.with_suffix(".json.tmp")
    try:
        tmp_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
        os.replace(tmp_path, path)
    except OSError:  # noqa: BLE001 - yazılamazsa uygulama çökmesin
        pass


def _read_json(path: Path) -> dict:
    if not path.exists():
        return {}
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError):
        return {}


# ---------------- devices_cache.json ----------------


def load_cache() -> list[LightDevice]:
    """Son bilinen cihaz listesini döner; dosya yoksa/bozuksa boş liste döner, çökmez."""
    raw = _read_json(_app_data_dir() / _CACHE_FILENAME)
    devices: list[LightDevice] = []
    for entry in raw.get("devices", []):
        try:
            devices.append(LightDevice.model_validate(entry))
        except Exception:  # noqa: BLE001 - bozuk tek bir kayıt tüm cache'i geçersiz kılmasın
            continue
    return devices


def save_cache(states: list[LightDevice]) -> None:
    """Cihaz listesini atomic write ile kaydeder: önce .tmp'ye yaz, sonra os.replace."""
    payload = {"version": 1, "devices": [s.model_dump(mode="json") for s in states]}
    _atomic_write(_app_data_dir() / _CACHE_FILENAME, payload)


# ---------------- config.json (isim/oda override'ları + sahneler) ----------------


def load_config() -> tuple[dict[str, DeviceOverride], list[Scene]]:
    """(device_overrides, scenes) döner; dosya yoksa/bozuksa boş döner, çökmez."""
    raw = _read_json(_app_data_dir() / _CONFIG_FILENAME)

    overrides: dict[str, DeviceOverride] = {}
    for stable_id, entry in raw.get("device_overrides", {}).items():
        try:
            overrides[stable_id] = DeviceOverride.model_validate(entry)
        except Exception:  # noqa: BLE001
            continue

    scenes: list[Scene] = []
    for entry in raw.get("scenes", []):
        try:
            scenes.append(Scene.model_validate(entry))
        except Exception:  # noqa: BLE001
            continue

    return overrides, scenes


def save_config(device_overrides: dict[str, DeviceOverride], scenes: list[Scene]) -> None:
    payload = {
        "version": 1,
        "device_overrides": {k: v.model_dump(mode="json") for k, v in device_overrides.items()},
        "scenes": [s.model_dump(mode="json") for s in scenes],
    }
    _atomic_write(_app_data_dir() / _CONFIG_FILENAME, payload)

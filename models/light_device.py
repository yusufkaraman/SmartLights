"""Normalize edilmiş cihaz modeli (plan madde 14).

WiZ veya Tapo olduğunun UI/CLI için çoğu zaman önemi kalmaz: backend bu
modeli üretir, dış katman `device.brightness`, `device.rgb` gibi ortak
alanlara bakar.
"""

from __future__ import annotations

from typing import Optional

from pydantic import BaseModel

from models.capabilities import Capabilities


class LightDevice(BaseModel):
    # Kararlı kimlik: WiZ için MAC, Tapo için device_id (plan madde 2).
    id: str
    name: str
    room: Optional[str] = None

    brand: str  # "wiz" | "tapo"
    model: str
    ip: str

    online: bool = True
    rssi: Optional[int] = None

    power: Optional[bool] = None
    brightness: Optional[int] = None  # 0-100 (yüzde), her iki marka için de ortak birim

    rgb: Optional[tuple[int, int, int]] = None
    kelvin: Optional[int] = None
    min_kelvin: Optional[int] = None
    max_kelvin: Optional[int] = None

    capabilities: Capabilities = Capabilities()

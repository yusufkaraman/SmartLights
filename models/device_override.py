"""Kullanıcının bir cihaza verdiği özel isim/oda ataması.

Ağdan gelen isim (WiZ için IP, Tapo için cihaz takma adı) her zaman
kullanıcı dostu olmayabilir; kullanıcı bunu değiştirebilir ve bir odaya
atayabilir. `config.json`'da stable_id -> DeviceOverride olarak saklanır
(plan madde 7.5, 17).
"""

from __future__ import annotations

from typing import Optional

from pydantic import BaseModel


class DeviceOverride(BaseModel):
    name: Optional[str] = None
    room: Optional[str] = None

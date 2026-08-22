"""Sahne modeli (plan madde 18): marka bağımsız, birden fazla cihazı tek
komutla istenen duruma getirir. Tapo'nun kendi scene sistemi ile WiZ'nin
kendi scene sistemini kullanıcıya ayrı ayrı göstermek zorunda kalmayız.
"""

from __future__ import annotations

from typing import Optional

from pydantic import BaseModel


class SceneAction(BaseModel):
    device_id: str
    power: Optional[bool] = None
    brightness: Optional[int] = None
    rgb: Optional[tuple[int, int, int]] = None
    kelvin: Optional[int] = None


class Scene(BaseModel):
    id: str
    name: str
    icon: str = "✨"
    actions: list[SceneAction] = []

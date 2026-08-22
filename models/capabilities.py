"""Cihaz yetenekleri (plan madde 13: capability-driven UI).

UI/CLI katmanı `if brand == "wiz"` gibi dallanma yapmaz; bunun yerine her
cihazın capabilities'ine bakar. Örn. `rgb=False` olan bir cihazda renk
sekmesi hiç gösterilmez.
"""

from __future__ import annotations

from pydantic import BaseModel


class Capabilities(BaseModel):
    power: bool = True
    brightness: bool = True
    rgb: bool = False
    color_temperature: bool = False
    effects: bool = False
    effect_speed: bool = False

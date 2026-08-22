"""Ortak Device arayüzü (plan madde 13, 28).

Her marka adapter'ı (`WizDevice`, `TapoDevice`) bu soyut arayüze uyar.
CLI/UI katmanı bu arayüz üzerinden konuşur, hangi markanın altta
çalıştığını bilmesi gerekmez — farklılık `LightDevice.capabilities`
üzerinden ifade edilir.

Bir cihaz desteklemediği bir eylemi (örn. RGB'siz bir Tapo L510'da
set_rgb) çağırırsa `NotImplementedError` fırlatır; çağıran taraf önce
`capabilities` alanına bakarak bunu zaten engellemelidir.
"""

from __future__ import annotations

from abc import ABC, abstractmethod

from models.light_device import LightDevice


class LightController(ABC):
    @abstractmethod
    async def turn_on(self) -> None:
        """Cihazı açar."""

    @abstractmethod
    async def turn_off(self) -> None:
        """Cihazı kapatır."""

    @abstractmethod
    async def set_brightness(self, brightness: int) -> None:
        """Parlaklığı ayarlar. brightness: 1-100 (yüzde)."""

    @abstractmethod
    async def set_rgb(self, r: int, g: int, b: int) -> None:
        """RGB rengini ayarlar (0-255 her kanal). Desteklenmiyorsa NotImplementedError."""

    @abstractmethod
    async def set_kelvin(self, kelvin: int) -> None:
        """Beyaz ışık sıcaklığını Kelvin cinsinden ayarlar. Desteklenmiyorsa NotImplementedError."""

    @abstractmethod
    async def get_state(self) -> LightDevice:
        """Cihazın güncel durumunu normalize edilmiş `LightDevice` olarak döner."""

    @abstractmethod
    async def close(self) -> None:
        """Açık bağlantıları serbest bırakır (WiZ UDP transport vb.)."""

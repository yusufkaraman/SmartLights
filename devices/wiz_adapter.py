"""WiZ ampulleri için LightController implementasyonu (Faz 2).

Kararlı kimlik MAC'tir (plan madde 2). Discovery/keşif zaman aşımına
uğrarsa uygulama çökmez, boş liste döner (plan madde 4).
"""

from __future__ import annotations

import asyncio
import logging

from pywizlight import PilotBuilder, discovery, wizlight
from pywizlight.exceptions import WizLightNotKnownBulb
from pywizlight.utils import hex_to_percent, percent_to_hex

from devices.base import LightController
from models.capabilities import Capabilities
from models.light_device import LightDevice

logger = logging.getLogger(__name__)

DISCOVERY_TIMEOUT = 6.0


class WizDevice(LightController):
    """Tek bir WiZ ampulünü `LightController` arayüzü arkasında sarmalar."""

    def __init__(self, bulb: wizlight) -> None:
        self._bulb = bulb

    async def turn_on(self) -> None:
        await self._bulb.turn_on()

    async def turn_off(self) -> None:
        await self._bulb.turn_off()

    async def set_brightness(self, brightness: int) -> None:
        await self._bulb.turn_on(PilotBuilder(brightness=percent_to_hex(brightness)))

    async def set_rgb(self, r: int, g: int, b: int) -> None:
        await self._bulb.turn_on(PilotBuilder(rgb=(r, g, b)))

    async def set_kelvin(self, kelvin: int) -> None:
        await self._bulb.turn_on(PilotBuilder(colortemp=kelvin))

    async def get_state(self) -> LightDevice:
        bulb = self._bulb
        try:
            bulb_type = await bulb.get_bulbtype()
            await bulb.updateState()
        except (WizLightNotKnownBulb, OSError, asyncio.TimeoutError) as exc:
            logger.warning("WiZ %s bilgisi alınamadı: %s", bulb.ip, exc)
            return LightDevice(
                id=bulb.mac or bulb.ip,
                name=bulb.ip,
                brand="wiz",
                model="Bilinmiyor",
                ip=bulb.ip,
                online=False,
                capabilities=Capabilities(power=False, brightness=False),
            )

        # bulb.state, çok başlıklı (dual head) cihazlar için liste tutar;
        # ana ışık her zaman index 0'dadır.
        state = bulb.state[0] if bulb.state else None
        kelvin_range = bulb_type.kelvin_range

        rgb = None
        brightness_pct = None
        kelvin = None
        if state is not None:
            raw_rgb = state.get_rgb()
            if all(v is not None for v in raw_rgb):
                rgb = (int(raw_rgb[0]), int(raw_rgb[1]), int(raw_rgb[2]))
            raw_brightness = state.get_brightness()
            if raw_brightness is not None:
                brightness_pct = hex_to_percent(raw_brightness)
            kelvin = state.get_colortemp()

        return LightDevice(
            id=bulb.mac,
            name=bulb.ip,
            brand="wiz",
            model=bulb_type.name or "WiZ",
            ip=bulb.ip,
            online=bulb.status is not None,
            power=bulb.status,
            brightness=brightness_pct,
            rgb=rgb,
            kelvin=kelvin,
            min_kelvin=kelvin_range.min if kelvin_range else None,
            max_kelvin=kelvin_range.max if kelvin_range else None,
            capabilities=Capabilities(
                power=True,
                brightness=bulb_type.features.brightness,
                rgb=bulb_type.features.color,
                color_temperature=bulb_type.features.color_tmp,
                effects=bulb_type.features.effect,
            ),
        )

    async def close(self) -> None:
        await self._bulb.async_close()


# Windows soket hata kodları (winerror). errno da aynı sayısal değeri taşır
# ama winerror daha güvenilir; ikisine de bakıyoruz (bkz. _classify_os_error).
_WSAEACCES = 10013  # İzin reddedildi -> genelde Windows Güvenlik Duvarı
_WSA_NO_NETWORK = {10050, 10051, 10064, 10065}  # ağ kapalı/erişilemez


def _classify_os_error(exc: OSError) -> str:
    """OSError'ı plan madde 9'daki senaryolara göre kullanıcıya anlamlı mesaja çevirir."""
    code = getattr(exc, "winerror", None) or exc.errno
    if code == _WSAEACCES:
        return "WiZ: Windows Güvenlik Duvarı ağ yayınını (UDP broadcast) engelliyor olabilir"
    if code in _WSA_NO_NETWORK:
        return "WiZ: ağ bağlantısı yok (Wi-Fi kapalı olabilir)"
    return f"WiZ: ağ hatası ({exc})"


async def discover(timeout: float = DISCOVERY_TIMEOUT) -> tuple[list[WizDevice], str | None]:
    """LAN'da WiZ ampullerini tara.

    Dönüş: (bulunan cihazlar, hata mesajı). Zaman aşımı bir HATA SAYILMAZ
    (plan madde 4) — sadece "bu turda cihaz bulunamadı" demektir, hata
    mesajı None döner. Ağ/firewall sorunları ise kullanıcıya gösterilecek
    ayrı bir mesajla döner (plan madde 9).
    """
    try:
        bulbs = await asyncio.wait_for(
            discovery.discover_lights(wait_time=timeout), timeout=timeout + 3
        )
    except asyncio.TimeoutError:
        logger.warning("WiZ discovery zaman aşımına uğradı")
        return [], None
    except OSError as exc:
        message = _classify_os_error(exc)
        logger.warning("WiZ discovery ağ hatası: %s", exc)
        return [], message
    return [WizDevice(bulb) for bulb in bulbs], None

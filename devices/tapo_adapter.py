"""Tapo ampulleri için LightController implementasyonu (Faz 2).

Kararlı kimlik device_id'dir (plan madde 2). RGB, `tapo` kütüphanesinde
doğrudan desteklenmiyor; set_hue_saturation ile veriliyor, o yüzden burada
RGB <-> HSV dönüşümü yapılıyor.

Not: yalnızca `ColorLightHandler` (L530/L535/L630) ve ışık şeritleri RGB +
Kelvin destekler. Düz `LightHandler` (L510/L520/L610) sadece aç/kapat +
parlaklık destekler — `set_color_temperature`/`set_hue_saturation` bu
handler'da yok, bu yüzden capability'leri handler tipine göre ayırıyoruz.
"""

from __future__ import annotations

import colorsys
import logging
from dataclasses import dataclass, field
from typing import Any

from tapo import ApiClient, DiscoveryResult

from devices.base import LightController
from models.capabilities import Capabilities
from models.light_device import LightDevice

logger = logging.getLogger(__name__)

DISCOVERY_TIMEOUT = 6
BROADCAST_ADDRESS = "255.255.255.255"

# RGB + Kelvin destekleyen handler tipleri (ColorLightHandler ve ışık şeritleri).
_COLOR_CAPABLE = (
    DiscoveryResult.ColorLight,
    DiscoveryResult.RgbLightStrip,
    DiscoveryResult.RgbicLightStrip,
)


class TapoDevice(LightController):
    """Tek bir Tapo cihazını `LightController` arayüzü arkasında sarmalar."""

    def __init__(self, discovery_result: Any) -> None:
        self._result = discovery_result
        self._handle = discovery_result.handler
        self._supports_color = isinstance(discovery_result, _COLOR_CAPABLE)

    async def turn_on(self) -> None:
        await self._handle.on()

    async def turn_off(self) -> None:
        await self._handle.off()

    async def set_brightness(self, brightness: int) -> None:
        await self._handle.set_brightness(brightness)

    async def set_rgb(self, r: int, g: int, b: int) -> None:
        if not self._supports_color:
            raise NotImplementedError("Bu Tapo cihazı RGB desteklemiyor")
        h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
        hue = round(h * 360) % 360
        saturation = max(1, round(s * 100))
        brightness = max(1, round(v * 100))
        await self._handle.set_hue_saturation(hue, saturation)
        await self._handle.set_brightness(brightness)

    async def set_kelvin(self, kelvin: int) -> None:
        if not self._supports_color:
            raise NotImplementedError("Bu Tapo cihazı Kelvin ayarını desteklemiyor")
        await self._handle.set_color_temperature(kelvin)

    async def get_state(self) -> LightDevice:
        try:
            info = await self._handle.get_device_info()
        except Exception as exc:  # noqa: BLE001 - cihaz yanıt vermese bile çökme
            logger.warning("Tapo %s durumu alınamadı: %s", self._result.ip, exc)
            return LightDevice(
                id=self._result.device_id,
                name=self._result.nickname,
                brand="tapo",
                model=self._result.model,
                ip=self._result.ip,
                online=False,
                capabilities=Capabilities(power=False, brightness=False),
            )

        rgb = None
        kelvin = None
        if self._supports_color:
            hue = getattr(info, "hue", None)
            saturation = getattr(info, "saturation", None)
            if hue is not None and saturation is not None:
                r, g, b = colorsys.hsv_to_rgb(hue / 360, saturation / 100, info.brightness / 100)
                rgb = (round(r * 255), round(g * 255), round(b * 255))
            kelvin = getattr(info, "color_temp", None) or None

        return LightDevice(
            id=info.device_id,
            name=info.nickname,
            brand="tapo",
            model=info.model,
            ip=info.ip,
            online=True,
            rssi=info.rssi,
            power=info.device_on,
            brightness=info.brightness,
            rgb=rgb,
            kelvin=kelvin,
            # tapo kütüphanesi cihaz başına kelvin aralığı döndürmüyor; 2500-6500
            # `set_color_temperature`'ın kendi API dokümanında sabitlenmiş sınır.
            min_kelvin=2500 if self._supports_color else None,
            max_kelvin=6500 if self._supports_color else None,
            capabilities=Capabilities(
                power=True,
                brightness=True,
                rgb=self._supports_color,
                color_temperature=self._supports_color,
            ),
        )

    async def close(self) -> None:
        # Tapo handler'ları kalıcı bir bağlantı/transport tutmuyor, kapatılacak bir şey yok.
        return None


@dataclass
class TapoDiscoveryResult:
    devices: list[TapoDevice] = field(default_factory=list)
    error: str | None = None
    auth_failed: bool = False


def _is_auth_error(exc: Exception) -> bool:
    """Tapo kimlik doğrulama hatasını (yanlış e-posta/şifre) ağ hatasından ayırır.

    `tapo` kütüphanesi Rust tarafından gelen hatayı örn.
    `Unauthorized { kind: "HASH_MISMATCH", ... }` şeklinde metne döküyor —
    gerçek bir sınıf hiyerarşisi olarak gelmiyor, bu yüzden metin üzerinden
    tespit ediyoruz.
    """
    text = str(exc)
    # Kilitlenme veya oturum zaman aşımı yanlış parola anlamına gelmez.
    if "TPAP_AUTH_ATTEMPTS_LIMIT" in text or "SESSION_TIMEOUT" in text:
        return False
    return "Unauthorized" in text or "HASH_MISMATCH" in text


async def discover(
    email: str,
    password: str,
    broadcast: str = BROADCAST_ADDRESS,
    timeout: int = DISCOVERY_TIMEOUT,
) -> TapoDiscoveryResult:
    """LAN'da Tapo cihazlarını tara.

    Kimlik doğrulama hatası (yanlış e-posta/şifre, plan madde 9) ağ
    hatasından ayrı işaretlenir (`auth_failed=True`) — bu, çağıran tarafın
    saklı yanlış bilgiyi temizleyip kullanıcıdan yeniden istemesini sağlar.
    """
    client = ApiClient(email, password)
    result = TapoDiscoveryResult()
    try:
        async for maybe_result in await client.discover_devices(broadcast, timeout_s=timeout):
            try:
                device_result = maybe_result.get()
            except Exception as exc:  # noqa: BLE001 - tek cihaz hatası akışı durdurmasın
                if _is_auth_error(exc):
                    result.auth_failed = True
                logger.warning("Tapo cihazı okunamadı: %s", exc)
                continue
            if not hasattr(device_result, "handler"):
                # Kamera, priz vb. henüz desteklemediğimiz cihaz tipleri.
                logger.info("Desteklenmeyen Tapo cihazı atlandı: %s", device_result.model)
                continue
            result.devices.append(TapoDevice(device_result))
    except Exception as exc:  # noqa: BLE001 - kimlik/ağ hatasında uygulama çökmesin
        logger.warning("Tapo discovery hata: %s", exc)
        if _is_auth_error(exc):
            result.auth_failed = True
        else:
            result.error = f"Tapo: ağ hatası ({exc})"

    if result.auth_failed:
        result.error = "Tapo: kimlik bilgileri hatalı"
    return result

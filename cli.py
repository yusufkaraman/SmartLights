"""SmartLights - CLI debug aracı (eski main.py, Faz 1-2).

Faz 3'ten itibaren gerçek uygulama main.py'de (QML/PySide6). Bu dosya,
adapter/model katmanını UI olmadan hızlıca test etmek için saklanıyor —
`if brand == "wiz"` gibi dallanma yapmıyor (plan madde 13); her cihaz
`LightController` arayüzüne uyuyor (devices/base.py), farklılık yalnızca
`LightDevice.capabilities` üzerinden ifade ediliyor.
"""

from __future__ import annotations

import asyncio
import logging

from core.credentials import get_tapo_credentials
from devices import tapo_adapter, wiz_adapter
from devices.base import LightController
from models.light_device import LightDevice

logging.basicConfig(level=logging.WARNING, format="%(levelname)s: %(message)s")


async def scan_devices() -> list[LightController]:
    print("Cihazlar aranıyor (WiZ + Tapo, birkaç saniye sürebilir)...")

    email, password = get_tapo_credentials()

    (wiz_devices, wiz_error), tapo_result = await asyncio.gather(
        wiz_adapter.discover(),
        tapo_adapter.discover(email, password),
    )

    if wiz_error:
        print(f"⚠ {wiz_error}")
    elif not wiz_devices:
        print("⚠ WiZ: cihaz bulunamadı (zaman aşımı veya ağda yok)")

    if tapo_result.error:
        print(f"⚠ {tapo_result.error}")
    elif not tapo_result.devices:
        print("⚠ Tapo: cihaz bulunamadı (zaman aşımı veya ağda yok)")

    return [*wiz_devices, *tapo_result.devices]


async def print_devices(devices: list[LightController]) -> list[LightDevice]:
    states = await asyncio.gather(*(dev.get_state() for dev in devices))

    if not states:
        print("\nHiç cihaz bulunamadı.")
        return states

    print(f"\n{len(states)} cihaz bulundu:\n")
    for i, state in enumerate(states, start=1):
        status = "●" if state.online else "○"
        caps = []
        if state.capabilities.rgb:
            caps.append("RGB")
        if state.capabilities.color_temperature:
            caps.append("Kelvin")
        caps_str = ", ".join(caps) if caps else "sadece aç/kapat"
        print(
            f"  [{i}] {status} {state.brand.upper():5} {state.model:14} "
            f"{state.name:20} {state.ip:16} ({caps_str})"
        )
    return states


async def run_action(device: LightController, state: LightDevice) -> None:
    print(
        "\n  1) Aç   2) Kapat   3) RGB renk   4) Kelvin (beyaz)   5) Parlaklık   0) Geri"
    )
    choice = input("  Seçim: ").strip()

    try:
        if choice == "1":
            await device.turn_on()
            print("  ✓ Açıldı")
        elif choice == "2":
            await device.turn_off()
            print("  ✓ Kapatıldı")
        elif choice == "3":
            if not state.capabilities.rgb:
                print("  ⚠ Bu cihaz RGB desteklemiyor")
                return
            raw = input("  R G B (0-255, boşlukla, örn: 255 80 20): ").strip()
            r, g, b = (int(x) for x in raw.split())
            await device.set_rgb(r, g, b)
            print(f"  ✓ Renk ayarlandı: ({r}, {g}, {b})")
        elif choice == "4":
            if not state.capabilities.color_temperature:
                print("  ⚠ Bu cihaz Kelvin ayarını desteklemiyor")
                return
            lo = state.min_kelvin or 2200
            hi = state.max_kelvin or 6500
            raw = input(f"  Kelvin ({lo}-{hi}): ").strip()
            kelvin = int(raw)
            await device.set_kelvin(kelvin)
            print(f"  ✓ Kelvin ayarlandı: {kelvin}K")
        elif choice == "5":
            if not state.capabilities.brightness:
                print("  ⚠ Bu cihaz parlaklık ayarını desteklemiyor")
                return
            raw = input("  Parlaklık (1-100): ").strip()
            brightness = int(raw)
            await device.set_brightness(brightness)
            print(f"  ✓ Parlaklık ayarlandı: %{brightness}")
        elif choice == "0":
            return
        else:
            print("  Geçersiz seçim")
    except (ValueError, IndexError):
        print("  ⚠ Geçersiz değer girdiniz")
    except NotImplementedError as exc:
        print(f"  ⚠ {exc}")
    except Exception as exc:  # noqa: BLE001 - cihaz yanıt vermese bile CLI çökmesin
        print(f"  ⚠ {state.name} yanıt vermedi: {exc}")


async def main() -> None:
    devices = await scan_devices()
    states = await print_devices(devices)

    try:
        while devices:
            raw = input("\nCihaz seç (numara, 'r' yeniden tara, 'q' çıkış): ").strip().lower()
            if raw == "q":
                break
            if raw == "r":
                devices = await scan_devices()
                states = await print_devices(devices)
                continue
            try:
                index = int(raw) - 1
                if not 0 <= index < len(devices):
                    raise ValueError
            except ValueError:
                print("Geçersiz numara")
                continue
            await run_action(devices[index], states[index])
    finally:
        for dev in devices:
            await dev.close()


if __name__ == "__main__":
    asyncio.run(main())

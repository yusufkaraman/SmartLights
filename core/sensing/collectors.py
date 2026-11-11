"""Ham ölçüm toplayıcıları (yalnızca okuma).

Her toplayıcı bir asyncio görevidir ve ``emit(kayit)`` ile düz sözlükler üretir:
``{"src": "...", ...alanlar}``; zaman damgasını kaydedici ekler. Hiçbiri ışıkların
durumunu değiştirmez; WiZ'e yalnızca ``getPilot`` ve ``registration`` gider, Tapo'ya
yalnızca ``get_device_info``.

Güvenlik: WiZ yanıtı beklenen MAC'ten gelmiyorsa kayıt atılır. Böylece DHCP bir IP'yi
başka bir ampule (ör. aynı ağdaki başka birinin WiZ'i) verirse ona istek gitmeye devam etmez.
"""

from __future__ import annotations

import asyncio
import ctypes
import ctypes.wintypes as wt
import json
import socket
import time
from typing import Any, Awaitable, Callable

Emit = Callable[[dict[str, Any]], None]
WIZ_PORT = 38899
WIZ_PUSH_PORT = 38900


# --- WiZ -------------------------------------------------------------------------------------------------------

def find_wiz(mac: str, timeout: float = 3.0) -> str | None:
    """Yayınla getSystemConfig sorar, verilen MAC'in IP'sini döner (salt okuma)."""
    mac = mac.lower().replace(":", "")
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    s.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
    s.settimeout(0.5)
    s.sendto(b'{"method":"getSystemConfig","params":{}}', ("255.255.255.255", WIZ_PORT))
    end = time.monotonic() + timeout
    try:
        while time.monotonic() < end:
            try:
                data, addr = s.recvfrom(4096)
            except socket.timeout:
                continue
            res = json.loads(data).get("result", {})
            if res.get("mac", "").lower() == mac:
                return addr[0]
    finally:
        s.close()
    return None


class _WizClient(asyncio.DatagramProtocol):
    def __init__(self) -> None:
        self.waiter: asyncio.Future | None = None

    def datagram_received(self, data: bytes, addr: Any) -> None:
        if self.waiter and not self.waiter.done():
            self.waiter.set_result(data)


async def wiz_poll(ip: str, mac: str, hz: float, emit: Emit) -> None:
    """getPilot'u ``hz`` hızında sorar; rssi, durum ve yanıt süresini kaydeder."""
    mac = mac.lower().replace(":", "")
    loop = asyncio.get_running_loop()
    transport, proto = await loop.create_datagram_endpoint(_WizClient, remote_addr=(ip, WIZ_PORT))
    period = 1.0 / hz
    try:
        while True:
            t0 = time.perf_counter()
            proto.waiter = loop.create_future()
            transport.sendto(b'{"method":"getPilot","params":{}}')
            try:
                data = await asyncio.wait_for(proto.waiter, timeout=0.8)
                rtt = (time.perf_counter() - t0) * 1000
                res = json.loads(data).get("result", {})
                if res.get("mac", "").lower() != mac:
                    emit({"src": "wiz_poll", "error": "mac_mismatch"})
                else:
                    emit({"src": "wiz_poll", "rssi": res.get("rssi"), "on": res.get("state"),
                          "dim": res.get("dimming"), "rtt_ms": round(rtt, 1)})
            except asyncio.TimeoutError:
                emit({"src": "wiz_poll", "error": "timeout"})
            await asyncio.sleep(max(0.0, period - (time.perf_counter() - t0)))
    finally:
        transport.close()


class _PushListener(asyncio.DatagramProtocol):
    def __init__(self, ip: str, mac: str, emit: Emit) -> None:
        self.ip, self.mac, self.emit = ip, mac, emit

    def datagram_received(self, data: bytes, addr: Any) -> None:
        if addr[0] != self.ip:
            return
        try:
            msg = json.loads(data)
        except ValueError:
            return
        params = msg.get("params", {})
        if params.get("mac", "").lower() not in ("", self.mac):
            return
        # Yalnızca ölçüm ve kaynak alanları; renk ayrıntıları gerekmez.
        self.emit({"src": "wiz_push", "method": msg.get("method"), "rssi": params.get("rssi"),
                   "from": params.get("src"), "on": params.get("state"), "dim": params.get("dimming")})


def _local_ip_towards(ip: str) -> str:
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect((ip, WIZ_PORT))
        return s.getsockname()[0]
    finally:
        s.close()


async def wiz_push(ip: str, mac: str, emit: Emit, every_s: float = 15.0) -> None:
    """Ampule 'bana durum gönder' kaydı yapar ve syncPilot / firstBeat mesajlarını dinler.

    Kayıt kalıcı bir ayar değildir; yenilenmezse ampul bir süre sonra göndermeyi bırakır.
    """
    mac = mac.lower().replace(":", "")
    loop = asyncio.get_running_loop()
    listen, _ = await loop.create_datagram_endpoint(lambda: _PushListener(ip, mac, emit), local_addr=("0.0.0.0", WIZ_PUSH_PORT))
    me = _local_ip_towards(ip)
    reg = json.dumps({"method": "registration", "params": {"phoneIp": me, "register": True,
                      "phoneMac": "aabbccddeeff", "id": 1}}).encode()
    sender, _ = await loop.create_datagram_endpoint(asyncio.DatagramProtocol, remote_addr=(ip, WIZ_PORT))
    try:
        while True:
            sender.sendto(reg)
            await asyncio.sleep(every_s)
    finally:
        sender.close()
        listen.close()


# --- Tapo ------------------------------------------------------------------------------------------------------

async def tapo_poll(ip: str, hz: float, emit: Emit) -> None:
    """get_device_info'yu ``hz`` hızında sorar (KLAP oturumu yeniden kullanılır)."""
    from tapo import ApiClient

    from core.credentials import get_stored_tapo_credentials

    creds = get_stored_tapo_credentials()
    if not creds:
        emit({"src": "tapo", "error": "no_credentials"})
        return
    def auth_error(exc: Exception) -> bool:
        text = str(exc)
        return any(k in text for k in ("Unauthorized", "HASH_MISMATCH", "InvalidCredentials"))

    client = ApiClient(*creds)
    try:
        dev = await client.l535(ip)
    except Exception as exc:
        # Yanlış e-posta/şifre ile tekrar tekrar denemek Tapo hesabını kilitleyebilir: bir kez dene, dur.
        emit({"src": "tapo", "error": "auth" if auth_error(exc) else type(exc).__name__})
        return
    period = 1.0 / hz
    while True:
        t0 = time.perf_counter()
        try:
            info = (await dev.get_device_info()).to_dict()
            emit({"src": "tapo", "rssi": info.get("rssi"), "level": info.get("signal_level"),
                  "on": info.get("device_on"), "rtt_ms": round((time.perf_counter() - t0) * 1000, 1)})
        except Exception as exc:
            emit({"src": "tapo", "error": "auth" if auth_error(exc) else type(exc).__name__})
            if auth_error(exc):
                return
            await asyncio.sleep(5)  # oturum düştüyse yeniden bağlanmadan önce bekle
            try:
                dev = await client.l535(ip)
            except Exception:
                return
        await asyncio.sleep(max(0.0, period - (time.perf_counter() - t0)))


# --- PC Wi-Fi (Windows WLAN API) -------------------------------------------------------------------------------

class _GUID(ctypes.Structure):
    _fields_ = [("d1", wt.DWORD), ("d2", wt.WORD), ("d3", wt.WORD), ("d4", ctypes.c_ubyte * 8)]


class _IfInfo(ctypes.Structure):
    _fields_ = [("guid", _GUID), ("desc", ctypes.c_wchar * 256), ("state", wt.DWORD)]


class _IfList(ctypes.Structure):
    _fields_ = [("n", wt.DWORD), ("i", wt.DWORD), ("items", _IfInfo * 8)]


_RSSI_OPCODE = 0x10000102  # wlan_intf_opcode_rssi


class WlanRssi:
    """Bağlı Wi-Fi arayüzünün AP RSSI'ını (dBm) okur."""

    def __init__(self) -> None:
        self.api = ctypes.windll.wlanapi
        self.handle = wt.HANDLE()
        ver = wt.DWORD()
        if self.api.WlanOpenHandle(2, None, ctypes.byref(ver), ctypes.byref(self.handle)):
            raise OSError("WlanOpenHandle")
        plist = ctypes.POINTER(_IfList)()
        if self.api.WlanEnumInterfaces(self.handle, None, ctypes.byref(plist)):
            raise OSError("WlanEnumInterfaces")
        items = [plist.contents.items[k] for k in range(plist.contents.n)]
        connected = [it for it in items if it.state == 1] or items  # 1 = connected
        if not connected:
            raise OSError("Wi-Fi arayüzü yok")
        self.guid = connected[0].guid

    def read(self) -> int | None:
        size = wt.DWORD()
        data = ctypes.c_void_p()
        kind = wt.DWORD()
        if self.api.WlanQueryInterface(self.handle, ctypes.byref(self.guid), _RSSI_OPCODE, None,
                                       ctypes.byref(size), ctypes.byref(data), ctypes.byref(kind)):
            return None
        value = ctypes.cast(data, ctypes.POINTER(ctypes.c_long)).contents.value
        self.api.WlanFreeMemory(data)
        return int(value)


async def pc_wifi(hz: float, emit: Emit) -> None:
    wlan = WlanRssi()
    while True:
        emit({"src": "pc_wifi", "rssi": wlan.read()})
        await asyncio.sleep(1.0 / hz)


# --- PC giriş boşta süresi -------------------------------------------------------------------------------------

class _LastInput(ctypes.Structure):
    _fields_ = [("cbSize", wt.UINT), ("dwTime", wt.DWORD)]


def idle_seconds() -> float:
    li = _LastInput(ctypes.sizeof(_LastInput), 0)
    ctypes.windll.user32.GetLastInputInfo(ctypes.byref(li))
    return (ctypes.windll.kernel32.GetTickCount() - li.dwTime) / 1000.0


async def pc_idle(hz: float, emit: Emit) -> None:
    while True:
        emit({"src": "pc_idle", "idle_s": round(idle_seconds(), 1)})
        await asyncio.sleep(1.0 / hz)


# --- Bluetooth LE reklamları -----------------------------------------------------------------------------------

async def ble_scan(emit: Emit) -> None:
    """Gelen her BLE reklamını (adres, ad, RSSI, üretici kimlikleri) kaydeder; pasif dinleme."""
    from bleak import BleakScanner

    def seen(device: Any, adv: Any) -> None:
        emit({"src": "ble", "addr": device.address, "name": adv.local_name or device.name, "rssi": adv.rssi,
              "mfr": sorted(adv.manufacturer_data.keys()), "uuids": adv.service_uuids[:4]})

    async with BleakScanner(detection_callback=seen):
        await asyncio.Event().wait()


# --- Telefon Wi-Fi (kablosuz hata ayıklama üzerinden, uygulama değişikliği gerekmez) ----------------------------

async def phone_wifi(adb: str, serial: str, hz: float, emit: Emit) -> None:
    """Telefonun bağlı olduğu AP'den ölçtüğü RSSI ve bağlantı hızını ``adb shell cmd wifi status`` ile okur.

    Telefon burada sabit bir radyo düğümüdür (ör. masada); yakınlığı "insan var" sayılmaz.
    """
    import re

    period = 1.0 / hz
    while True:
        t0 = time.perf_counter()
        try:
            proc = await asyncio.create_subprocess_exec(adb, "-s", serial, "shell", "cmd", "wifi", "status",
                                                        stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.DEVNULL)
            out, _ = await asyncio.wait_for(proc.communicate(), timeout=3)
            text = out.decode(errors="replace")
            rssi = re.search(r"RSSI: ?(-?\d+)", text)
            speed = re.search(r"Link speed: ?(\d+)", text)
            emit({"src": "phone_wifi", "rssi": int(rssi.group(1)) if rssi else None,
                  "mbps": int(speed.group(1)) if speed else None, "rtt_ms": round((time.perf_counter() - t0) * 1000, 1)})
        except Exception as exc:
            emit({"src": "phone_wifi", "error": type(exc).__name__})
        await asyncio.sleep(max(0.0, period - (time.perf_counter() - t0)))


def find_adb_device(adb: str) -> str | None:
    """Bağlı ilk adb cihazının seri numarası (kablosuz hata ayıklama: ip:port)."""
    import subprocess

    try:
        out = subprocess.run([adb, "devices"], capture_output=True, text=True, timeout=5).stdout
    except Exception:
        return None
    for line in out.splitlines()[1:]:
        parts = line.split()
        if len(parts) == 2 and parts[1] == "device":
            return parts[0]
    return None


Collector = Callable[..., Awaitable[None]]

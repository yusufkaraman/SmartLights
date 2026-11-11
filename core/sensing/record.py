"""Algılama deneyleri için kaydedici + etiketleme aracı.

Örnek (PowerShell, proje kökünden):
    .venv\\Scripts\\python -m core.sensing.record --wiz-mac <MAC> --tapo-ip <IP> --minutes 30 --note "bos oda"

Kaynakları ``--sources`` ile seçin (varsayılan: hepsi). Kayıt sırasında tuşlar gerçek durumu etiketler:
    g giriş · c çıkış · k kapı önünden geçiş · y odada yürüyor · a ayakta duruyor · o oturuyor · u uyku
    f fan/perde · m ışığı elle değiştirdim · b oda boş · q bitir
Çıktı: %LOCALAPPDATA%\\SmartLights\\sensing\\<tarih-saat>.jsonl (depoya girmez; MAC/IP içerir).
"""

from __future__ import annotations

import argparse
import asyncio
import json
import os
import sys
import threading
import time
from datetime import datetime
from pathlib import Path

from core.sensing import collectors as c

LABELS = {"g": "giris", "c": "cikis", "k": "kapi_onu", "o": "oturuyor", "u": "uyku", "f": "fan_perde", "b": "bos",
          "y": "yuruyor", "m": "manuel_isik", "a": "ayakta_duruyor"}
SOURCES = ("wiz_poll", "wiz_push", "tapo", "pc_wifi", "pc_idle", "ble", "phone_wifi")


def out_dir() -> Path:
    d = Path(os.environ.get("LOCALAPPDATA", Path.home())) / "SmartLights" / "sensing"
    d.mkdir(parents=True, exist_ok=True)
    return d


class Recorder:
    def __init__(self, path: Path) -> None:
        self.f = path.open("a", encoding="utf-8")
        self.lock = threading.Lock()
        self.counts: dict[str, int] = {}

    def emit(self, rec: dict) -> None:
        rec = {"t": round(time.time(), 3), **rec}
        with self.lock:
            self.f.write(json.dumps(rec, ensure_ascii=False) + "\n")
            self.counts[rec["src"]] = self.counts.get(rec["src"], 0) + 1

    def close(self) -> None:
        self.f.close()


def _keys(rec: Recorder, stop: threading.Event) -> None:
    import msvcrt

    while not stop.is_set():
        if msvcrt.kbhit():
            ch = msvcrt.getwch().lower()
            if ch == "q":
                stop.set()
                return
            if ch in LABELS:
                rec.emit({"src": "label", "label": LABELS[ch]})
                print(f"  [{datetime.now():%H:%M:%S}] etiket: {LABELS[ch]}")
        time.sleep(0.05)


async def run(args: argparse.Namespace) -> Path:
    path = out_dir() / f"{datetime.now():%Y%m%d-%H%M%S}.jsonl"
    rec = Recorder(path)
    rec.emit({"src": "meta", "note": args.note, "sources": args.sources, "wiz_hz": args.wiz_hz, "tapo_hz": args.tapo_hz})
    tasks = []
    want = set(args.sources)
    if want & {"wiz_poll", "wiz_push"}:
        ip = c.find_wiz(args.wiz_mac) if args.wiz_mac else None
        if not ip:
            print("WiZ bulunamadı (MAC verilmedi ya da yanıt yok); WiZ kaynakları atlandı.")
        else:
            if "wiz_poll" in want:
                tasks.append(c.wiz_poll(ip, args.wiz_mac, args.wiz_hz, rec.emit))
            if "wiz_push" in want:
                tasks.append(c.wiz_push(ip, args.wiz_mac, rec.emit))
    if "tapo" in want and args.tapo_ip:
        tasks.append(c.tapo_poll(args.tapo_ip, args.tapo_hz, rec.emit))
    if "pc_wifi" in want:
        tasks.append(c.pc_wifi(2.0, rec.emit))
    if "pc_idle" in want:
        tasks.append(c.pc_idle(1.0, rec.emit))
    if "ble" in want:
        tasks.append(c.ble_scan(rec.emit))
    if "phone_wifi" in want:
        serial = c.find_adb_device(args.adb)
        if serial:
            tasks.append(c.phone_wifi(args.adb, serial, 1.0, rec.emit))
        else:
            print("Telefon adb ile bağlı değil (kablosuz hata ayıklama); phone_wifi atlandı.")

    stop = threading.Event()
    if sys.stdin.isatty():
        threading.Thread(target=_keys, args=(rec, stop), daemon=True).start()
        print("Kaydediliyor. Etiketler: " + " · ".join(f"{k}={v}" for k, v in LABELS.items()) + " · q=bitir")
    running = [asyncio.ensure_future(t) for t in tasks]
    end = time.monotonic() + args.minutes * 60
    try:
        while time.monotonic() < end and not stop.is_set():
            await asyncio.sleep(0.5)
            for t in running:
                if t.done() and t.exception():
                    rec.emit({"src": "error", "error": repr(t.exception())})
                    running.remove(t)
                    break
    finally:
        stop.set()
        for t in running:
            t.cancel()
        await asyncio.gather(*running, return_exceptions=True)
        rec.close()
    print(f"Bitti: {path}  " + ", ".join(f"{k}={v}" for k, v in sorted(rec.counts.items())))
    return path


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    p = argparse.ArgumentParser(description="SmartLights algılama kaydedicisi (yalnızca okuma)")
    p.add_argument("--wiz-mac", help="kendi WiZ ampulünün MAC'i (yalnızca bu ampule istek gider)")
    p.add_argument("--tapo-ip", help="kendi Tapo ampulünün IP'si")
    p.add_argument("--minutes", type=float, default=10)
    p.add_argument("--wiz-hz", type=float, default=4)
    p.add_argument("--tapo-hz", type=float, default=1)
    p.add_argument("--sources", nargs="+", default=list(SOURCES), choices=SOURCES)
    p.add_argument("--note", default="")
    default_adb = Path(os.environ.get("LOCALAPPDATA", "")) / "Android" / "sdk" / "platform-tools" / "adb.exe"
    p.add_argument("--adb", default=str(default_adb), help="adb yolu (telefon Wi-Fi ölçümü için)")
    asyncio.run(run(p.parse_args()))


if __name__ == "__main__":
    main()

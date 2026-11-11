"""Kayıt özeti (E0 + E1): her kaynağın gerçek örnekleme hızı, RSSI çözünürlüğü ve oynaklığı; BLE yayıncıları.

    .venv\\Scripts\\python -m core.sensing.analyze <kayit.jsonl>
"""

from __future__ import annotations

import json
import statistics as st
import sys
from collections import defaultdict


def summarize(path: str) -> str:
    rows = [json.loads(line) for line in open(path, encoding="utf-8")]
    by: dict[str, list[dict]] = defaultdict(list)
    for r in rows:
        by[r["src"]].append(r)
    t0 = min(r["t"] for r in rows)
    t1 = max(r["t"] for r in rows)
    out = [f"Kayıt: {path}", f"Süre: {(t1 - t0) / 60:.1f} dk, {len(rows)} satır"]
    for src in ("wiz_poll", "wiz_push", "tapo", "pc_wifi", "pc_idle"):
        rs = by.get(src, [])
        if not rs:
            continue
        ok = [r for r in rs if "error" not in r]
        errs = len(rs) - len(ok)
        ts = [r["t"] for r in ok]
        gaps = [b - a for a, b in zip(ts, ts[1:])]
        line = f"\n[{src}] {len(ok)} örnek, {errs} hata"
        if gaps:
            line += f", aralık medyan {st.median(gaps) * 1000:.0f} ms (en uzun {max(gaps):.1f} s) → {len(ok) / max(1e-9, ts[-1] - ts[0]):.2f} Hz"
        out.append(line)
        vals = [r["rssi"] for r in ok if r.get("rssi") is not None]
        if vals:
            changes = sum(1 for a, b in zip(vals, vals[1:]) if a != b)
            out.append(f"  RSSI: {min(vals)}…{max(vals)} dBm, medyan {st.median(vals)}, std {st.pstdev(vals):.2f}, "
                       f"farklı değer {len(set(vals))}, değişim {changes} kez ({changes / max(1, len(vals) - 1) * 100:.0f}% örnekte)")
        rtts = [r["rtt_ms"] for r in ok if r.get("rtt_ms") is not None]
        if rtts:
            out.append(f"  Yanıt süresi: medyan {st.median(rtts):.0f} ms, p90 {sorted(rtts)[int(len(rtts) * 0.9)]:.0f} ms")
        if src == "wiz_push":
            kinds = defaultdict(int)
            for r in ok:
                kinds[f"{r.get('method')}/{r.get('from')}"] += 1
            out.append("  Mesajlar: " + ", ".join(f"{k}×{v}" for k, v in sorted(kinds.items())))
    ble = by.get("ble", [])
    if ble:
        dev: dict[str, list[dict]] = defaultdict(list)
        for r in ble:
            dev[r["addr"]].append(r)
        span = max(1e-9, t1 - t0)
        out.append(f"\n[ble] {len(ble)} reklam, {len(dev)} cihaz (en sık 25):")
        for addr, rs in sorted(dev.items(), key=lambda kv: -len(kv[1]))[:25]:
            names = {r.get("name") for r in rs if r.get("name")}
            mfr = sorted({m for r in rs for m in r.get("mfr", [])})
            rssi = [r["rssi"] for r in rs]
            out.append(f"  {addr}  {len(rs) / span:5.2f} Hz  RSSI {st.median(rssi):.0f} (std {st.pstdev(rssi):.1f})  "
                       f"ad={','.join(sorted(names)) or '-'}  üretici={mfr or '-'}")
    labels = by.get("label", [])
    if labels:
        out.append("\nEtiketler: " + ", ".join(f"{r['label']}@{r['t'] - t0:.0f}s" for r in labels))
    return "\n".join(out)


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    print(summarize(sys.argv[1]))

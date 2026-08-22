"""Ayrı asyncio worker thread (plan madde 8).

Qt UI thread'i ile asyncio event loop'u doğrudan birbirine bağlamıyoruz
(QtAsyncio hâlâ technical preview). Bunun yerine asyncio kendi thread'inde
çalışır; Qt tarafı `submit()` ile coroutine gönderir ve sonucu
`concurrent.futures.Future` üzerinden (done-callback ile) alır. Qt Signal
bu callback içinden emit edilirse, farklı thread'den emit olduğu için Qt
otomatik olarak queued connection kullanır — bu yüzden thread-safe'tir.
"""

from __future__ import annotations

import asyncio
import threading
from concurrent.futures import Future
from typing import Any, Coroutine


class AsyncWorker:
    def __init__(self) -> None:
        self._loop = asyncio.new_event_loop()
        self._ready = threading.Event()
        self._thread = threading.Thread(target=self._run_loop, daemon=True)
        self._thread.start()
        self._ready.wait()

    def _run_loop(self) -> None:
        asyncio.set_event_loop(self._loop)
        self._ready.set()
        self._loop.run_forever()

    def submit(self, coro: Coroutine[Any, Any, Any]) -> Future:
        """Coroutine'i worker thread'e gönderir; sonucu Future olarak döner.

        Future.add_done_callback ile sonuca/hataya bakılmalı; callback
        worker thread'inde çalışır, Qt Signal.emit() burada çağrılırsa
        Qt otomatik queued connection ile ana thread'e taşır.
        """
        return asyncio.run_coroutine_threadsafe(coro, self._loop)

    def stop(self) -> None:
        self._loop.call_soon_threadsafe(self._loop.stop)
        self._thread.join(timeout=2)

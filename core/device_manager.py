"""QML'e cihaz listesini sunan model (plan madde 14: normalize LightDevice).

Optimistic UI (plan madde 16): kullanıcı bir eyleme tıklayınca UI hemen
güncellenir, istek arka planda gider; istek başarısız olursa önceki
duruma geri dönülür ve `errorOccurred` sinyali ile kullanıcıya haber
verilir.

Faz 6: isim/oda override'ları (config.json) ve odalar/sahneler; oda ve
"tümünü aç/kapat" komutları paralel çalışır (plan madde 17: asyncio.gather).
"""

from __future__ import annotations

import asyncio
import logging
import uuid
from concurrent.futures import Future
from dataclasses import dataclass, field
from typing import Any

from PySide6.QtCore import Property, QAbstractListModel, QModelIndex, QObject, Qt, Signal, Slot

from core import config_store
from core.async_worker import AsyncWorker
from core.credentials import (
    clear_tapo_credentials,
    get_stored_tapo_credentials,
    set_tapo_credentials,
)
from devices import tapo_adapter, wiz_adapter
from devices.base import LightController
from models.device_override import DeviceOverride
from models.light_device import LightDevice
from models.scene import Scene, SceneAction

logger = logging.getLogger(__name__)

_UNROOMED = "Diğer"

_ROLE_NAMES = (
    "name",
    "brand",
    # NOT: rol adı bilerek "model" değil "modelName" — QML delegate'lerinde
    # "model" zaten built-in context nesnesinin adı; bir rolü de "model"
    # olarak adlandırmak isim çakışmasına yol açıp TÜM model.xxx
    # erişimlerini (sadece bu rolü değil) bozuyordu (gerçek donanımda
    # görülen "isim/marka yazmıyor, state yanlış" bug'ının asıl sebebi buydu).
    "modelName",
    "ip",
    "online",
    "power",
    "brightness",
    "capBrightness",
    "capRgb",
    "capKelvin",
    "kelvin",
    "minKelvin",
    "maxKelvin",
    "room",
)
# Qt.UserRole + 1, +2, ... sırasıyla yukarıdaki isimlere karşılık gelir.
_ROLES = {Qt.UserRole + i: name for i, name in enumerate(_ROLE_NAMES, start=1)}


@dataclass
class _ScanOutcome:
    """_scan()'in tek bir dönüş değeri olarak taşıdığı sonuç (plan madde 9).

    Cihaz listesinin yanında WiZ/Tapo'ya özgü hata mesajlarını ve Tapo
    kimlik hatasının ayrı bir bayrağını (auth_failed) taşır ki
    `_apply_scan_result` her birini uygun şekilde (genel hata mesajı,
    kimlik bilgisi sıfırlama + yeniden isteme) ele alabilsin.
    """

    pairs: list[tuple[LightController | None, LightDevice]] = field(default_factory=list)
    wiz_error: str | None = None
    tapo_error: str | None = None
    tapo_auth_failed: bool = False


class DeviceListModel(QAbstractListModel):
    scanningChanged = Signal()
    errorOccurred = Signal(str)
    scenesChanged = Signal()
    roomStructureChanged = Signal()
    dataVersionChanged = Signal()
    # Tapo kimlik bilgisi eksik/hatalı olduğunda QML'in kimlik diyaloğunu
    # açması için (plan madde 9). Parametre kullanıcıya gösterilecek mesaj.
    tapoCredentialsNeeded = Signal(str)

    # İç sinyaller: worker thread'den GUI thread'ine güvenli geçiş için.
    # `Future.add_done_callback` worker thread'de çalışır; oradan doğrudan
    # beginResetModel()/dataChanged.emit() çağırmak Qt'nin thread affinity
    # kuralını çiğner (model mutasyonu GUI thread dışında olmamalı) ve bu,
    # yanlış power/brightness değerlerinin görünmesine yol açan asıl
    # sebepti. Signal.emit() farklı thread'den çağrılırsa Qt otomatik
    # olarak queued connection kullanıp teslimi GUI thread'e erteler; asıl
    # model mutasyonu bu sinyale bağlı slot içinde (GUI thread'de) yapılır.
    _scanFinished = Signal(object)
    _actionFinished = Signal(int, object, object)
    _groupFinished = Signal(object, object)

    def __init__(self, worker: AsyncWorker, parent: QObject | None = None) -> None:
        super().__init__(parent)
        self._worker = worker
        self._scanning = False
        self._refresh_pending = False
        self._credentials_changed_during_scan = False
        self._data_version = 0
        self._scanFinished.connect(self._apply_scan_result)
        self._actionFinished.connect(self._apply_action_result)
        self._groupFinished.connect(self._apply_group_result)

        self._overrides, self._scenes = config_store.load_config()

        # Açılışta discovery'yi beklemeden son bilinen cihazları anında göster
        # (plan madde 3). Bu kayıtların henüz canlı bir controller'ı yok;
        # ilk refresh() tamamlanana kadar aksiyonlar "çevrimdışı" sayılır.
        self._states = [self._apply_overrides(s) for s in config_store.load_cache()]
        self._controllers: list[LightController | None] = [None] * len(self._states)
        self._sort_states()

    # ---------------- QAbstractListModel ----------------

    def rowCount(self, parent: QModelIndex = QModelIndex()) -> int:  # noqa: N802 - Qt override
        return len(self._states)

    def roleNames(self) -> dict[int, bytes]:  # noqa: N802 - Qt override
        return {role: name.encode() for role, name in _ROLES.items()}

    def data(self, index: QModelIndex, role: int = Qt.DisplayRole) -> Any:  # noqa: N802
        if not index.isValid() or not (0 <= index.row() < len(self._states)):
            return None
        state = self._states[index.row()]
        role_name = _ROLES.get(role)
        if role_name == "name":
            return state.name
        if role_name == "brand":
            return state.brand
        if role_name == "modelName":
            return state.model
        if role_name == "ip":
            return state.ip
        if role_name == "online":
            return state.online
        if role_name == "power":
            return bool(state.power)
        if role_name == "brightness":
            return state.brightness or 0
        if role_name == "capBrightness":
            return state.capabilities.brightness
        if role_name == "capRgb":
            return state.capabilities.rgb
        if role_name == "capKelvin":
            return state.capabilities.color_temperature
        if role_name == "kelvin":
            return state.kelvin or 0
        if role_name == "minKelvin":
            return state.min_kelvin or 2200
        if role_name == "maxKelvin":
            return state.max_kelvin or 6500
        if role_name == "room":
            return state.room or _UNROOMED
        return None

    # ---------------- scanning property ----------------

    @Property(bool, notify=scanningChanged)
    def scanning(self) -> bool:  # noqa: N802 - Qt property
        return self._scanning

    def _set_scanning(self, value: bool) -> None:
        if self._scanning != value:
            self._scanning = value
            self.scanningChanged.emit()

    # ---------------- odaya göre gruplama (QML için) ----------------
    #
    # GridView'ın (ListView'ın aksine) native `section` desteği YOK — bu Qt
    # sürümünde QQuickGridView'da böyle bir property bulunmuyor (metadata'dan
    # doğrulandı). Bu yüzden gruplamayı iki AYRI, farklı sıklıkta değişen
    # parçaya bölüyoruz:
    #
    #   1) `roomStructure` — hangi oda, hangi satır index'lerini içeriyor.
    #      Sadece YAPISAL değişikliklerde (tarama, oda değiştiren rename)
    #      yeniden hesaplanıp sinyal gönderilir.
    #   2) `deviceAt(row)` + `dataVersion` — kartın GERÇEK verisi (power,
    #      brightness, kelvin...). `dataVersion` her optimistic güncellemede
    #      artar; QML tarafı bunu bir property binding içinde OKUYARAK
    #      (değerini kullanmadan) bağımlılık kurar, böylece sadece o kartın
    #      property'leri yeniden değerlendirilir — Repeater'ın modeli
    #      (roomStructure) DEĞİŞMEDİĞİ için hiçbir kart yok edilip yeniden
    #      oluşturulmaz.
    #
    # Önceki tasarımda TEK bir "gruplanmış liste" property'si her optimistic
    # güncellemede (örn. slider sürüklenirken ~60ms'de bir) yeniden
    # hesaplanıp QML'e YENİ bir liste olarak veriliyordu; QML'in Repeater'ı
    # düz bir liste yeniden atandığında TÜM delegate'leri yok edip yeniden
    # oluşturuyor — tam o an sürüklenen Slider da dahil. "Hareket edip sonra
    # takılma" bug'ının asıl sebebi buydu.

    def _sort_states(self) -> None:
        if not self._states:
            return
        combined = sorted(
            zip(self._states, self._controllers),
            key=lambda pair: (pair[0].room or _UNROOMED, pair[0].name),
        )
        self._states = [state for state, _ in combined]
        self._controllers = [controller for _, controller in combined]

    @Property("QVariant", notify=roomStructureChanged)
    def roomStructure(self) -> list[dict[str, Any]]:
        groups: dict[str, list[int]] = {}
        for row, state in enumerate(self._states):
            groups.setdefault(state.room or _UNROOMED, []).append(row)

        ordered_names = sorted(name for name in groups if name != _UNROOMED)
        if _UNROOMED in groups:
            ordered_names.append(_UNROOMED)

        return [{"room": name, "indices": groups[name]} for name in ordered_names]

    @Property(int, notify=dataVersionChanged)
    def dataVersion(self) -> int:
        return self._data_version

    def _bump_data_version(self) -> None:
        self._data_version += 1
        self.dataVersionChanged.emit()

    @Slot(int, result="QVariant")
    def deviceAt(self, row: int) -> dict[str, Any]:
        if not 0 <= row < len(self._states):
            return {}
        state = self._states[row]
        return {
            "index": row,
            "id": state.id,
            "name": state.name,
            "brand": state.brand,
            "modelName": state.model,
            "ip": state.ip,
            "online": state.online,
            "power": bool(state.power),
            "brightness": state.brightness or 0,
            "kelvin": state.kelvin or 0,
            "minKelvin": state.min_kelvin or 2200,
            "maxKelvin": state.max_kelvin or 6500,
            "capBrightness": state.capabilities.brightness,
            "capRgb": state.capabilities.rgb,
            "capKelvin": state.capabilities.color_temperature,
            "room": state.room or _UNROOMED,
        }

    @Slot(str, result="QVariant")
    def deviceById(self, device_id: str) -> dict[str, Any]:  # noqa: N802 - QML slot adı
        for row, state in enumerate(self._states):
            if state.id == device_id:
                return self.deviceAt(row)
        return {}

    # ---------------- Tapo kimlik bilgileri (plan madde 9) ----------------

    @Slot(result=bool)
    def hasTapoCredentials(self) -> bool:  # noqa: N802 - QML slot adı
        return get_stored_tapo_credentials() is not None

    @Slot(str, str)
    def setTapoCredentials(self, email: str, password: str) -> None:  # noqa: N802 - QML slot adı
        """QML kimlik diyaloğundan çağrılır — konsola hiç dokunmaz."""
        email = email.strip()
        if not email or not password:
            self.errorOccurred.emit("E-posta ve şifre boş olamaz")
            return
        set_tapo_credentials(email, password)
        if self._scanning:
            self._credentials_changed_during_scan = True
        self.refresh()

    # ---------------- override'lar (isim/oda) ----------------

    def _apply_overrides(self, state: LightDevice) -> LightDevice:
        override = self._overrides.get(state.id)
        if override is None:
            return state
        updates: dict[str, Any] = {}
        if override.name:
            updates["name"] = override.name
        if override.room:
            updates["room"] = override.room
        return state.model_copy(update=updates) if updates else state

    @Slot(int, str, str)
    def renameDevice(self, row: int, name: str, room: str) -> None:  # noqa: N802 - QML slot adı
        if not 0 <= row < len(self._states):
            return
        name = name.strip()
        room = room.strip()
        current = self._states[row]
        room_changed = (current.room or _UNROOMED) != (room or _UNROOMED)

        self._overrides[current.id] = DeviceOverride(name=name or None, room=room or None)
        config_store.save_config(self._overrides, self._scenes)

        updated = current.model_copy(update={"name": name or current.name, "room": room or None})

        if room_changed:
            # Oda değiştiyse satır sırası da değişir (aynı odadakiler ardışık
            # kalmalı, bkz. _sort_states) — bu nadir olduğu için tam reset ucuz.
            self.beginResetModel()
            self._states[row] = updated
            self._sort_states()
            self.endResetModel()
            self.roomStructureChanged.emit()
        else:
            self._states[row] = updated
            idx = self.index(row)
            self.dataChanged.emit(idx, idx, [])

        self._bump_data_version()
        config_store.save_cache(self._states)

    # ---------------- QML'den çağrılabilir eylemler ----------------

    @Slot()
    def refresh(self) -> None:
        if self._scanning:
            self._refresh_pending = True
            return
        self._set_scanning(True)
        future = self._worker.submit(self._scan())
        future.add_done_callback(self._on_scan_done)

    async def _scan(self) -> _ScanOutcome:
        # Bir sonraki turda "bulunamadı" olarak işaretleyebilmek için önceki
        # (cache veya bir önceki tarama) durumu MAC/device_id (stable_id) ile
        # sakla — IP değişse bile aynı cihaz olarak eşleşir (plan madde 2).
        previous_by_id = {state.id: state for state in self._states}

        for controller in self._controllers:
            if controller is None:
                continue
            try:
                await controller.close()
            except Exception:  # noqa: BLE001 - eski bağlantı kapatılırken hata önemsiz
                pass

        # Tapo bilgisi yoksa KONSOLA SORMUYORUZ (GUI'de input() kilitlenir) —
        # kullanıcı hiç Tapo cihazı kurmamış olabilir, bu durumda Tapo
        # taramasını sessizce atlıyoruz (plan madde 9: gereksiz hata nagg'i
        # olmasın). Bilgi varsa ama YANLIŞSA bu, tapo_result.auth_failed ile
        # ayrıca ele alınır.
        stored = get_stored_tapo_credentials()

        async def _tapo_task() -> tapo_adapter.TapoDiscoveryResult:
            if stored is None:
                return tapo_adapter.TapoDiscoveryResult()
            result = await tapo_adapter.discover(*stored)
            # UDP discovery paketleri zaman zaman kaybolabilir. Daha önce
            # bilinen Tapo cihazları bulunamadıysa kullanıcıyı yenilemeye
            # zorlamadan bir kez daha dene.
            if (not result.devices and not result.auth_failed and not result.error
                    and any(s.brand == "tapo" for s in previous_by_id.values())):
                result = await tapo_adapter.discover(*stored)
            return result

        (wiz_devices, wiz_error), tapo_result = await asyncio.gather(
            wiz_adapter.discover(), _tapo_task()
        )

        live_controllers: list[LightController] = [*wiz_devices, *tapo_result.devices]
        live_states = await asyncio.gather(*(c.get_state() for c in live_controllers))

        pairs: list[tuple[LightController | None, LightDevice]] = list(
            zip(live_controllers, live_states)
        )

        # Önceden bilinen ama bu turda bulunamayan cihazları listeden atmak
        # yerine "çevrimdışı" olarak koru (plan madde 5: offline detection).
        found_ids = {state.id for state in live_states}
        for stable_id, previous_state in previous_by_id.items():
            if stable_id not in found_ids:
                pairs.append((None, previous_state.model_copy(update={"online": False})))

        return _ScanOutcome(
            pairs=pairs,
            wiz_error=wiz_error,
            tapo_error=tapo_result.error,
            tapo_auth_failed=tapo_result.auth_failed,
        )

    def _on_scan_done(self, future: Future) -> None:
        # Worker thread'de çalışır: model'e dokunma, sadece sinyal gönder.
        try:
            outcome = future.result()
        except Exception as exc:  # noqa: BLE001 - tarama hatasında UI çökmesin
            self._scanFinished.emit(exc)
            return
        self._scanFinished.emit(outcome)

    @Slot(object)
    def _apply_scan_result(self, payload: Any) -> None:
        # GUI thread'de çalışır (queued connection): model mutasyonu burada güvenli.
        credentials_changed = self._credentials_changed_during_scan
        self._credentials_changed_during_scan = False
        if isinstance(payload, Exception):
            logger.warning("Tarama başarısız: %s", payload)
            self.errorOccurred.emit("Cihazlar taranamadı")
            self._set_scanning(False)
            self._run_pending_refresh()
            return

        outcome: _ScanOutcome = payload
        self.beginResetModel()
        self._controllers = [pair[0] for pair in outcome.pairs]
        self._states = [self._apply_overrides(pair[1]) for pair in outcome.pairs]
        self._sort_states()
        self.endResetModel()
        self._set_scanning(False)
        self.roomStructureChanged.emit()
        self._bump_data_version()
        config_store.save_cache(self._states)

        # Plan madde 9: WiZ/Tapo hatalarını ayrı ayrı, anlamlı şekilde bildir.
        if outcome.wiz_error:
            self.errorOccurred.emit(outcome.wiz_error)
        if outcome.tapo_auth_failed and not credentials_changed:
            # Yanlış/eskimiş bilgiyi temizle ki bir daha aynı hatayı vermesin;
            # QML kimlik diyaloğunu bu sinyalle otomatik açar.
            clear_tapo_credentials()
            self.tapoCredentialsNeeded.emit(
                "Tapo kimlik bilgileri hatalı. Lütfen tekrar girin."
            )
        elif outcome.tapo_error:
            if not credentials_changed:
                self.errorOccurred.emit(outcome.tapo_error)
        self._run_pending_refresh()

    def _run_pending_refresh(self) -> None:
        if self._refresh_pending:
            self._refresh_pending = False
            self.refresh()

    @Slot(int)
    def toggle(self, row: int) -> None:
        controller = self._require_controller(row)
        if controller is None:
            return
        previous = self._states[row]
        new_power = not previous.power

        self._apply_optimistic(row, previous.model_copy(update={"power": new_power}))
        coro = controller.turn_on() if new_power else controller.turn_off()
        self._run_action(row, previous, coro)

    @Slot(int, int)
    def setBrightness(self, row: int, percent: int) -> None:  # noqa: N802 - QML slot adı
        controller = self._require_controller(row)
        if controller is None:
            return
        previous = self._states[row]

        self._apply_optimistic(row, previous.model_copy(update={"brightness": percent}))
        self._run_action(row, previous, controller.set_brightness(percent))

    @Slot(int, int, int, int)
    def setRgb(self, row: int, r: int, g: int, b: int) -> None:  # noqa: N802 - QML slot adı
        controller = self._require_controller(row)
        if controller is None:
            return
        previous = self._states[row]

        self._apply_optimistic(row, previous.model_copy(update={"rgb": (r, g, b)}))
        self._run_action(row, previous, controller.set_rgb(r, g, b))

    @Slot(int, int)
    def setKelvin(self, row: int, kelvin: int) -> None:  # noqa: N802 - QML slot adı
        controller = self._require_controller(row)
        if controller is None:
            return
        previous = self._states[row]

        self._apply_optimistic(row, previous.model_copy(update={"kelvin": kelvin}))
        self._run_action(row, previous, controller.set_kelvin(kelvin))

    # ---------------- oda / global toplu aç-kapat (plan madde 17) ----------------

    @Slot(str)
    def turnOnRoom(self, room: str) -> None:  # noqa: N802 - QML slot adı
        self._set_group_power(self._rows_in_room(room), True)

    @Slot(str)
    def turnOffRoom(self, room: str) -> None:  # noqa: N802 - QML slot adı
        self._set_group_power(self._rows_in_room(room), False)

    @Slot()
    def turnOnAll(self) -> None:  # noqa: N802 - QML slot adı
        self._set_group_power(list(range(len(self._states))), True)

    @Slot()
    def turnOffAll(self) -> None:  # noqa: N802 - QML slot adı
        self._set_group_power(list(range(len(self._states))), False)

    def _rows_in_room(self, room: str) -> list[int]:
        return [i for i, s in enumerate(self._states) if (s.room or _UNROOMED) == room]

    def _set_group_power(self, rows: list[int], power: bool) -> None:
        previous_by_row: dict[int, LightDevice] = {}
        coros = []
        for row in rows:
            if not 0 <= row < len(self._states):
                continue
            controller = self._controllers[row]
            if controller is None:
                continue
            previous = self._states[row]
            previous_by_row[row] = previous
            self._apply_optimistic(row, previous.model_copy(update={"power": power}))
            coros.append(controller.turn_on() if power else controller.turn_off())

        if not coros:
            return

        future = self._worker.submit(self._run_group(coros))
        future.add_done_callback(lambda f: self._on_group_done(f, previous_by_row))

    async def _run_group(self, coros: list[Any]) -> list[Any]:
        # Paralel çalıştır (plan madde 17): toplam süre en yavaş cihaz kadar
        # olur, tek bir cihazın hatası diğerlerini etkilemez.
        return await asyncio.gather(*coros, return_exceptions=True)

    def _on_group_done(self, future: Future, previous_by_row: dict[int, LightDevice]) -> None:
        # Worker thread'de çalışır: model'e dokunma, sadece sinyal gönder.
        try:
            results = future.result()
        except Exception as exc:  # noqa: BLE001
            results = [exc] * len(previous_by_row)
        self._groupFinished.emit(previous_by_row, results)

    @Slot(object, object)
    def _apply_group_result(
        self, previous_by_row: dict[int, LightDevice], results: list[Any]
    ) -> None:
        # GUI thread'de çalışır (queued connection): model mutasyonu burada güvenli.
        failed = 0
        for row, result in zip(previous_by_row.keys(), results):
            if isinstance(result, Exception):
                failed += 1
                if 0 <= row < len(self._states):
                    self._states[row] = previous_by_row[row]
                    idx = self.index(row)
                    self.dataChanged.emit(idx, idx, [])
        if failed:
            self._bump_data_version()
            self.errorOccurred.emit(f"{failed} cihaz yanıt vermedi")

    # ---------------- sahneler (plan madde 18) ----------------

    @Property("QVariant", notify=scenesChanged)
    def scenes(self) -> list[dict[str, Any]]:
        return [{"id": s.id, "name": s.name, "icon": s.icon} for s in self._scenes]

    @Slot(str)
    def createSceneFromCurrent(self, name: str) -> None:  # noqa: N802 - QML slot adı
        name = name.strip()
        if not name:
            return
        actions = [
            SceneAction(
                device_id=s.id, power=s.power, brightness=s.brightness, rgb=s.rgb, kelvin=s.kelvin
            )
            for s in self._states
            if s.online
        ]
        if not actions:
            self.errorOccurred.emit("Sahne oluşturmak için çevrimiçi cihaz yok")
            return
        scene = Scene(id=f"scene_{uuid.uuid4().hex[:8]}", name=name, actions=actions)
        self._scenes.append(scene)
        config_store.save_config(self._overrides, self._scenes)
        self.scenesChanged.emit()

    @Slot(str)
    def deleteScene(self, scene_id: str) -> None:  # noqa: N802 - QML slot adı
        self._scenes = [s for s in self._scenes if s.id != scene_id]
        config_store.save_config(self._overrides, self._scenes)
        self.scenesChanged.emit()

    @Slot(str)
    def applyScene(self, scene_id: str) -> None:  # noqa: N802 - QML slot adı
        scene = next((s for s in self._scenes if s.id == scene_id), None)
        if scene is None:
            return

        previous_by_row: dict[int, LightDevice] = {}
        coros = []
        for action in scene.actions:
            for row, state in enumerate(self._states):
                if state.id != action.device_id:
                    continue
                controller = self._controllers[row]
                if controller is None:
                    break
                previous_by_row[row] = state
                updates: dict[str, Any] = {}
                if action.power is not None:
                    updates["power"] = action.power
                if action.brightness is not None:
                    updates["brightness"] = action.brightness
                if action.rgb is not None:
                    updates["rgb"] = action.rgb
                if action.kelvin is not None:
                    updates["kelvin"] = action.kelvin
                self._apply_optimistic(row, state.model_copy(update=updates))
                coros.append(self._apply_scene_action(controller, action))
                break

        if not coros:
            self.errorOccurred.emit("Sahnedeki cihazlar bulunamadı")
            return

        future = self._worker.submit(self._run_group(coros))
        future.add_done_callback(lambda f: self._on_group_done(f, previous_by_row))

    async def _apply_scene_action(self, controller: LightController, action: SceneAction) -> None:
        if action.power is False:
            await controller.turn_off()
            return
        # set_rgb/set_kelvin/set_brightness cihazı zaten açık konuma getirir
        # (adapter'lar bunu garanti ediyor), ayrıca turn_on() gerekmez.
        if action.rgb is not None:
            await controller.set_rgb(*action.rgb)
        if action.kelvin is not None:
            await controller.set_kelvin(action.kelvin)
        if action.brightness is not None:
            await controller.set_brightness(action.brightness)
        elif action.power is True and action.rgb is None and action.kelvin is None:
            await controller.turn_on()

    # ---------------- yardımcılar ----------------

    def _require_controller(self, row: int) -> LightController | None:
        """Satır geçerli ve canlı bir controller'a sahipse onu döner.

        Cache'ten gelen veya bu turda bulunamayan (çevrimdışı) cihazların
        controller'ı yoktur (None) — bu durumda komut göndermek yerine
        kullanıcıya haber verilir.
        """
        if not 0 <= row < len(self._states):
            return None
        controller = self._controllers[row]
        if controller is None:
            self.errorOccurred.emit(f"{self._states[row].name} çevrimdışı")
            return None
        return controller

    def _apply_optimistic(self, row: int, new_state: LightDevice) -> None:
        self._states[row] = new_state
        idx = self.index(row)
        self.dataChanged.emit(idx, idx, [])
        self._bump_data_version()

    def _run_action(self, row: int, previous: LightDevice, coro: Any) -> None:
        future = self._worker.submit(coro)
        future.add_done_callback(lambda f: self._on_action_done(f, row, previous))

    def _on_action_done(self, future: Future, row: int, previous: LightDevice) -> None:
        # Worker thread'de çalışır: model'e dokunma, sadece sinyal gönder.
        try:
            future.result()
        except Exception as exc:  # noqa: BLE001 - cihaz yanıt vermese bile UI çökmesin
            self._actionFinished.emit(row, previous, exc)
            return
        self._actionFinished.emit(row, previous, None)

    @Slot(int, object, object)
    def _apply_action_result(self, row: int, previous: LightDevice, exc: Exception | None) -> None:
        # GUI thread'de çalışır (queued connection): model mutasyonu burada güvenli.
        if exc is None:
            return  # optimistic güncelleme zaten uygulanmıştı, yapılacak bir şey yok.
        logger.warning("Komut başarısız: %s", exc)
        if 0 <= row < len(self._states):
            self._states[row] = previous
            idx = self.index(row)
            self.dataChanged.emit(idx, idx, [])
            self._bump_data_version()
        self.errorOccurred.emit(f"{previous.name} yanıt vermedi")

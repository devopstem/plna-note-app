# Модель данных (Firebase RTDB → C++ сцена)

Источник правды для чертежа магазина — Firebase Realtime Database.
C++ **не ходит в Firebase сам**: платформа (Kotlin / Swift / Win) скачивает JSON и один раз заливает в ядро через FFI. Так батарея не жжётся на парсинге каждый кадр — в кадре только `buildDrawList()` + отрисовка буфера.

## Дерево RTDB (упрощённо)

```
catalog/tenants/{tenantId}/
  metadata, products/{id} → barcode, name, category, …

stores/{storeKey}/
  metadata.updatedAt
  stores_index/{storeKey} → name, address, city, lat/lng, radiusMeters
  store_maps/{mapId}/
    layout
    walls[], wallPoints[]
    racks[], coolers[], stands[]
    markers[]
    zones[], zoneDictionary, zoneColors

users/{uid}/ → name, role, store_keys[]
```

Рабочий полный чертёж в экспорте: `stores/gippo-momo-01a05683/store_maps/...`
(другие ключи могут быть «заготовками» только с zones/layout).

---

## `layout` → пол сцены

| Firebase | C++ |
|----------|-----|
| `widthMeters` | `MapScene::setFloor(w, h)` → `floorW_` |
| `heightMeters` | `floorH_` |
| `gridSizeCm` | шаг сетки в draw list (сейчас захардкожен 5 м — нужно брать из layout) |
| `origin` (`sw`) | соглашение координат (юго-запад = 0,0) |
| `entranceSide`, `entranceOffsetM`, `cashSide`, … | пока маркеры / будущий nav-graph |

Единицы везде **метры**, плоскость пола **X/Y**.

---

## `walls[]` → `Wall`

```json
{ "startX", "startY", "endX", "endY", "wallType": "wall"|"door" }
```

| Firebase | C++ `Wall` |
|----------|------------|
| start/end | `x1,y1,x2,y2` |
| `wallType == "door"` | `isDoor = true` |

`wallPoints[]` — дублирующий контур полилинии; для ядра достаточно `walls[]`.

---

## `racks[]` / `coolers[]` / `stands[]` → `Equipment`

Общие поля:

| Firebase | C++ |
|----------|-----|
| `id` | `id` |
| `equipmentType` | `EquipType` (`rack`/`cooler`/`stand`) |
| `posX`, `posY` (и дубли `rx`,`ry`) | `x`, `y` |
| `widthMeters` | `w` |
| `lengthMeters` | `d` (глубина на плане) |
| `heightMeters` | `h` |
| `rotation` | `rotationDeg` (**пока не рисуется** — см. ISSUES) |
| `shelfCount` / `levels[]` | `shelves` + будущий детальный shelf-graph |
| `zoneColor` | `r,g,b,a` (парсить `#RRGGBB` на хосте или в loader) |
| `zoneCode`, `segments[]`, `direction` | зональные грани / навигация (ещё не в сцене) |

`levels[]`: `{ levelNumber, heightFromFloorMeters, capacityMeters }` — для 3D полок и `findProduct`, не для контура на плане.

---

## `markers[]` → `Marker`

| Firebase `type` | C++ `MarkerType` |
|-----------------|------------------|
| `cash_register` | `Cash` |
| `entrance` | `Entrance` |
| `exit` (если появится) | `Exit` |
| beacon / other | `Beacon` / `Other` |

Поля: `posX/Y` → `x/y`, `widthMeters`/`depthMeters` → `w`/`d`, `color`, `rotation`.

---

## `zones` / каталог / пользователи

| Данные | Где живут |
|--------|-----------|
| `zones`, dictionary, colors | Справочник UI + подписи; в draw list можно слой зон позже |
| `catalog/.../products` | Кэш штрихкодов → `putProduct` / задача мерча |
| `users` | Только app-слой (роль, список магазинов) |
| Геофенс `stores_index` | App (GPS), не map-core |

---

## Кто что делает (батарея / CPU)

```
Firebase JSON  ──раз──►  plna_map_load_json / nativeLoadStoreMapJson
                              C++ MapScene
                                    │  revision++ только при изменении
                                    ▼
                         buildDrawList()  (кэш, пока revision тот же)
                                    ▼
                         float[]  ──paint──►  GPU/Canvas
```

Ещё комфортнее: на хосте хранить `lastRevision` и **не** трогать Canvas-буфер, пока `plna_map_revision()` не вырос.

Сетка: в JSON `gridSizeCm` (snap 50 см), на экране рисуется major-grid ~5 м — меньше линий, меньше батареи.

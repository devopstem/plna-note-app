# Архитектура PLNA Native Core

## Зачем C++ в корне

Один алгоритм чертежа/карты для **Android · iOS · Windows** (и дальше desktop/CI):

- Kotlin/Swift/C# **не считают** геометрию зала каждый кадр → меньше CPU и расход батареи.
- Маршрут, draw list, кэш товара, (опционально) TCP-framing — **одна** реализация.
- UI-слои тонкие: скачали Firebase → залили в ядро → рисуют `float[]`.

Развёрнуто для продукта/демо: **[docs/WHY_CPP.md](../../../../docs/WHY_CPP.md)**.

```
        ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
        │ Android      │  │ iOS          │  │ Windows      │
        │ JNI / Kotlin │  │ cinterop/    │  │ C API / P/Invoke│
        │              │  │ Swift        │  │              │
        └──────┬───────┘  └──────┬───────┘  └──────┬───────┘
               │                 │                 │
               ▼                 ▼                 ▼
        ┌─────────────────────────────────────────────────┐
        │              plna_core (C++23)                  │
        │  MapScene · math · (SocketClient)               │
        │  единый чертёж в метрах + buildDrawList         │
        └─────────────────────────────────────────────────┘
               ▲
               │ JSON store_maps (один раз при sync)
        Firebase RTDB  (см. DATA_MODEL.md)
```

Данные чертежа — из RTDB `store_maps` (racks/coolers/stands/walls/markers/layout). Подробный маппинг полей: **[DATA_MODEL.md](DATA_MODEL.md)**.

---

## Структура репозитория

| Путь | Назначение |
|------|------------|
| `core/` | Портативное ядро (без JNI) — собирается везде |
| `platform/android/` | Тонкий JNI к `NativeMapEngine` / `NativeNetwork` |
| `platform/ios/` | *(план)* C-API / Objective-C++ wrapper |
| `platform/windows/` | *(план)* C-API / DLL exports |
| `CMakeLists.txt` | `plna_core` всегда; JNI только при `ANDROID` |

---

## 1. Карта зала — `plna::map::MapScene`

Синглтон сцены в метрах (плоскость X/Y, origin как в `layout.origin`, обычно SW).

Хранит: пол, оборудование, стены/двери, маркеры, маршрут, флаги слоёв и 2D/3D.

### Draw list (контракт с любым рендерером)

`buildDrawList()` → `vector<float>`: поток opcode + аргументы. Host **только интерпретирует**, не считает метры заново.

| `DrawOp` | Код | Формат после opcode |
|----------|-----|---------------------|
| `Floor` | 1 | `x, y, w, h, r, g, b, a` |
| `Box2D` | 2 | `x, y, w, h, r, g, b, a` |
| `Line` | 3 | `x1, y1, x2, y2, r, g, b, a, stroke` |
| `Circle` | 4 | `x, y, radius, r, g, b, a` |
| `Box3D` | 5 | `x, y, w, d, h, r, g, b, a` |
| `Path` | 6 | `count, x0,y0,…, r, g, b, a, stroke` |

Порядок: пол → сетка → стены → оборудование → маркеры → маршрут.

### Маршрут

`routeAround` — упрощённый L-path с объездом AABB стоек. Стены как препятствия и `rotation` оборудования — ещё не полноценный pathfinding (см. ISSUES).

---

## 2. Геометрия и товары — `plna::math`

| Функция | Роль |
|---------|------|
| `calculateRoute` | `routeAround` + запись пути в сцену |
| `drawRack` | вершины полок (ось Y = высота — отдельно от плана X/Y) |
| `putProduct` / `findProduct` | in-memory штрихкод → позиция |
| `calculateBatchPositions` | legacy batch по полкам |

Каталог из Firebase (`catalog/tenants/.../products`) хост кладёт в `putProduct` при необходимости навигации к товару.

---

## 3. Сеть — `plna::net::SocketClient`

Опциональный бинарный TCP (пакеты AUTH / SYNC_PLANOGRAM / задачи / HEARTBEAT). Сейчас POSIX (`socket`/`poll`) — Android/Linux; для Windows нужен Winsock-адаптер.

Синк карты из Firebase чаще идёт через SDK хоста; этот сокет — для live-задач мерча, не замена RTDB.

Заголовок: `magic 0x5450 | type BE | length BE | body`.

---

## 4. Платформенные мосты (тонкие)

### Android (есть) — JNI

Классы приложения:

- `by.smartplanogram.plnanote.nativebridge.NativeMapEngine`
- `by.smartplanogram.plnanote.data.remote.NativeNetwork`

Host обязан: `nativeInit`, колбэки `onDataReceived` / `onConnectionStatusChanged`.

### iOS / Windows (цель)

Тот же `plna_core`, другой glue:

- стабильный **C API** (`plna_map_clear`, `plna_map_add_equipment`, `plna_map_build_draw_list`, …) поверх C++;
- Swift / C# только вызывают C API и рисуют буфер.

JNI-имена Android **не** должны протекать в core.

---

## 5. Типичный поток (все платформы)

```
1. Host: auth + download store_maps/{id} из Firebase
2. plna: clear → setFloor(layout) → addWall* → addEquipment* → addMarker*
3. (опц.) putProduct из каталога / планограммы
4. Каждый кадр / по dirty-flag: buildDrawList() → Canvas/Metal/DirectX/Skia
5. По жесту «маршрут к товару»: calculateRoute / findProduct
6. Live-задачи: SocketClient или свой канал — без пересчёта геометрии на UI
```

---

## 6. Сборка

```bash
# Desktop / CI / Windows (MinGW/MSVC) — только ядро
cmake -B build -DCMAKE_BUILD_TYPE=Release
cmake --build build
# → libplna_core.a (и/или libplna без JNI)

# Android NDK — ядро + JNI → libplna.so
cmake -B build \
  -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-24
cmake --build build
```

См. также [ISSUES.md](ISSUES.md) и [DATA_MODEL.md](DATA_MODEL.md).

# Что не так в текущем коде

Проверено по исходникам и попытке desktop-сборки (`cmake` → `log-lib NOTFOUND`).

## Критично (ломает CI / прод)

### 1. CMake / портативность — частично закрыто

`plna_core` собирается без JNI/`liblog` на desktop. JNI включается только при `ANDROID` / `PLNA_BUILD_ANDROID_JNI`.

Осталось:

- `ctest` пустой — тестов нет
- `SocketClient` = POSIX only → Windows (Winsock) ещё нет
- **C API есть** (`plna/c_api.h`) — осталось тонкие Swift/C# wrappers в `platform/ios|windows`
- цель «один алгоритм везде» по карте: `plna_map_load_json` + draw list + revision

### 2. README / бейджи указывают не туда

- CI badge: `devopstem-debug/plna-engine`
- Ссылка на экосистему: `github.com/devopstem-plna-core-cpp` (невалидный URL)
- Репозиторий по факту: `plna-core-cpp`

### 3. Гонки в `SocketClient`

- `disconnect()` закрывает `sockfd_`, пока `networkLoop` может быть в `poll`/`recv`/`send` — data race на дескрипторе.
- Колбэки `onPacketReceived_` / `onStatusChanged_` пишутся с UI/JNI-потока без мьютекса, читаются с worker.
- `sendPacket` кладёт в очередь даже если клиент не запущен — очередь растёт без bound.
- При неудачном `connect` (таймаут `EINPROGRESS`) fd часто остаётся открытым до следующего `handleReconnect`.

### 4. Нет лимита размера пакета

`length` из сети принимается как есть. Вредоносный/битый заголовок → огромный `vector` или бесконечное ожидание тела. Нужен `MAX_PACKET_SIZE` и сброс при превышении.

### 5. Восстановление после desync magic — баг

При `magic != 0x5450` ищется один байт `0x54`. На little-endian wire-образ `0x5450` — это байты `50 54`, поиск `0x54` цепляет **второй** байт и сдвигает поток мимо настоящего заголовка.

Дополнительно: `type`/`length` идут через `htons`/`htonl`, а `magic` — нет. Работает только пока клиент и сервер одной endianness (типично LE).

---

## Средний приоритет (логика / контракт)

### 6. Побочный эффект `calculateRoute`

Функция маршрута ещё и вызывает `setRoute` — меняет сцену. Неочевидно для вызывающего; повторный `buildDrawList` внезапно рисует путь.

### 7. Поля модели — частично закрыто

`visible` и `rotationDeg` учитываются (поворот → `DrawOp::Quad`).  
`shelves` / `levels[]` пока не рисуют отдельные полки на плане (только счётчик для math/drawRack).

### 8. Две системы координат

| API | Пол | Высота |
|-----|-----|--------|
| `MapScene` | X/Y | `h` / `Box3D` |
| `drawRack` | X/Z в углах | Y = номер полки × `shelfHeight` |

Легко перепутать при стыковке с Kotlin-визуализатором.

### 9. Маршрут слишком наивный

- Стены/двери не препятствия.
- Объезд только «горизонтальная нога vs AABB стойки», вертикальная нога не проверяется.
- `rotationDeg` не влияет на AABB.

### 10. JNI `DetachCurrentThread` всегда

В `dispatchToKotlin` / `dispatchStatusToKotlin` после работы всегда `Detach`. Для выделенного network-потока ок; если колбэк когда-либо вызовут с уже attached-потока — сломают JNIEnv вызывающего.

### 11. `nativeDisconnect` сбрасывает GlobalRef класса

После disconnect колбэки некуда слать, пока снова не вызовут `nativeInit`. Порядок вызовов на Kotlin критичен.

### 12. `connect()` возвращает `true` «поток стартовал»

Не означает установленное TCP-соединение. Реальный статус только через `onConnectionStatusChanged` / `isConnected()`.

---

## Мелочи / долг

| Проблема | Где |
|----------|-----|
| Пустые `SECURITY.md`, `pull_request_template.md` | корень |
| В CONTRIBUTING обещан `.clang-format` — файла нет | — |
| README обещает iOS / WASM — кода нет | README |
| `MapScene` синглтон без синхронизации | многопоточный вызов с UI + hypothetical worker = UB |
| Нет unit-тестов на draw list / protocol framing / route | — |
| `#include <span>` в `map_geometry.cpp` — ок для C++23, но CI не доходит до компиляции | — |

---

## Рекомендуемый порядок фиксов

1. Разделить CMake: `plna_core` (без JNI/log) + опциональный `plna_jni` для Android; desktop CI собирает только core + тесты.
2. Добавить framing-тесты (magic, partial read, max length, heartbeat).
3. Починить endianness magic + resync (искать 2 байта `50 54` / использовать `ntohs` единообразно).
4. Сериализовать доступ к `sockfd_` и колбэкам; закрывать fd только из network-потока или через `shutdown` + join без гонки.
5. Учитывать `visible` / `rotationDeg` в draw list либо убрать мёртвые поля из API.

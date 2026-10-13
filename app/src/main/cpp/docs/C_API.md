# C API — один вход для Android / iOS / Windows

Заголовок: `core/include/plna/c_api.h`  
Линковать: `plna_core` (static) или Android `libplna.so` (JNI + core).

## Самый комфортный путь (карта из Firebase)

```c
char err[256];
if (!plna_map_load_json(store_maps_json, err, sizeof err)) {
    // log err
}
uint64_t rev = plna_map_revision();

// каждый кадр / после жеста:
if (rev != lastRev) {
    size_t n = 0;
    const float* dl = plna_map_build_draw_list(&n);
    // upload to GPU / Canvas
    lastRev = rev;
}
```

Kotlin (после добавления native-метода):

```kotlin
NativeMapEngine.nativeLoadStoreMapJson(jsonString)
val rev = NativeMapEngine.nativeRevision()
val draw = NativeMapEngine.nativeBuildDrawList() // FloatArray
```

Не парси геометрию в Compose каждый кадр — только рисуй буфер, пока `revision` не вырос.

## DrawOp (хост-рендерер)

| Code | Имя | Payload |
|------|-----|---------|
| 1 | Floor | x y w h rgba |
| 2 | Box2D | x y w h rgba |
| 3 | Line | x1 y1 x2 y2 rgba stroke |
| 4 | Circle | x y r rgba |
| 5 | Box3D | x y w d h rgba |
| 6 | Path | count x0 y0 … rgba stroke |
| 7 | **Quad** | x0 y0 … x3 y3 rgba (поворот) |

## CLI

```bash
cmake -B build && cmake --build build
./build/plna_map_dump fixtures/store_map_sample.json --route 1 1 28 20
```

## Продукты / маршрут

```c
plna_product_put("4810…", x, y, z, shelf);
float xyzw[4];
if (plna_product_find("4810…", xyzw)) { /* navigate */ }

float path[64];
size_t n = plna_map_route(fx, fy, tx, ty, path, 64, /*apply*/ 1);
```

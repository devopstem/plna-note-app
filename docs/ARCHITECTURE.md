# Архитектура приложения (Android)

Package / applicationId: `by.smartplanogram.plnanote`  
JNI map bridge: `by.smartplanogram.plnanote.nativebridge`

## Слои

```
┌─────────────────────────────────────────────┐
│ UI (Fragments / Activities, ViewBinding)    │
│ Home · Lists · ListDetail · Map · Profile   │
└──────────────────┬──────────────────────────┘
                   │
┌──────────────────▼──────────────────────────┐
│ Kotlin data                                 │
│ UserSettings (SharedPreferences)            │
│ ShoppingListRepository (JSON в prefs)       │
│ OfflineRepository → Room SQLite             │
│ RtdbSyncEngine (assets → Room → Firebase)   │
└──────────────────┬──────────────────────────┘
                   │ JNI
┌──────────────────▼──────────────────────────┐
│ plna_core (C++23)                           │
│ MapScene · route · buildDrawList            │
│ см. app/src/main/cpp/docs/                  │
└─────────────────────────────────────────────┘
```

## Данные: что откуда

Из Firebase RTDB / `assets/rtdb_export.json` берём **только нужное для навигации**:

| Поле | Назначение |
|------|------------|
| `name` | Название товара |
| `rackId` | Стеллаж / зона |
| `shelf` | Полка |
| `x`, `y` | Координаты на плане (метры) |
| `store_maps` JSON | Чертёж зала для C++ (кэш в Room) |

UI **не** ходит в Firebase напрямую при каждом поиске. Источник правды на телефоне — **Room** (`plna_offline.db`).

### Порядок sync

1. Прочитать `assets/rtdb_export.json` → записать магазины, товары, карту в Room (всегда работает офлайн).
2. Попытаться обновить дерево из Firebase RTDB (если проект/правила доступны).
3. Поиск товаров: `LIKE` по Room для выбранного `storeId`.

## Настройки пользователя

`UserSettings` (SharedPreferences) — синхронно, без Room:

- тема: dark / light / system → `AppCompatDelegate`
- язык: ru / en / be → `AppCompatDelegate.setApplicationLocales`
- вход: login id, флаг logged_in

Выбор магазина и кэш каталога — в Room (`AppPrefsEntity.selectedStoreId`).

## Карта

1. `OfflineRepository.mapJsonForSelectedStore()` или fallback `store_map_sample.json`.
2. `NativeMapEngine.nativeLoadStoreMapJson(json)`.
3. При маршруте: `calculateRoute(fromX, fromY, item.x, item.y)`.
4. `nativeBuildDrawList()` → `StoreMapView` рисует opcodes (Floor, Box2D, Line, …).

Подробнее про opcodes и C API: [cpp/docs/ARCHITECTURE.md](../app/src/main/cpp/docs/ARCHITECTURE.md).

## Модули Kotlin (кратко)

| Пакет / класс | Роль |
|---------------|------|
| `PlnaApp` | Тема + язык при старте, фоновый `ensureSynced()` |
| `OfflineRepository` | Room API для UI |
| `RtdbSyncEngine` | Парсинг RTDB/assets → entities |
| `ShoppingListRepository` | Локальные заметки пользователя |
| `QuantityBottomSheet` | Выбор веса/объёма при добавлении товара |
| `StorePicker` | Ручной выбор магазина |

# PLNA Заметки (Smart Map)

Список покупок и маршрут по залу магазина. Часть экосистемы **PLNA** (общий C++ map-engine + общая модель данных).

**© 2026 Smart Map from PLNA**

![Logo](branding/plna-smart-map-logo.png)

## Зачем

Покупатель собирает заметку с товарами (вес/объём), в магазине открывает карту и идёт по маршруту к стеллажам. Каталог и план зала кэшируются в Room — после sync работает офлайн.

Подробнее: [docs/PRODUCT.md](docs/PRODUCT.md)

## Стек

| Слой | Технология |
|------|------------|
| UI | Android, Kotlin, ViewBinding, Material |
| Офлайн БД | Room (SQLite) |
| Sync | Firebase RTDB + `assets/rtdb_export.json` |
| Карта | C++23 `plna_core` через JNI |
| Настройки | SharedPreferences (`UserSettings`) |

Архитектура: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) · ядро: [app/src/main/cpp/docs/](app/src/main/cpp/docs/)

## Быстрый старт

1. Откройте проект в Android Studio.
2. Положите `app/google-services.json` (см. example-файл).
3. `./gradlew :app:assembleDebug`
4. Установите `app/build/outputs/apk/debug/app-debug.apk`

Полная инструкция: [docs/BUILD.md](docs/BUILD.md)

## Структура

```
app/                 Android-приложение + NDK
app/src/main/cpp/    plna_core (C++23) + JNI
branding/            SVG/PNG логотип Smart Map
docs/                Продукт, архитектура, сборка, GitHub-чеклист
```

## Статус

Прототип / внутренний MVP. Package: `by.smartplanogram.plnanote`.  
Публикация — см. [docs/GITHUB.md](docs/GITHUB.md) (рекомендуется private, пока не убраны секреты).

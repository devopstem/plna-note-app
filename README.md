# PLNA Заметки (Smart Map)

**Версия: 0.2.0** · © 2026 Smart Map from PLNA  
Package: `by.smartplanogram.plnanote`

Список покупок и маршрут по залу магазина. Часть экосистемы **PLNA**: UI на Kotlin, чертёж и маршрут — в C++23 `plna_core`.

![Logo](branding/plna-smart-map-logo.png)

## Что умеет 0.2

| Возможность | Как работает |
|-------------|--------------|
| Главная | Hero, магазин, быстрые плитки, сводка |
| Заметки | Списки, количество, поиск по каталогу магазина |
| План зала офлайн | Room + JSON плана; интернет не обязателен |
| Маршрут | От якоря **Вход / Кассы** (или от точки «я») к товару |
| «Иду» (PDR) | Шаги телефона + компас → приблизительная позиция |
| Стеллаж | Подсветка + «лицо» полки |
| Мои покупки | Локальная аналитика на устройстве |
| Языки | ru / en / be |
| Демо-доступ | Локальный профиль по телефону (без облака) |

## Стек

| Слой | Технология |
|------|------------|
| UI | Android, Kotlin, ViewBinding, Material 3 |
| Офлайн | Room (SQLite) |
| Sync | Firebase RTDB + `assets/rtdb_export.json` |
| Карта | C++23 `plna_core` (JNI) |
| Позиция «я» | Kotlin PDR (датчики) |
| Настройки | SharedPreferences |

## Документация

| Документ | О чём |
|----------|--------|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Чертёж системы, потоки данных |
| [docs/WHY_CPP.md](docs/WHY_CPP.md) | Почему карта на C++, а не только Kotlin |
| [app/src/main/cpp/docs/](app/src/main/cpp/docs/) | Native core, C API, модель плана |
| [docs/PRODUCT.md](docs/PRODUCT.md) | Продуктовый взгляд |
| [docs/BUILD.md](docs/BUILD.md) | Сборка |
| [docs/CHANGELOG.md](docs/CHANGELOG.md) | История версий |
| [docs/GITHUB.md](docs/GITHUB.md) | Публикация репозитория |

## Быстрый старт

1. Откройте проект в Android Studio.
2. Положите `app/google-services.json` (см. `app/google-services.json.example`).
3. `./gradlew :app:assembleDebug` (для показа лучше `assembleRelease`).
4. APK: `app/build/outputs/apk/...`

Подробнее: [docs/BUILD.md](docs/BUILD.md)

## Структура

```
app/                 Android + NDK
app/src/main/cpp/    plna_core (C++23) + JNI
branding/            логотип Smart Map
docs/                продукт, архитектура, сборка, changelog
```

## Версии

| Версия | Статус |
|--------|--------|
| **0.2.0** | текущая: Home, PDR, Insights, прод-hardening |
| 0.1.x | якорь, стеллаж, компас, офлайн-каталог |
| 1.0 | публичный релиз (облачный аккаунт, стабильные сети) |

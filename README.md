# PLNA Заметки (Smart Map)

**Версия: MVP 0.1** (`0.1.0-mvp`) · © 2026 Smart Map from PLNA

Список покупок и маршрут по залу. Часть экосистемы **PLNA** (C++ map-engine + общая модель магазина).

![Logo](branding/plna-smart-map-logo.png)

## Что умеет MVP 0.1

| Возможность | Как работает |
|-------------|--------------|
| Заметки с товарами | Списки, количество, «на карте» |
| План зала офлайн | Room + JSON плана; интернет для карты не обязателен |
| Маршрут | От якоря **Вход / Кассы** к ближайшему некупленному товару |
| Стеллаж | Подсветка цели + bottom sheet «лицо» полки |
| Ориентация | С/В/Ю/З на плане + мини-компас |
| Языки | ru / en / be |
| Вход | Телефон или код (без пароля) |

**Не умеет (и не обещаем в 0.1):** живая точка «я» по залу (GPS внутри здания бесполезен; шаги/маяки — следующие уровни). Подробности: [docs/CHANGELOG.md](docs/CHANGELOG.md).

## Стек

| Слой | Технология |
|------|------------|
| UI | Android, Kotlin, ViewBinding, Material |
| Офлайн | Room (SQLite) |
| Sync | Firebase RTDB + `assets/rtdb_export.json` |
| Карта | C++23 `plna_core` (JNI) |
| Настройки | SharedPreferences |

Архитектура: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) · продукт: [docs/PRODUCT.md](docs/PRODUCT.md) · ядро: [app/src/main/cpp/docs/](app/src/main/cpp/docs/)

## Быстрый старт

1. Откройте проект в Android Studio.
2. Положите `app/google-services.json` (см. example-файл).
3. `./gradlew :app:assembleDebug`
4. Установите `app/build/outputs/apk/debug/app-debug.apk`

Полная инструкция: [docs/BUILD.md](docs/BUILD.md)

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
| **0.1.0-mvp** | текущий MVP: карта, якорь, стеллаж, компас |
| 0.2+ | PDR / уточнение «я» (план) |
| 1.0 | публичный релиз (когда стабильны данные сетей и UX) |

История изменений: [docs/CHANGELOG.md](docs/CHANGELOG.md)

## Статус репозитория

Внутренний MVP. Package: `by.smartplanogram.plnanote`.  
Публикация: [docs/GITHUB.md](docs/GITHUB.md) (лучше private, пока не убраны секреты).

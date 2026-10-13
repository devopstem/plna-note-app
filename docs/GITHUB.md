# Публикация на GitHub — чеклист

## Сейчас публиковать?

**Рекомендация: ещё рано для публичного репозитория.** Сначала — private repo или локальный git.

| Критерий | Статус |
|----------|--------|
| Git-репозиторий инициализирован | Нет (на момент подготовки docs) |
| README / документация | Да (этот каталог + README) |
| Секреты вынесены | Нужно: `google-services.json`, `local.properties` |
| applicationId / package | `by.smartplanogram.plnanote` ✅ |
| Auth | Демо-коды в клиенте — ок для прототипа, не для продакшена |
| Firebase rules / production data | Экспорт в assets — проверьте, нет ли лишних PII |
| Лицензия | Не выбрана |

## Перед `git push` (обязательно)

1. `git init` (если ещё нет).
2. Убедиться, что в `.gitignore`:
   - `local.properties`
   - `app/google-services.json`
   - `/build`, `app/build`, `.gradle`, `.cxx`, `cpp-build/`
3. В репозитории только `google-services.json.example`.
4. Просмотреть `rtdb_export.json`: это демо-каталог магазина; если там чувствительные данные сети — заменить урезанным sample.
5. Выбрать **private** репозиторий, пока applicationId и Firebase не приведены в порядок.
6. Добавить `LICENSE` (MIT / Apache-2.0 / proprietary).

## Когда можно сделать public

- В Firebase Console добавлено Android-приложение с package `by.smartplanogram.plnanote` (и свежий `google-services.json`).
- Firebase: отдельный demo-проект или чистые rules + sample data.
- Auth не хардкодит продакшен-коды (или только demo flavor).
- Release-сборка и краткий «Getting started» проверены с нуля на чистой машине.

## Команды (когда будете готовы)

```bash
git init
git add README.md docs branding app gradle gradlew* build.gradle.kts settings.gradle.kts gradle.properties .gitignore
# НЕ добавлять: local.properties, google-services.json, cpp-build, .idea (целиком)
git commit -m "Initial commit: PLNA Smart Map Android + native core"
gh repo create plna-smart-map --private --source=. --remote=origin --push
```

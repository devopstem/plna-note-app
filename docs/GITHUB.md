# Публикация на GitHub

Репозиторий: `https://github.com/devopstem/plna-note-app`  
Текущая версия в коде: **0.2.0**

## Что не коммитить

Уже в `.gitignore` (проверьте перед push):

- `local.properties`
- `app/google-services.json` (в репо только `*.example`)
- `/build`, `app/build`, `.gradle`, `.cxx`, `cpp-build/`
- ключи / keystore

## Перед публичным (public) релизом

| Критерий | 0.2 |
|----------|-----|
| README + архитектура + WHY_CPP | да |
| Демо-доступ без кодов на экране | да |
| Демо-каталог не в поиске | да |
| Cleartext выключен, R8 на release | да |
| Firebase demo-проект / rules | по желанию владельца |
| LICENSE | не выбран — укажите proprietary / MIT при public |

Пока репозиторий может оставаться **private** или public с пометкой «preview 0.2».

## Команды обновления

```bash
git add -A
git status   # убедиться, что нет google-services.json
git commit -m "Release 0.2.0: Home, PDR, demo polish, architecture docs."
git push -u origin HEAD
git tag -a v0.2.0 -m "PLNA Note App 0.2.0"
git push origin v0.2.0
```

## Документы для читателя репо

1. [README.md](../README.md) — старт  
2. [ARCHITECTURE.md](ARCHITECTURE.md) — чертёж  
3. [WHY_CPP.md](WHY_CPP.md) — зачем native core  
4. [CHANGELOG.md](CHANGELOG.md) — что в версии  

# Сборка

## Требования

- Android Studio (JBR / JDK 17+)
- Android SDK (compileSdk 37, minSdk 24)
- NDK (в проекте использовался 28.x) + CMake 3.22.1
- Gradle Wrapper или локальный Gradle 9.6+

## Firebase

1. Скопируйте `app/google-services.json.example` → `app/google-services.json`.
2. Подставьте реальный файл из Firebase Console (проект с Realtime Database).
3. **Не коммитьте** боевой `google-services.json` в публичный репозиторий.

Офлайн-демо работает без сети через `app/src/main/assets/rtdb_export.json`.

## Debug APK

```bash
export JAVA_HOME=/path/to/android-studio/jbr
./gradlew :app:assembleDebug -Dorg.gradle.java.home="$JAVA_HOME"
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## C++ ядро (десктоп / clangd)

```bash
cmake -S app/src/main/cpp -B cpp-build -DCMAKE_EXPORT_COMPILE_COMMANDS=ON
cmake --build cpp-build
```

Симлинк `compile_commands.json` → `cpp-build/` (в `.gitignore`).

Документация ядра:

- [ARCHITECTURE.md](../app/src/main/cpp/docs/ARCHITECTURE.md)
- [DATA_MODEL.md](../app/src/main/cpp/docs/DATA_MODEL.md)
- [C_API.md](../app/src/main/cpp/docs/C_API.md)

## Брендинг

Исходник логотипа: `branding/plna-smart-map-logo.svg`  
Перегенерация PNG/mipmap: `python3 branding/render_logo.py app/src/main/res`

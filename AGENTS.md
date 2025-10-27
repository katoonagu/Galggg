# Repository Guidelines

## Project Structure & Module Organization
The Android app module lives in `app/`. Production code sits under `app/src/main/java/com/example/galggg`, layouts and drawables under `app/src/main/res`, native binaries in `app/src/main/jniLibs`, and configuration assets in `app/src/main/assets`. Shared test doubles and unit tests live in `app/src/test/java`. Prebuilt bundles extracted for reference reside in `_apk_extract/`, while reusable provisioning helpers are under `tools/`. Keep generated build artifacts in `build/` out of version control.

## Build, Test, and Development Commands
- `./gradlew assembleDebug` – build a debuggable APK.
- `./gradlew installDebug` – deploy the debug build to a connected device or emulator.
- `./gradlew testDebugUnitTest` – run JVM unit tests (Robolectric & JUnit).
- `./gradlew lint` – execute Android lint checks; address warnings before review.
Run commands from the repo root; add `--info` if you need Gradle task diagnostics.

## Coding Style & Naming Conventions
Source uses Java 17 with AndroidX. Follow Android Studio's default formatter (4-space indentation, braces on new lines for types). Class names are PascalCase, methods camelCase, constants UPPER_SNAKE_CASE. Resource IDs should be lowercase_with_underscores (e.g., `activity_main`). Prefer dependency injection-ready constructors over singletons. Run `Code > Reformat Code` before committing to normalize imports and spacing.

## Testing Guidelines
Unit tests reside beside code in mirrored packages under `app/src/test/java`. Name test classes `${ProductionClass}Test` and methods `shouldDoXWhenY`. Use Robolectric for Android framework surfaces; mock network layers with OkHttp's interceptors. Aim to cover new branches and error paths; add regression tests for bug fixes. Execute `./gradlew testDebugUnitTest` locally and include flaky reproductions in PR notes.

## Commit & Pull Request Guidelines
Commits in history are short and topic-focused; keep messages imperative and under 60 characters (`Fix DNS routing fallback`). Group related changes per commit to ease bisects. For pull requests, supply a concise summary, testing notes (`gradlew testDebugUnitTest`), and screenshots for UI changes. Link tracking tickets with `Fixes #123` when applicable and request at least one reviewer familiar with the affected area.

## Tools & External Assets
Run `tools/fetch-tun2socks.ps1` to refresh the bundled tun2socks binary and manually update sing-box assets as needed; commit the resulting files only after verifying licensing. Never expose production secrets—replace provisional tokens in `app/build.gradle` with environment-specific values before release tagging.
# AGENTS.md — Galggg (UI Compose интеграция из Figma)

## Цель
Быстро и точно перенести экраны из Figma в Android Jetpack Compose (Material3) в модуле `composeui`, соблюдая токены и компоненты дизайн‑системы.

## Как запускать проект
- Сборка: `./gradlew :app:assembleDebug`
- Compose‑модуль: `./gradlew :composeui:assemble`
- Минимум: Android Gradle Plugin/Compose версии — см. `build.gradle`, использовать BOM.

## Структура
- `composeui/src/main/kotlin/.../ui/screens` — экраны
- `composeui/.../ui/components` — атомарные/композитные компоненты
- Тема/токены: `composeui/.../ui/theme/*`

## Правила UI (кратко)
- Только Kotlin/Compose (Material3), без XML.
- Никакого хардкода цветов/размеров — только токены.
- Компоненты переиспользуемые, с `modifier` и превью.
- Доступность: `contentDescription`, контраст ≥ AA, тач‑таргет.

## Интеграция Figma MCP
- Editor: Cursor с Figma MCP (remote или local).
- Рабочий флоу:
  1) `get_design_context` по ссылке на фрейм/слой
  2) при необходимости `get_metadata` → сузить область
  3) `get_screenshot` для эталона
  4) `get_variable_defs` (локально) → маппинг на тему
  5) экспорт ассетов → `res/drawable*`
- Переводим структуру MCP → Jetpack Compose/Material3.

## Definition of Done (для PR)
- 1:1 по макету (сверка с MCP‑скриншотом)
- Используются токены/компоненты DS, нет дублирования
- Есть превью и базовый UI‑тест
- Нет хардкода, линтеры зелёные

## Команды
- Сборка: `./gradlew assemble`
- UI‑тесты (если есть): `./gradlew :composeui:connectedAndroidTest`

## Мини‑агенты
- **Compose Implementer**: переводит фрейм → Composable + превью.
- **Figma Bridge**: тянет `get_design_context`, `get_screenshot`, `get_variable_defs`, экспортирует ассеты.
- **Visual QA**: сравнивает превью с MCP‑скриншотом, фиксит отступы.

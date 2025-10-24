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

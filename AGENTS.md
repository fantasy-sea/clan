# AGENTS.md

## Project
`clan` (家助) — Android app for remote screen control (controller remotely operates a controlled phone). Single-module Jetpack Compose app, package `com.fantaysea.clan`.

## Current state
The app is still the default Android Studio template: `MainActivity.kt` just renders "Hello Android". The README's intended stack (MediaProjection, WebSocket + WebRTC, AccessibilityService) is **not implemented** — don't assume these components exist.

## Build (Windows)
- Use the Gradle wrapper: `.\gradlew.bat ...` — not a system Gradle (wrapper = Gradle 9.5.0).
- Toolchain: AGP 9.3.0, Kotlin 2.2.10 (Compose compiler plugin), Java 11 target, Compose BOM `2026.02.01`.

### Commands
- Debug APK: `.\gradlew.bat :app:assembleDebug`
- Install to device/emulator: `.\gradlew.bat :app:installDebug`
- Unit tests: `.\gradlew.bat :app:testDebugUnitTest`
- Instrumented tests (needs device/emulator): `.\gradlew.bat :app:connectedDebugAndroidTest`
- Lint: `.\gradlew.bat :app:lintDebug`

## Build-system quirks (AGP 9 — don't "fix" to old style)
- Built-in Kotlin: module applies only `android.application` + `kotlin.compose`; do **not** add `org.jetbrains.kotlin.android`.
- `compileSdk` uses the new block DSL: `compileSdk { version = release(37) }`, not `compileSdk = 37`.
- All deps go through the version catalog `gradle/libs.versions.toml` (`libs.*`); add entries there, don't inline versions.
- Repos are centralized in `settings.gradle.kts` (`RepositoriesMode.FAIL_ON_PROJECT_REPOS`) — never declare repositories in a module file.
- `org.gradle.configuration-cache=true` is enabled.

## Runtime
- minSdk 31 (Android 12+), targetSdk/compileSdk 37.
- SDK path lives in gitignored `local.properties`.
- MediaProjection / AccessibilityService / instrumented tests need a physical device or emulator.

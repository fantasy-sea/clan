# AGENTS.md

## Project
`clan` (家助) — Android app for remote screen control (controller remotely operates a controlled phone). Single-module Jetpack Compose app, package `com.fantaysea.clan`.

## Current state
UI closed loop is implemented and wired to the real backend; server-side control-session endpoints **are now available and wired** (P4):
- Navigation: `core/navigation` (`AppNavigation`, `HomeNavGraph`) with onboarding → home (`ui/home`) → `ui/control` / `ui/share` / `ui/session`.
- Navigation is reached through `Navigator` (`core/navigation/Navigator.kt`), exposed to the whole `NavHost` subtree via `LocalNavigator` (`staticCompositionLocalOf`, default `NoOpNavigator`). Screens call `LocalNavigator.current.navigateTo(...)` / `.back()` — they never touch `NavController`. `NavController.asNavigator()` is the single adapter point; keep it that way.
- `navigateTo` accepts `AppDestination` (`core/navigation/AppDestination.kt`), a plain marker interface — each module declares its own `@Serializable sealed interface XxxDestination : AppDestination`, so adding a module never changes `Navigator`. Register the destination (`composable<XxxDestination.Xxx>` / its own `navigation<XxxGraph>`) in `AppNavigation`; the NavController resolves the target across the whole graph, so cross-module jumps work as-is.
- UI code must not import `androidx.navigation.NavController`. `NavController` stays confined to `core/navigation`.
- Backend: `data/repository/RealClanRepository` implements `ClanRepository` over REST (`data/remote/ClanApi`) and WebSocket (`data/ws/*`); `ClanApplication` wires it for the whole app. Session endpoints (`POST/GET /api/v1/sessions`, accept/reject/end), `activeSession`, local permissions, and P4 WS messages (`SESSION_CONNECTED`, `WEBRTC_OFFER/ANSWER`, `ICE_CANDIDATE`, `CONTROL`, `CONTROL_RESULT`, `PERMISSION_STATUS`) are implemented.
- Server contract: see `clan-service/docs/websocket.md` and `clan-service/docs/webrtc.md`. `RemoteEvent` is session-centric (`sessionId`), not request-id-centric. The server sends `CONTROL_REQUEST` / `SESSION_STARTED` / `SESSION_ENDED` with `sessionId`.
- Reserved skeletons, **not functional**: `media/ScreenCaptureManager`, `media/WebRtcManager`, `service/ScreenCaptureService`, AccessibilityService. MediaProjection / WebRTC media / touch injection are not implemented — don't assume they work. The session screens send `SESSION_CONNECTED` as a placeholder until the real PeerConnection callback is wired, and the controlled screen only ACKs `CONTROL` instead of executing it.
- Unit tests in `app/src/test` cover pairing-code logic, code input, `RealClanRepository` REST/WS/session behavior, `WsProtocol`/`WsEventMapper` P4 mapping, and ViewModel capability/session flows.

## Build (Windows)
- Use the Gradle wrapper: `.\gradlew.bat ...` — not a system Gradle (wrapper = Gradle 9.5.0).
- Toolchain: AGP 9.3.0, Kotlin 2.2.10 (Compose compiler plugin), Java 11 target, Compose BOM `2026.02.01`.

### Commands
- Debug APK: `.\gradlew.bat :app:assembleDebug`
- Quick Kotlin check: `.\gradlew.bat :app:compileDebugKotlin`
- Lint: `.\gradlew.bat :app:lintDebug`

## Build-system quirks (AGP 9 — don't "fix" to old style)
- Built-in Kotlin: module applies only `android.application` + `kotlin.compose`; do **not** add `org.jetbrains.kotlin.android`.
- `compileSdk` uses the new block DSL: `compileSdk { version = release(37) }`, not `compileSdk = 37`.
- All deps go through the version catalog `gradle/libs.versions.toml` (`libs.*`); add entries there, don't inline versions.
- Repos are centralized in `settings.gradle.kts` (`RepositoriesMode.FAIL_ON_PROJECT_REPOS`) — never declare repositories in a module file.
- `org.gradle.configuration-cache=true` is enabled.

## Verification
**用户已明确要求跳过验证阶段**：改完代码不要主动跑 `compileDebugKotlin` / `assembleDebug` / `testDebugUnitTest` / `lintDebug`，直接交付改动说明即可。只有用户显式要求验证时才运行。

## Runtime
- minSdk 31 (Android 12+), targetSdk/compileSdk 37.
- SDK path lives in gitignored `local.properties`.
- MediaProjection / AccessibilityService / WebRTC need a physical device or emulator, but device-based checks are out of scope for verification.

# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

**Copiloto para Motoristas** — a profitability assistant for rideshare/delivery drivers (Uber, 99, inDrive). It reads incoming ride offers from the screen, computes R$/km, R$/hour and net profit against the driver's costs, and surfaces a traffic-light recommendation as a floating card plus a spoken announcement.

Monorepo with two independent projects:
- `android/` — native Android client (Kotlin, Jetpack Compose, MVVM, Room, Coroutines).
- `backend/` — Django REST + PostgreSQL API, containerized with Docker.

> **Scope note:** The spec's Module E was specified as a "Secret Camera" (covert recording that bypasses the OS camera-preview requirement). The covert/bypass design is intentionally **not** built — covertly recording people is a legal/privacy problem. Instead Module E is implemented as a **transparent safety dashcam**: CameraX video+audio in a foreground service with a *visible, persistent* "Gravando viagem" notification, no 1x1 preview bypass. The 5 GB rotating buffer and scoped storage from the spec are kept (`DashcamStorage`, `DashcamService`).

## Commands

### Android (`cd android`)
- Build debug APK: `./gradlew :app:assembleDebug`
- Run unit tests (engine + parser): `./gradlew :app:testDebugUnitTest`
- Run a single test class: `./gradlew :app:testDebugUnitTest --tests "com.copiloto.motorista.engine.RideCalculatorTest"`
- Install on a connected device/emulator: `./gradlew :app:installDebug`
- The `gradlew` here needs `JAVA_HOME` on JDK 17. `local.properties` points at the local Android SDK.
- Build constraints: `compileSdk`/`targetSdk` = 36 (matches the installed platform), `minSdk` = 26, AGP 8.9.3 / Gradle 8.11.1. `gradle.properties` sets `android.suppressUnsupportedCompileSdk=36` and `android.overridePathCheck=true` (the repo path contains the non-ASCII `ç`).

### Backend (`cd backend`)
- Run everything with Postgres: `docker compose up --build` (migrations + collectstatic run automatically via `entrypoint.sh`; API on `:8000`).
- Local without Docker/Postgres: set `USE_SQLITE=True` (plus `DJANGO_SECRET_KEY`) to fall back to SQLite.
- Checks: `USE_SQLITE=True python manage.py check`
- Tests: `USE_SQLITE=True python manage.py test`
- Single test: `USE_SQLITE=True python manage.py test api.tests.RideApiTests.test_create_ride_persists_and_links_driver`

## Architecture

### The ride pipeline (the core flow)
A ride offer flows through the same path whether it is scraped or simulated:

```
RideAccessibilityService (Module A)  ─┐
                                      ├─▶ OverlayService.showOffer(RideOffer)
MainViewModel.simulateRide (Module G)─┘
                                           │  (foreground service)
                                           ▼
            RideCalculator.evaluate(offer, profile)  ──▶ RideEvaluation
                                           │
              ┌────────────────────────────┼───────────────────────────┐
              ▼                             ▼                           ▼
     OverlayController (Module C)   TtsSpeaker (Module D)   RideHistoryRepository → Room
     (WindowManager card)           (TextToSpeech pt-BR)    then SyncScheduler.syncNow
```

- **`OverlayService`** is the orchestrator. Offers reach it only as an `Intent` (primitive extras, see its `companion object`), never as objects — this is the single entry point to the pipeline and the only component that touches the engine, overlay, TTS and persistence together.
- **`RideCalculator`** (in `engine/`) is pure and side-effect-free — all profit/classification math lives here and is unit-tested. Formulas and the GREEN/YELLOW/RED rule are documented in that file.
- **`RideParser`** (in `engine/`) turns scraped screen text into a `RideOffer` with locale-tolerant regex. Note the deliberate heuristic: it **sums every km and every minute** found on screen (rideshare apps show pickup + trip legs separately). This can over-count if an app also shows a combined total.

### Why offers travel as Intents
`OverlayService` runs as a `specialUse` foreground service so the OS keeps the overlay/TTS alive while the driver waits. Both producers (accessibility service, UI simulator) start it the same way, so there is no shared in-memory bus to keep in sync.

### Data / persistence split
- **Ride history** → Room (`CopilotoDatabase`, offline-first). Rows carry `synced`/`remoteId`; `SyncWorker` (WorkManager, network-constrained) pushes unsynced rows to the backend and marks them.
- **Driver config** → DataStore (`DriverProfileRepository`), not Room — it's a single small reactive record. A per-install `deviceId` (also in DataStore) scopes all backend data; there is no login in the MVP.
- **Dependency wiring** is manual via `CopilotoContainer` (held by `CopilotoApp.container`), reachable from services, workers and `AndroidViewModel`s. No Hilt/Dagger.

### Backend identity model
The API has no auth; the client sends an `X-Device-Id` header. `views.resolve_device_id` turns it into a `DriverProfile` (`get_or_create`), and every `RideHistory` is scoped to that driver. `backend/api/models.py` mirrors the Android domain models; keep the two in sync when fields change.

## Conventions
- Portuguese (pt-BR) is the user-facing language (UI strings, TTS phrases, currency `R$`). Code identifiers and comments are English.
- The three special Android permissions (overlay, accessibility, foreground-service) are checked at runtime in `ui/PermissionUtils.kt` and surfaced on the Home screen; the accessibility package allow-list lives in `res/xml/accessibility_service_config.xml` **and** is re-checked in `RideAccessibilityService`.
- When adding a monitored rideshare app, update both the XML config and `RideSource`.

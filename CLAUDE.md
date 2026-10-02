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
- Single test: `USE_SQLITE=True python manage.py test api.tests.RideApiTests.test_create_ride_links_to_authenticated_driver`
- Deploy to a VPS (HTTP on `IP:8000`): `docker compose -f docker-compose.prod.yml up -d --build` — see `backend/DEPLOY.md`. WhiteNoise serves static files so the admin is styled with `DEBUG=False`; the `prod` compose does not publish Postgres to the internet.

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

### Auth & identity model (JWT)
The API uses **JWT** (`djangorestframework-simplejwt`). A `DriverProfile` is a `OneToOne` to Django's `User`; every `RideHistory` is scoped to `driver__user=request.user`, so data follows the driver across devices. `backend/api/models.py` mirrors the Android domain models; keep the two in sync when fields change.

**Roles:** an **operator** of the Central de Operações is any `is_staff` user (created/promoted in the admin). The `IsOperator` permission (`api/permissions.py`) gates the alert list and operator actions; regular drivers are never staff. Login/register tokens embed `username` + `is_staff` claims (`CopilotoTokenObtainPairSerializer`) so the **signaling server** can authorize WebRTC connections without a DB call. Refresh tokens **rotate** and are **blacklisted** on logout (`/api/auth/logout/`); `login`/`register` are rate-limited (`auth` throttle scope). When behind a TLS proxy, set `SECURE_SSL=True` to turn on secure cookies + HSTS.

On Android, `TokenStore` (DataStore) holds the access/refresh tokens. `AuthInterceptor` attaches `Authorization: Bearer <access>` to every request except those marked `No-Auth` (login/register/refresh); `TokenAuthenticator` refreshes the access token once on a 401 and clears the tokens (→ login screen) if the refresh fails. A one-time **LGPD consent gate** (`ConsentScreen`/`ConsentStore`) runs before `AuthScreen`; nothing else loads until the driver accepts.

### API routes
| Método | Rota | Auth | Descrição |
|---|---|---|---|
| `GET` | `/api/health/` | não | liveness/readiness (checa o banco) |
| `POST` | `/api/auth/register/` | não | `{username, email?, password}` → `{access, refresh, user}` |
| `POST` | `/api/auth/login/` | não | `{username, password}` → `{access, refresh}` |
| `POST` | `/api/auth/refresh/` | não | `{refresh}` → `{access}` |
| `POST` | `/api/auth/logout/` | sim | `{refresh}` → 205; invalida o refresh (blacklist) |
| `GET` | `/api/auth/me/` | sim | `{username, email, is_operator}` |
| `GET` / `POST` | `/api/rides/` | sim | lista / cria o histórico de corridas do usuário |
| `GET` / `PUT` | `/api/profile/` | sim | lê / atualiza a configuração do motorista |
| `POST` | `/api/alerts/` | sim (motorista) | dispara um alerta de pânico |
| `GET` | `/api/alerts/` | **operador** | lista alertas da Central |
| `PATCH` | `/api/alerts/<id>/` | **operador** | ação do operador sobre um alerta |

### Live video & signaling auth
`signaling/server.js` only does the WebRTC handshake (one room per `alertId`). Every WS connection must carry the driver/operator JWT as `?token=<access>` — the server verifies it (HS256, same `DJANGO_SECRET_KEY` via `SIGNALING_JWT_SECRET`) and **only operators may `watch`**. `StreamingService` appends the driver's token; the dashboard's `VideoFeed` appends the operator's. STUN is built in; a **TURN** relay (coturn) is added for production via the `TURN_*` config (`BuildConfig` on Android, `VITE_TURN_*` on the dashboard). TLS (Caddy), TURN and Postgres backup live in `backend/docker-compose.tls.yml` — see `backend/DEPLOY.md`.

### How to log in (app)
1. Suba o backend (`cd backend && docker compose up --build`).
2. No app, ajuste `API_BASE_URL` em `android/app/build.gradle.kts` se não for emulador (o padrão `http://10.0.2.2:8000/` é o host local visto do emulador).
3. Abra o app → tela de login. Toque em **"Cadastre-se"**, crie usuário/senha (a senha passa pelos validadores do Django) e o app guarda os tokens e entra. Nas próximas vezes, use **Entrar**. Para trocar de conta, use **Ajustes → Sair da conta**.

## Conventions
- Portuguese (pt-BR) is the user-facing language (UI strings, TTS phrases, currency `R$`). Code identifiers and comments are English.
- The three special Android permissions (overlay, accessibility, foreground-service) are checked at runtime in `ui/PermissionUtils.kt` and surfaced on the Home screen.
- **Monitored apps are user-configurable.** `accessibility_service_config.xml` no longer restricts `packageNames`; the allow-list is built at runtime in `RideAccessibilityService` from `RideSource.BUILT_INS` (Uber/99/inDrive) plus the driver's picks in `MonitoredAppsStore` (DataStore). The driver adds apps in **Ajustes → Apps monitorados** (picker reads installed launchable apps via the manifest `<queries>`). A driver-added app parses with the same generic heuristics under `RideSource.OTHER`, carrying its label in `RideOffer.sourceLabel` so the overlay/history show the real name. To add a *built-in* default, extend `RideSource.BUILT_INS`.

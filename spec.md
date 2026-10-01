# App Specification: Driver Earnings Assistant (Copiloto para Motoristas)

## 1. Project Overview
A native Android application designed to assist rideshare and delivery drivers (Uber, 99, inDrive) by reading incoming ride offers directly from the screen, calculating real-time profitability (R$/km, R$/hour), and overlaying a visual/auditory recommendation. 

## 2. Tech Stack
- **Mobile (Frontend):** 100% Native Android (Kotlin).
  - UI: Jetpack Compose.
  - Architecture: MVVM (Model-View-ViewModel) + Kotlin Coroutines for async processing.
  - Local Database: Room Database (SQLite) for offline-first capabilities.
- **Backend (API):** Python with Django REST Framework.
  - Database: PostgreSQL.
  - Deployment/Containerization: Docker.
- **Monetization:** None for the MVP. 

## 3. Core Android Permissions & Services (CRITICAL)
The app heavily relies on deeply integrated Android APIs. The following permissions and services must be correctly configured in the `AndroidManifest.xml`:
- `android.permission.BIND_ACCESSIBILITY_SERVICE`: To read the ViewNode tree of rideshare apps.
- `android.permission.SYSTEM_ALERT_WINDOW`: To draw the floating UI card over other apps.
- `android.permission.CAMERA` & `android.permission.RECORD_AUDIO`: For the Secret Camera feature.
- `android.permission.FOREGROUND_SERVICE` (types: `camera`, `microphone`, `specialUse`): To prevent the OS from killing the app while waiting for rides or recording in the background.

## 4. Feature Modules

### Module A: Scraping Engine (AccessibilityService)
- **Objective:** Detect when a ride offer appears on screen, extract the text (Price, Distance, Time, Pickup/Dropoff), and parse it into a structured Data Class.
- **Implementation:** A custom class extending `AccessibilityService`. 
- **Filtering:** Must filter `AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED` and `TYPE_WINDOW_CONTENT_CHANGED` specifically for packages like `com.ubercab.driver` and `com.taxis99`.
- **Parsing Logic:** Use Regex handlers to extract numbers and time formats (e.g., converting "R$ 15,50" to float `15.50`, "15 min" to integer `15`).

### Module B: Calculation Engine
- **Objective:** Process the scraped data against the driver's configured costs.
- **Formulas:**
  - `Cost per km` = (Fuel Price / Km per Liter) + Maintenance Cost per km.
  - `Total Cost` = Total Distance * Cost per km.
  - `Gross R$/km` = Price / Total Distance.
  - `Gross R$/hour` = (Price / Total Time in minutes) * 60.
  - `Net Profit` = Price - Total Cost.
- **Evaluation Rule:** Classify the ride as GREEN (Above Target), YELLOW (Acceptable/Review), or RED (Below Target) based on driver-defined thresholds.

### Module C: Floating UI (Overlay)
- **Objective:** Display the parsed data, net profit, and color evaluation (Green/Yellow/Red) over the active rideshare app.
- **Implementation:** Use `WindowManager` to inflate a Jetpack Compose view or XML layout. The layout must be lightweight, draggable via touch listeners, and non-blocking to underlying touches.

### Module D: Voice Notification (Text-To-Speech)
- **Objective:** Announce the ride details audibly so the driver keeps their eyes on the road.
- **Implementation:** Android `TextToSpeech` API. Triggered asynchronously by the Calculation Engine once parsing is complete.
- **Example String:** "Corrida verde. 15 reais, 5 quilômetros. Lucro de 10 reais."

### Module E: Secret Camera (Background Recording)
- **Objective:** Record audio and video discretely while a ride is active for driver safety.
- **Implementation:** `CameraX` API running in a Foreground Service showing a persistent notification. 
- **Bypass UI Requirement:** Requires a 1x1 pixel invisible `SurfaceView` or `TextureView` attached to the WindowManager to bypass the OS requirement that cameras need a preview surface.
- **Storage Management:** Save files to the app's scoped external storage, managing a rotation buffer (auto-delete oldest files if total folder size > 5GB).

### Module F: Backend Synchronization & Admin (Django + PostgreSQL)
- **Objective:** Sync configurations, backup ride history, and manage users.
- **Data Models:**
  - `DriverProfile`: Configs (Fuel type, avg consumption, target R$/km).
  - `RideHistory`: Date, AppSource, Gross Price, Distance, Time, Net Profit, Accepted (Boolean).
- **Network & Sync:** Retrofit + OkHttp. Save all captured rides to Room Database first, then use WorkManager to sync local `RideHistory` with the Django API when network conditions are stable.

### Module G: Ride Simulator (Dev/Test Tool)
- **Objective:** Allow the developer and the user to test the overlay, calculation, and voice features without waiting for a real ride offer.
- **Implementation:** A "Simulate Ride" button in the main Compose UI. When clicked, it generates a mock ride data object (e.g., Uber, R$ 18.50, 6km, 12 min) and injects it directly into the `Calculation Engine`, bypassing the `AccessibilityService` but triggering the Floating UI and Voice Notification exactly as a real ride would.

## 5. Execution Milestones for the AI Assistant
1. **Milestone 1 - Foundation:** Scaffold the Android Native project (Kotlin + Jetpack Compose) and the Django REST project. Set up Room DB and PostgreSQL schemas.
2. **Milestone 2 - The Core Engine & Simulator:** Implement the `Calculation Engine` and the **Ride Simulator** (Module G) to allow immediate testing. Then, implement the `AccessibilityService` and route its parsed data to the same engine.
3. **Milestone 3 - Overlay & UI:** Implement the `WindowManager` floating overlay. Connect the engine to update the floating UI in real-time.
4. **Milestone 4 - Safety & Audio:** Integrate the `TextToSpeech` engine and develop the `CameraX` Foreground Service with circular storage buffer.
5. **Milestone 5 - Sync & Polish:** Wire up Retrofit, setup WorkManager for background syncing from Room DB to Django REST API, and finalize the Compose configuration screens.
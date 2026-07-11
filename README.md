# FishWellness

A native Android app that blocks access to selected apps during specified time schedules — an enhanced version of Digital Wellbeing.

## Features

- **App Selection** — Pick any installed app to block
- **Time Schedules** — Create recurring schedules (e.g., 22:00–07:00 every night) with day-of-week selection
- **Quick Block** — Temporarily block all selected apps for 15–120 minutes on demand
- **Block Overlay** — Full-screen blocking activity shown on top of any blocked app
- **Live Enforcement** — Re-evaluates the foreground app when rules or schedule boundaries change

## How It Works

1. `AppBlockAccessibilityService` listens for `TYPE_WINDOW_STATE_CHANGED` events
2. Room flows feed `BlockingPolicyStore`, the runtime's immutable source of truth
3. `BlockingPolicyEvaluator` applies quick-block, weekly schedule, and daily-limit contracts
4. The accessibility service re-evaluates the current app every 500 ms and renders an accessibility overlay when blocked

Overnight schedules belong to the day on which they start. For example, a Monday
22:00-14:00 policy remains active until Tuesday 14:00. Equal start and end times
represent a 24-hour window.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| UI | Jetpack Compose + Material 3 |
| DI | Hilt |
| DB | Room |
| Policy engine | Pure Kotlin + `java.time` |
| Monitoring | AccessibilityService |

## Build & Run

```bash
# Debug build
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## Required Permissions (user-granted at runtime)

- **Accessibility Service** — Detect foreground app changes
- **Usage Access** — Query app usage stats only when daily limits are configured

## Project Structure

```
app/src/main/java/com/fish/wellness/
├── FishApplication.kt          # Hilt entry point
├── MainActivity.kt             # Single-activity Compose host
├── data/                       # Room database layer
│   ├── entity/                 # Policy, BlockedApp, QuickBlockSession
│   └── dao/                    # Data access objects
├── domain/blocking/            # Schedule matching and blocking decisions
├── di/                         # Hilt modules
├── manager/
│   └── BlockingPolicyStore.kt  # Reactive immutable rule snapshots
├── service/
│   ├── AppBlockAccessibilityService.kt  # Monitors foreground changes
│   └── BlockOverlayActivity.kt          # Last-resort overlay fallback
├── model/                      # Domain models
├── util/                       # Permission & app utilities
└── ui/
    ├── theme/                  # Material 3 theme
    ├── navigation/             # NavHost routes
    └── screen/                 # Home, AppPicker, ScheduleEdit
```

## Min SDK

Android 8.0 (API 26)

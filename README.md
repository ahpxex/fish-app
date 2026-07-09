# FishWellness

A native Android app that blocks access to selected apps during specified time schedules — an enhanced version of Digital Wellbeing.

## Features

- **App Selection** — Pick any installed app to block
- **Time Schedules** — Create recurring schedules (e.g., 22:00–07:00 every night) with day-of-week selection
- **Quick Block** — Temporarily block all selected apps for 15–120 minutes on demand
- **Block Overlay** — Full-screen blocking activity shown on top of any blocked app
- **Foreground Service** — Keeps blocking active across reboots

## How It Works

1. `AppBlockAccessibilityService` listens for `TYPE_WINDOW_STATE_CHANGED` events
2. When the foreground app changes, `AppBlockManager` checks if that package is currently blocked
3. A package is blocked if: it's in the blocked-apps list AND (an active quick-block session exists OR an enabled schedule is currently active)
4. If blocked, `BlockOverlayActivity` launches on top, preventing app usage

## Tech Stack

| Layer | Technology |
|-------|-----------|
| UI | Jetpack Compose + Material 3 |
| DI | Hilt |
| DB | Room |
| Scheduling | AlarmManager + WorkManager |
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

- **SYSTEM_ALERT_WINDOW** — Display overlay on blocked apps
- **Accessibility Service** — Detect foreground app changes
- **Usage Access** — Query app usage stats
- **RECEIVE_BOOT_COMPLETED** — Restart service after reboot
- **SCHEDULE_EXACT_ALARM** — Precise schedule triggers

## Project Structure

```
app/src/main/java/com/fish/wellness/
├── FishApplication.kt          # Hilt entry point
├── MainActivity.kt             # Single-activity Compose host
├── data/                       # Room database layer
│   ├── entity/                 # BlockedApp, Schedule, QuickBlockSession
│   └── dao/                    # Data access objects
├── di/                         # Hilt modules
├── manager/
│   └── AppBlockManager.kt      # Core blocking logic
├── service/
│   ├── AppBlockAccessibilityService.kt  # Monitors foreground changes
│   ├── BlockOverlayActivity.kt          # Full-screen block screen
│   ├── ScheduleForegroundService.kt     # Keeps blocking alive
│   └── ScheduleAlarmReceiver.kt         # Quick-block expiry alarms
├── receiver/
│   └── BootReceiver.kt          # Restart on boot
├── model/                      # Domain models
├── util/                       # Permission & app utilities
└── ui/
    ├── theme/                  # Material 3 theme
    ├── navigation/             # NavHost routes
    └── screen/                 # Home, AppPicker, ScheduleEdit
```

## Min SDK

Android 8.0 (API 26)

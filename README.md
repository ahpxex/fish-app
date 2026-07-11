# FishWellness

A native Android app that blocks distracting apps on a schedule, with per-app daily
time limits and on-demand quick blocking — an enhanced, self-hosted take on Digital
Wellbeing. Blocking is enforced by an `AccessibilityService`, so it works across the
whole system without root.

> Heads-up: because enforcement relies on the Accessibility permission, **you must
> exempt FishWellness from battery optimization**, or aggressive OEM power managers
> will silently turn the permission off. See [Keep it alive](#4-keep-it-alive-most-important)
> — this is the single most common reason blocking "randomly stops working".

## Features

- **Policies** — Group apps under a named policy with its own schedule and limits
- **Time Schedules** — Recurring windows (e.g. 22:00–07:00 nightly) with day-of-week selection; overnight windows are supported
- **Daily Limits** — Allow an app for N minutes per day, then block it for the rest of the day, with a live countdown overlay
- **Quick Block** — Block everything in every policy immediately for a set duration
- **Password Protection** — Optionally require a password to change or disable policies
- **Block Overlay** — Full-screen block screen drawn on top of any blocked app, with a fallback activity if the overlay can't attach
- **Live Enforcement** — Re-evaluates the foreground app the moment rules or schedule boundaries change

## Download & Install

1. Grab the latest signed APK from the [**Releases**](https://github.com/ahpxex/fish-app/releases/latest) page.
2. Open the APK on your phone and allow installation from unknown sources when prompted.
3. If you previously installed a build signed with a different key, **uninstall the old
   version first** — Android refuses updates when the signature changes.

Minimum Android version: **8.0 (API 26)**.

## Setup Guide

FishWellness needs a few permissions to work. Grant them in order; the home screen
shows a "Permissions Required" card with a **Grant** shortcut for each.

### 1. Accessibility Service (required)

Home screen → **Grant** next to "Accessibility service" → find **FishWellness App
Blocker** in the list → turn it on.

- On **Android 13+**, sideloaded apps show the Accessibility toggle greyed out under
  "Restricted setting". If so: **Settings → Apps → FishWellness → ⋮ (top-right menu) →
  Allow restricted settings**, then enable the toggle.

This permission lets the app detect which app is in the foreground so it can block it.

### 2. Usage Access (only for daily limits)

Home screen → **Grant** next to "Usage access" → enable FishWellness.

Only needed if you use per-app **daily time limits**; scheduled/quick blocking works
without it.

### 3. Overlay / Draw over other apps

The block screen is drawn as an accessibility overlay and normally needs no extra
grant. If your OEM strips it, allow **"Display over other apps"** for FishWellness in
Settings → Apps → FishWellness.

### 4. Keep it alive (MOST IMPORTANT)

**Why this matters — the failure everyone hits:** Android *removes* an app's
Accessibility permission whenever that app is **force-stopped**. Ordinary background
memory reclaim does *not* do this (the service just rebinds), but a force-stop does —
and OEM battery managers force-stop backgrounded apps aggressively to save power. When
that happens, FishWellness's Accessibility permission is silently switched off and all
blocking stops until you re-enable it. Exempting the app from battery optimization is
what prevents those force-stops.

Do **all** of the following:

- **Disable battery optimization**: Settings → Apps → FishWellness → Battery → set to
  **Unrestricted** / **Don't optimize**.
- **Lock the app in Recents**: open Recents, find the FishWellness card, and lock it
  (long-press the card or tap the lock/pin icon) so "clear all" won't force-stop it.
- **Allow auto-start / background activity** (see your brand below).

#### Per-brand steps

| Brand (skin) | Where to look |
|---|---|
| **OPPO / OnePlus / realme** (ColorOS) | Settings → Battery → **App battery management** → FishWellness → turn on **Allow background activity** and **Allow auto launch**; disable **Sleep / Deep sleep** for it. Also App info → Battery → **Allow auto-launch**. |
| **Xiaomi / Redmi / POCO** (MIUI / HyperOS) | Settings → Apps → Manage apps → FishWellness → **Autostart: ON**; Battery saver → **No restrictions**. Lock the app in Recents. |
| **Huawei / Honor** (EMUI / HarmonyOS / MagicOS) | Settings → Battery → **App launch** → FishWellness → switch to **Manage manually** and enable **Auto-launch**, **Secondary launch**, and **Run in background**. |
| **vivo / iQOO** (OriginOS / Funtouch) | Settings → Battery → **Background power consumption management** → FishWellness → **Allow**. Settings → Apps → **Autostart** → enable FishWellness. |
| **Samsung** (One UI) | Settings → Apps → FishWellness → Battery → **Unrestricted**. Settings → Battery → Background usage limits → remove FishWellness from **Sleeping/Deep-sleeping apps**; turn off **Put unused apps to sleep**. |
| **Stock Android / Pixel** | Settings → Apps → FishWellness → Battery → **Unrestricted**. |

For exact, per-device instructions, see [dontkillmyapp.com](https://dontkillmyapp.com).

> Note: force-stopping an app from Settings, or a system-initiated force-stop, will
> always clear the Accessibility permission — that is Android's design (a force-stop
> means "revoke trust"). No app can prevent the *clearing* itself; the fix is to stop
> the OEM from issuing force-stops in the first place, which the steps above do.

## How It Works

1. `AppBlockAccessibilityService` listens for `TYPE_WINDOW_STATE_CHANGED` /
   `TYPE_WINDOWS_CHANGED` events to learn the current foreground package.
2. Room flows feed `BlockingPolicyStore`, the runtime's immutable source of truth.
3. `BlockingPolicyEvaluator` applies the quick-block, weekly-schedule, and daily-limit
   contracts to produce a decision.
4. The service renders an accessibility overlay (or a fallback `BlockOverlayActivity`)
   when the foreground app should be blocked, and a live countdown while a daily limit
   is being consumed.

Overnight schedules belong to the day on which they start — a Monday 22:00–14:00 policy
stays active until Tuesday 14:00. Equal start and end times represent a 24-hour window.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| UI | Jetpack Compose + Material 3 |
| DI | Hilt |
| DB | Room |
| Persistence | DataStore (preferences) |
| Policy engine | Pure Kotlin + `java.time` |
| Monitoring | AccessibilityService |

## Build from Source

Requires JDK 17–21 (the Gradle 8.9 / AGP 8.7 toolchain does not run on JDK 24+) and the
Android SDK (compileSdk 35).

```bash
# Debug build
./gradlew assembleDebug        # → app/build/outputs/apk/debug/FishWellness-v<version>-debug.apk

# Install on a connected device
./gradlew installDebug
```

### Signing a release build

Create `keystore.properties` in the project root (it is gitignored):

```properties
storeFile=keystore/release.jks
storePassword=your-store-password
keyAlias=your-alias
keyPassword=your-key-password
```

Then:

```bash
./gradlew assembleRelease       # → app/build/outputs/apk/release/FishWellness-v<version>.apk
```

Release builds are minified and resource-shrunk with R8. Keep the keystore and its
passwords backed up — losing them means you can no longer ship signature-compatible
updates.

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

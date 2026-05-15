# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Dynamic Island implementation for Android (Android 8.0+). It creates a floating pill-shaped overlay that displays notifications in an iOS-style "Dynamic Island" manner. The app uses the NotificationListenerService to intercept notifications and display them in a floating window.

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Clean build
./gradlew clean
```

CI builds on push to main via `.github/workflows/build.yml` - outputs to `app/build/outputs/apk/debug/app-debug.apk`.

## Architecture

### Core Components

- **MainActivity** (`ui/MainActivity.kt`): Setup screen with permission requests and sliders for appearance customization. Uses ViewBinding with `activity_main.xml`.

- **FloatingService** (`service/FloatingService.kt`): Foreground service that manages the floating `IslandView`. Runs as `TYPE_APPLICATION_OVERLAY`. Receives notification broadcasts and updates preferences.

- **NotificationService** (`service/NotificationListenerService.kt`): `NotificationListenerService` subclass that intercepts all notifications and broadcasts them to `FloatingService`. Maps known packages to Chinese app names.

- **IslandView** (`ui/IslandView.kt`): Custom `FrameLayout` rendering the pill shape. Handles expand/collapse animations with `ValueAnimator`, `AnimatorSet`, and `OvershootInterpolator`/`DecelerateInterpolator`. Color-codes by app type (music=green, phone=blue, WeChat=green, QQ=cyan, other=purple).

- **IslandPrefs** (`utils/IslandPrefs.kt`): SharedPreferences wrapper managing 4 settings: `positionY`, `widthPercent`, `pillWidthDp`, `expandedHeightDp`.

- **BootReceiver** (`service/BootReceiver.kt`): BroadcastReceiver that starts `FloatingService` on device boot.

### Notification Flow

```
NotificationService (listener)
    ↓ broadcasts ACTION_NOTIFICATION
FloatingService (receives)
    ↓ calls
IslandView.showNotification(pkg, appName, title, body)
    ↓ triggers expand animation
Auto-collapse after 5 seconds via Handler
```

### Preferences Update Flow

```
MainActivity slider change
    ↓ updates IslandPrefs
    ↓ broadcasts ACTION_UPDATE_PREFS
FloatingService.receiver
    ↓ calls applyPrefs()
    ↓ updates window Y position
    ↓ updates IslandView via updatePrefs()
```

### Key Implementation Details

- Floating window uses `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL | FLAG_LAYOUT_IN_SCREEN`
- Pill corner radius: 28dp; collapsed height: 32dp; dot indicator: 7dp diameter
- Color blending uses `blendColors()` helper for accent color with base pill color
- Music apps recognized by hardcoded package set: netease cloudmusic, qqmusic, kugou, spotify, apple music
- Uses `RECEIVER_NOT_EXPORTED` for notification receiver (Android 13+ compatible)

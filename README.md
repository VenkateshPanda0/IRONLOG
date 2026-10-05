# Ironlog

Ironlog is a local-first Android training app (Kotlin, Jetpack Compose, Room) in the style of STNDRD: structured programs, a fast set logger, nutrition tracking, progress analytics, wellness tracking and a long-term achievement system. No account, no cloud. Everything except optional online food lookup stays on the phone.

## Status

- `./gradlew assembleDebug` builds `app/build/outputs/apk/debug/app-debug.apk` (about 72 MB: exercise demo images plus ML Kit's on-device pose model).
- `./gradlew testDebugUnitTest`: 124 tests pass, including a Robolectric + Roborazzi walkthrough that onboards, logs a workout, food, cardio, habits and measurements, runs a physique check, and screenshots every main screen into `app/build/screens/`.
- `./gradlew lintDebug`: 0 errors.
- Not verified: install and use on a physical device or emulator (none available in the build environment), live Open Food Facts requests from the app, the Google code scanner UI, notification delivery on a real device (the alarm-to-notification path is tested under Robolectric).

## Features

- **Onboarding**: six steps (you, body, goal, goal physique, training days, plan) that create a program and nutrition targets.
- **Train**: active program, program detail and builder (staple lifts, all 17 muscle groups trained each week), quick workouts by focus and time, mobility sessions, cardio entry, history.
- **Workout logger**: live timer with pause, last-time hints, warm-up / working / drop / rest-pause sets, insert drop or rest-pause sets mid-workout, swap exercise (with revert), reorder, notes, rest timer, summary with personal bests and shareable image cards.
- **Library**: 876 exercises, search and filters, favourites, custom exercises, animated stick-figure demos for 565 strength exercises (25 movement patterns: squat, hinge, lunge, presses, rows, pull-ups, curls and more, with bars, dumbbells, cables, benches and machines drawn in) plus start/end photo demos for 873 exercises, history, records and estimated 1RM trend.
- **Nutrition**: daily diary by meal, macro ring and bars, all 7,793 USDA SR Legacy foods offline, Open Food Facts search and barcode scanning, portions, recipes, copy day, weekly calorie balance, target presets.
- **Wellness**: morning readiness check-in with training advice, water, steps, sleep, habits with streaks, cardio log with timer, pace and calorie estimate, body measurements.
- **Physique check**: pick one of ten popular physique goals (Classic, Men's Physique, Bodybuilder, Athletic, Powerlifter, Lean; Bikini, Wellness, Figure, Athletic), then check a front photo. On-device pose detection and segmentation (ML Kit, bundled models, no network, no language model) measure shoulder, waist, hip and thigh widths; rule-based coaching compares the ratios with the goal and gives a match score, findings, muscles to prioritise and a calorie direction. The program builder can add extra volume for those muscles. Checks are saved as history.
- **Progress**: weight trend with 7-day average and goal, measurements, strength, weekly volume and sets per muscle, cardio, daily trends, progress photos.
- **Profile**: levels 1-50 (about two years for a dedicated athlete, checked by a simulation test), rank titles, lifetime stats and 183 achievements in Bronze, Silver, Gold, Platinum and Legend tiers, including physique-check medals (up to "Stage Ready": a 100% goal match held for a year) and collection medals up to "Completionist" (every other medal).
- **Reminders**: a workout reminder on your training days at a time you pick (silent if you already trained, with the next program day in the text and a comeback message after a week off), and a streak saver that nudges in the evening only when today's workout decides your weekly goal. Turned on from a Home card or Settings, with a test notification; survives reboots and clock changes. Tapping a notification opens the app.
- **Health Connect sync** (optional, read-only): daily steps and sleep from your phone, watch or other apps fill the daily log when the app opens (30 days the first time, then the last week), or on "Sync now". Sleep you typed in is never replaced; steps you typed in are only replaced by a higher measured count. Imported values are labelled "Health Connect".
- **Optional Google sign-in** (Credential Manager): back up to and restore from your own Google Drive app folder, including straight from onboarding on a new phone. No Ironlog server; the app works fully signed out.
- Backup and restore as JSON, to a file or Google Drive (progress and physique photos are not included).

## Build

Requires JDK 17+ and Android SDK platform 35. Set `sdk.dir` in `local.properties`, then:

```
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
```

Install with `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

### Health Connect

Works on Android 14+ (built in) and on Android 9-13 with the Health Connect app installed; Settings offers the install. Publishing on Google Play additionally requires the Health Connect declaration in the Play Console (data types: steps, sleep; read only). The in-app privacy explanation (`HealthPermissionsActivity`) is the screen Health Connect links to.

The client library is pinned to `1.1.0-beta01`, the newest that builds with compileSdk 35 and AGP 8.7; `1.1.0` stable needs compileSdk 36 and a newer Android Gradle plugin.

### Enabling Google sign-in and Drive backup

Without these steps the app builds and works normally; the sign-in button explains that it is not set up.

1. In the [Google Cloud console](https://console.cloud.google.com/), create a project (or reuse one) and enable the **Google Drive API**.
2. Configure the **OAuth consent screen** (External, app name "Ironlog") and add the scope `https://www.googleapis.com/auth/drive.appdata`. While the app is in Testing mode, add your Google account as a test user.
3. Create an **OAuth client ID** of type **Android**: package `app.ironlog.personal` and the SHA-1 of your signing key (`./gradlew signingReport`, debug variant for debug builds).
4. Create an **OAuth client ID** of type **Web application**. Copy its client ID.
5. Add it to `local.properties` (not committed): `googleWebClientId=1234567890-abc.apps.googleusercontent.com`, then rebuild.

The Web client ID is used as the server client ID that Credential Manager requires; the Android client ID (matched by package and SHA-1) authorises the app. Drive access is requested only when you first back up or restore.

## Data and attribution

- Exercises and demo images: free-exercise-db by yuhonas (from wrkout/exercises.json), public domain under the Unlicense. Rebuild images with `tools/build_exercise_media.py`.
- Generic foods: USDA FoodData Central SR Legacy (public domain). Rebuild with `tools/build_food_subset.py`.
- Google sign-in: AndroidX Credential Manager with Sign in with Google; Drive access via Google Identity authorization (drive.appdata scope only).
- Pose detection and selfie segmentation: Google ML Kit (bundled models, runs offline).
- Packaged foods: Open Food Facts contributors, ODbL 1.0, fetched only when you search or scan.
- Ironlog is independent and not affiliated with STNDRD, its coaches or any fitness brand. It contains no STNDRD content.

See `docs/SEED_DATA.md`, `docs/STATE.md` and `BUILD_LOG.md` for details.

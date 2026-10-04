# Ironlog

Ironlog is a local-first Android training app (Kotlin, Jetpack Compose, Room) in the style of STNDRD: structured programs, a fast set logger, nutrition tracking, progress analytics, wellness tracking and a long-term achievement system. No account, no cloud. Everything except optional online food lookup stays on the phone.

## Status

- `./gradlew assembleDebug` builds `app/build/outputs/apk/debug/app-debug.apk` (about 70 MB: exercise demo images plus ML Kit's on-device pose model).
- `./gradlew testDebugUnitTest`: 98 tests pass, including a Robolectric + Roborazzi walkthrough that onboards, logs a workout, food, cardio, habits and measurements, runs a physique check, and screenshots every main screen into `app/build/screens/`.
- `./gradlew lintDebug`: 0 errors.
- Not verified: install and use on a physical device or emulator (none available in the build environment), live Open Food Facts requests from the app, the Google code scanner UI, notifications.

## Features

- **Onboarding**: six steps (you, body, goal, goal physique, training days, plan) that create a program and nutrition targets.
- **Train**: active program, program detail and builder (staple lifts, all 17 muscle groups trained each week), quick workouts by focus and time, mobility sessions, cardio entry, history.
- **Workout logger**: live timer with pause, last-time hints, warm-up / working / drop / rest-pause sets, insert drop or rest-pause sets mid-workout, swap exercise (with revert), reorder, notes, rest timer, summary with personal bests and shareable image cards.
- **Library**: 876 exercises, search and filters, favourites, custom exercises, start/end movement demos for 873 exercises, history, records and estimated 1RM trend.
- **Nutrition**: daily diary by meal, macro ring and bars, all 7,793 USDA SR Legacy foods offline, Open Food Facts search and barcode scanning, portions, recipes, copy day, weekly calorie balance, target presets.
- **Wellness**: morning readiness check-in with training advice, water, steps, sleep, habits with streaks, cardio log with timer, pace and calorie estimate, body measurements.
- **Physique check**: pick one of ten popular physique goals (Classic, Men's Physique, Bodybuilder, Athletic, Powerlifter, Lean; Bikini, Wellness, Figure, Athletic), then check a front photo. On-device pose detection and segmentation (ML Kit, bundled models, no network, no language model) measure shoulder, waist, hip and thigh widths; rule-based coaching compares the ratios with the goal and gives a match score, findings, muscles to prioritise and a calorie direction. The program builder can add extra volume for those muscles. Checks are saved as history.
- **Progress**: weight trend with 7-day average and goal, measurements, strength, weekly volume and sets per muscle, cardio, daily trends, progress photos.
- **Profile**: levels 1-50 (about two years for a dedicated athlete, checked by a simulation test), rank titles, lifetime stats and 183 achievements in Bronze, Silver, Gold, Platinum and Legend tiers, including physique-check medals (up to "Stage Ready": a 100% goal match held for a year) and collection medals up to "Completionist" (every other medal).
- Backup and restore as JSON (progress photos are not included).

## Build

Requires JDK 17+ and Android SDK platform 35. Set `sdk.dir` in `local.properties`, then:

```
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
```

Install with `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

## Data and attribution

- Exercises and demo images: free-exercise-db by yuhonas (from wrkout/exercises.json), public domain under the Unlicense. Rebuild images with `tools/build_exercise_media.py`.
- Generic foods: USDA FoodData Central SR Legacy (public domain). Rebuild with `tools/build_food_subset.py`.
- Pose detection and selfie segmentation: Google ML Kit (bundled models, runs offline).
- Packaged foods: Open Food Facts contributors, ODbL 1.0, fetched only when you search or scan.
- Ironlog is independent and not affiliated with STNDRD, its coaches or any fitness brand. It contains no STNDRD content.

See `docs/SEED_DATA.md`, `docs/STATE.md` and `BUILD_LOG.md` for details.

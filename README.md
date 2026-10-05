# Ironlog

Ironlog is a local-first Android training app (Kotlin, Jetpack Compose, Room) in the style of STNDRD: structured programs, a fast set logger, nutrition tracking, progress analytics, wellness tracking and a long-term achievement system. No account, no cloud. Everything except optional online food lookup stays on the phone.

## Status

- `./gradlew assembleDebug` builds `app/build/outputs/apk/debug/app-debug.apk` (about 43 MB, mostly exercise demo photos).
- `./gradlew testDebugUnitTest`: 143 tests pass, including a Robolectric + Roborazzi walkthrough that onboards, logs a workout, food, cardio, habits and measurements, runs a tape-measure physique check, and screenshots every main screen into `app/build/screens/`.
- `./gradlew lintDebug`: 0 errors.
- Not verified: install and use on a physical device or emulator (none available in the build environment), live Open Food Facts requests from the app, the Google code scanner UI, notification delivery on a real device (the alarm-to-notification path is tested under Robolectric).

## Features

- **Units**: kg or lb for every weight (lifts, body weight, goals, records, volume, medal texts), and cm or inches for lengths (body and physique measurements, height in feet and inches, cardio distance in miles with pace per mile). Both switch in Settings or during onboarding and default to imperial in the US, Liberia and Myanmar. Data is always stored in kg, cm and km, so switching never changes logged numbers; drop sets round to 2.5 kg or 5 lb plates.
- **App tour**: right after onboarding, an optional one-minute guided tour over the real screens (it switches tabs and spotlights the Start workout button, each tab and the profile). Skip at any step; replay from Settings › Help. Users restoring a backup are not shown it.
- **Onboarding**: six steps (you, body, goal, goal physique, training days, plan) that create a program and nutrition targets.
- **Train**: active program, program detail and builder (staple lifts, all 17 muscle groups trained each week), quick workouts by focus and time, mobility sessions, cardio entry, history.
- **Coach**: on every exercise, today's suggestion from your last sessions (double progression: hit the top of the rep range on every set and it adds 2.5 kg / 5 lb, or double for lower-body lifts and easy sets; in range keeps the weight and asks for a rep more; two sessions short of the range deloads to 90%), with one tap to use it. Optional effort (RPE 6-10) per set refines it.
- **Plates, warm-ups and supersets**: per-side plate calculator for kg or lb bars, generated warm-up ramps (bar, 40%, 60%, 80%), and supersets or giant sets where rest starts only after the last exercise of each round.
- **Lock-screen workout**: while a workout is running, a silent ongoing notification shows elapsed time or the rest countdown and the next set (exercise, set number, weight × reps, following superset rounds), with Done, +30 s and Skip rest buttons that work from the lock screen. Tapping it opens the workout.
- **Workout logger**: live timer with pause, last-time hints, warm-up / working / drop / rest-pause sets, insert drop or rest-pause sets mid-workout, swap exercise (with revert), reorder, notes, rest timer, summary with personal bests and shareable image cards.
- **Library**: 876 exercises, search and filters, favourites, custom exercises, start/end photo demos for 873 exercises, plus optional animated stick figures for 565 strength exercises (25 movement patterns), history, records and estimated 1RM trend.
- **Nutrition**: daily diary by meal, macro ring and bars, all 7,793 USDA SR Legacy foods offline, Open Food Facts search and barcode scanning, portions, recipes, copy day, weekly calorie balance, target presets.
- **Wellness**: morning readiness check-in with training advice, water, steps, sleep, habits with streaks, cardio log with timer, pace and calorie estimate, body measurements.
- **Physique check**: pick one of ten popular physique goals (Classic, Men's Physique, Bodybuilder, Athletic, Powerlifter, Lean; Bikini, Wellness, Figure, Athletic), then enter four tape measurements (shoulders, waist, hips, thigh) with a measuring guide. Rule-based coaching compares the circumference ratios (V-taper/Adonis index, waist-to-hip, shoulder-to-hip, leg size) and waist-to-height with the goal and gives a match score, findings, muscles to prioritise and a calorie direction, next to drawn You-vs-goal figures. The program builder can add extra volume for those muscles. Checks are saved as history and as body measurements.
- **Progress**: weight trend with 7-day average and goal, measurements, strength, weekly volume and sets per muscle, cardio, daily trends, progress photos.
- **Profile**: levels 1-50 (about two years for a dedicated athlete, checked by a simulation test), rank titles, lifetime stats and 183 achievements in Bronze, Silver, Gold, Platinum and Legend tiers, including physique-check medals (up to "Stage Ready": a 100% goal match held for a year) and collection medals up to "Completionist" (every other medal).
- **Reminders**: a workout reminder on your training days at a time you pick (silent if you already trained, with the next program day in the text and a comeback message after a week off), and a streak saver that nudges in the evening only when today's workout decides your weekly goal. Turned on from a Home card or Settings, with a test notification; survives reboots and clock changes. Tapping a notification opens the app.
- **Health Connect sync** (optional): steps and sleep in; weigh-ins both ways (scale readings fill days you didn't log, your weigh-ins are shared); workouts both ways (finished strength workouts and logged cardio go out as exercise sessions, runs/rides/walks/swims/sports/yoga from a watch or other apps come in as cardio with distance). Syncs when the app opens and on "Sync now"; each data type follows its own permission. Your own entries are never overwritten, nothing is imported twice, and deleting an imported entry keeps it from coming back. Imported entries are labelled "Health Connect".
- **Backup and new phone**: Android's Auto Backup copies a compressed snapshot of all data (about 1 MB) plus progress photos reduced to 1024 px (up to 15 MB) to the user's Google account. On a new phone with the same account, installing Ironlog shows "Welcome back" on the first screen and restores everything in one tap. Phone-to-phone transfer during setup copies everything at full size. Manually: "Export everything" makes one .zip (data + photos) that imports on any phone, from Settings or the first onboarding screen.

## Build

Requires JDK 17+ and Android SDK platform 35. Set `sdk.dir` in `local.properties`, then:

```
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
```

Install with `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

### Health Connect

Works on Android 14+ (built in) and on Android 9-13 with the Health Connect app installed; Settings offers the install. Publishing on Google Play additionally requires the Health Connect declaration in the Play Console (data types: steps, sleep, distance read; weight and exercise read and write). The in-app privacy explanation (`HealthPermissionsActivity`) is the screen Health Connect links to.

The client library is pinned to `1.1.0-beta01`, the newest that builds with compileSdk 35 and AGP 8.7; `1.1.0` stable needs compileSdk 36 and a newer Android Gradle plugin.

### Restoring on a new phone

Auto Backup needs the phone's Google backup switched on (Settings › Google › Backup, on by default on most phones) and runs about once a day while charging on Wi-Fi; Ironlog refreshes its snapshot whenever you leave the app after a change. Restore happens when Ironlog is installed from Google Play on a phone signed in to the same account (during setup or later). Sideloaded installs only get restores during device setup; use the .zip export there.

## Data and attribution

- Exercises and demo images: free-exercise-db by yuhonas (from wrkout/exercises.json), public domain under the Unlicense. Rebuild images with `tools/build_exercise_media.py`.
- Generic foods: USDA FoodData Central SR Legacy (public domain). Rebuild with `tools/build_food_subset.py`.
- Packaged foods: Open Food Facts contributors, ODbL 1.0, fetched only when you search or scan.
- Ironlog is independent and not affiliated with STNDRD, its coaches or any fitness brand. It contains no STNDRD content.

See `docs/SEED_DATA.md`, `docs/STATE.md` and `BUILD_LOG.md` for details.

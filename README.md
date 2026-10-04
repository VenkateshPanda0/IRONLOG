# Ironlog

Ironlog is a local-first Android fitness log project (Kotlin, Jetpack Compose, Room and DataStore). This is an **unverified, partial implementation**. Build, test, lint, sync, and device gates remain NOT RUN because Gradle tasks cannot establish a loopback connection in the current shell; see `BUILD_LOG.md` and `docs/COMPILE_RISKS.md`.

## Open in Android Studio

Open this folder as an existing Gradle project. The pinned versions are in `gradle/libs.versions.toml`; the project targets SDK 35, min SDK 26 and Java 17. The Gradle 8.9 wrapper scripts and JAR are checked in. JDK 17 and SDK platforms are visible under the user-profile SDK path, but build tasks fail before project configuration with `Unable to establish loopback connection`. Compile compatibility remains unknown, and no emulator/device is attached.

## Current implementation

- Five-tab Compose shell, dark/light palette, local profile onboarding and estimated calorie target.
- Room v1 models and repositories for profile, programs, exercises, sessions/sets, foods/meals, weight and goals.
- Bundled source metadata for 876 exercises and sourced USDA SR Legacy values for 1,200 foods and 2,271 portions. See `docs/SEED_DATA.md`; exercise images are not bundled.
- Local quick workout start, pause/finish, set completion, custom exercise and custom food entry, meal totals, and weight log/trend calculation.
- Three seeded program templates (10 workout days / 50 prescriptions), profile-based recommendation save/activation, prescription snapshots, and cycle advancement after a linked workout is finished.
- Onboarding stores estimated calorie/macronutrient targets in Room; Nutrition displays them and Settings can edit the profile fields currently collected plus targets.
- Unit conversion, meal scaling, volume, Epley e1RM and seven-day mean pure Kotlin calculations.
- Exercise, food and program seeding is authored but has not been verified at runtime.

## Build/install (not run)

With Android SDK 35, JDK 17 and network access for dependency resolution available:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
```

The expected debug artifact is `app/build/outputs/apk/debug/app-debug.apk`. Install to an attached device with `adb install -r app/build/outputs/apk/debug/app-debug.apk`, or transfer the APK and open it on-device. No APK has been produced or verified by this handoff.

## Data and privacy

Implemented flows persist on-device using Room. Open Food Facts text/barcode requests are the only implemented food network requests; USDA online lookup remains a disabled placeholder. Camera scanning, runtime notification permission, reboot timer recovery, and timer behavior are incomplete or unverified. Do not treat this as a production privacy audit.

- Exercise data: free-exercise-db by yuhonas, Unlicense; 876 metadata records, images excluded.
- Generic nutrition data: USDA FoodData Central SR Legacy; 1,200 sourced food records and 2,271 portion rows.
- Packaged-food source planned: Open Food Facts contributors, ODbL 1.0, https://world.openfoodfacts.org/terms-of-use.
- No packaged-food facts are bundled. Open Food Facts is queried only when the user requests it.

`tools/build_food_subset.py` processes a downloaded USDA SR Legacy JSON export and produces the food and portion assets. `tools/fetch_exercise_seed.ps1` fetches the exercise metadata source. Dataset refresh and app database seeding tests have not run. Backup/import is implemented in source but unverified.

## Known limitations and future work

The brief's full feature set is not complete: schema JSON still needs regeneration by Room KSP; full onboarding details such as birth date and training weekdays, calendar-based weekly schedule and missed-day handling, program builder/detail/duplication, workout summary/replace/revert, barcode scanning, recipes, strength/volume/nutrition charts, measurements/photos, reminders, advanced goal UI, engagement UI, AI coach and M10 extras remain incomplete. The source inventory is roughly 37% complete; no app build/test/lint/device gate is verified, and no APK exists. No CI is configured. See `docs/STATE.md`, `docs/COMPILE_RISKS.md`, and `BUILD_LOG.md`.

## License and attribution

Ironlog is independent and not affiliated with any fitness brand. Data source details and the image exclusion decision are documented in `docs/SEED_DATA.md`.

## Current source status update

The active workout draft now persists typed set edits, completion state and pause/resume timestamps. The rest-timer controller stores an absolute deadline, reschedules inexact alarms and posts one completion notification when notification permission is already granted. Permission prompting, reboot rescheduling, process-death/device verification and notification behavior have not been tested. The Open Food Facts provider performs explicit text/barcode lookup, filters incomplete macros and caches records in Room; no live request was made. USDA is a disabled placeholder, not a working provider.

The progress tab now plots actual recorded daily weights and supports date range filters (1M/3M/6M/1Y/ALL); it still has no strength, volume or nutrition chart. These tests were authored but remain NOT RUN.

Backup and import sources use JSON through Storage Access Framework document pickers. The backup includes v1 exercise/program/session/set/food/meal/profile/weight/goal records; import validates structure and references before one Room transaction. Body photos and future M10 entities are not included. A Robolectric round-trip/corrupt-input test source was added but NOT RUN.

The M7b domain slice now has deterministic XP/level replay, workout/food/photo/goal medal unlock calculations, weekly/food streak rules, and a quick-workout selector that honors target, equipment and avoid filters. Corresponding test source was authored. The M7b screens, event extraction from Room history, unlock settings, backfill, reminders, photos and share flow remain incomplete; no gate was run.

The recommender source now generates deterministic day-by-day prescriptions from locally seeded exercise metadata and onboarding inputs. Three built-in program templates seed 10 days and 50 prescriptions; loader validation checks exercise references and prescription bounds. The onboarding and Train program builder can save/activate custom recommendations. Experience, session length, and avoid-list are stored in the current Room v1 source schema, which has not been generated or compiled. Program prescriptions are copied into workout-session snapshots; completing a linked session advances the active program cycle transactionally. Program seed/recommender/progression tests are authored but NOT RUN. Calendar-based weekly rotation, missed-day handling, full profile editing and a full custom program builder remain incomplete. Current assumptions are recorded in `docs/STATE.md`.

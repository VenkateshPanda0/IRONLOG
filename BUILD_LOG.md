# Ironlog build log

## User-directed verification policy

The continuation brief authorizes Gradle, tests, and dataset downloads; current work is in Android Studio mode because CLI prerequisites are incomplete. All build/test/manual gates remain **NOT RUN**. Do not interpret authored tests as executed tests.

## Gate status

| Milestone | Required gate | Status |
| --- | --- | --- |
| M1 | `assembleDebug`, launch if device available | NOT RUN |
| M2 | `testDebugUnitTest`, calculator/idempotency/seed/backup tests, generated Room schema | NOT RUN |
| M3 | build and recommender matrix tests | NOT RUN |
| M4 | persistence/recovery/timer/PR tests and manual device checklist | NOT RUN |
| M5 | macro/snapshot/provider/cache tests and live-network check | NOT RUN |
| M6 | trend/ranges/volume tests | NOT RUN |
| M7 | backup round-trip/import validation tests | NOT RUN |
| M7b | XP/medal/generator/backfill tests | NOT RUN |
| M7c | goal and optional AI parsing/gating tests | NOT RUN |
| M8 | full tests, lint, instrumented tests, APK build/metadata/permissions | NOT RUN; no APK produced |
| M10 | gated post-M8 feature checks | NOT RUN; M8 was not verified |
| M9 | screenshot review/restyle | SKIPPED; no `reference/screens/` images supplied |

## Environment check and Phase 1 status (2026-10-03)

- `java -version`: Temurin 17.0.18 available.
- `ANDROID_HOME` and `ANDROID_SDK_ROOT`: `C:\Users\VENKATESH PANDA\AppData\Local\Android\Sdk`; the user-stated `C:\Android\Sdk` path does not exist. SDK platforms 33, 34, 36, and 36.1 plus build-tools are present under the configured profile SDK path.
- `sdkmanager --list_installed`: command unavailable; `sdkmanager` is not on PATH.
- `adb devices`: command ran; no attached devices.
- `gradle --version`: unavailable; system Gradle is not installed/on PATH.
- Network HEAD probes to `https://services.gradle.org`, `https://dl.google.com`, and `https://repo.maven.apache.org` each returned HTTP 200.
- Official Gradle 8.9 distribution was downloaded to a temporary folder; wrapper scripts and JAR are checked in. `gradlew.bat --version` passed and reported Gradle 8.9 / JDK 17.0.18.
- `gradlew.bat assembleDebug --no-daemon` failed before project configuration with `java.io.IOException: Unable to establish loopback connection`. No-daemon and IPv4 preference retries failed with the same error. No Android source was compiled; build/test/lint gates remain NOT RUN.
- Android Studio mode is active while the Gradle loopback issue is unresolved. Risks and compile checklist are in `docs/COMPILE_RISKS.md`.

## Source authoring notes

- Source-only M3 recommender increment: selects deterministic full-body, upper/lower, or PPL cycles; filters seed exercise options by equipment/avoid list; ranks compounds first; emits per-day set/rep/rest prescriptions; onboarding renders a generated preview. Recommender unit tests were authored, NOT RUN.
- M3 source follow-up: added 3 built-in templates with 10 days / 50 prescriptions, based on existing exercise IDs confirmed by a local JSON comparison. Loader validates references and prescription bounds, inserts/updates transactionally, and reuses built-in program IDs. Authored Room idempotency/count/reference/progression tests remain NOT RUN. Added experience/sessionMinutes/avoidList profile fields, versioned built-in seeding, onboarding save/activate, Train program preview/activation/day start, Home's active program/next-cycle day, and set/rep/rest snapshots in sessions. Finishing a linked program workout advances its cycle atomically; quick workouts and repeat finish calls do not. Calendar weekly schedule, missed/skip-day support, and complete onboarding/profile editing remain incomplete. Room changes are NOT compiled and schema JSON is absent.
- Current change batch: source and tests only. No Gradle build, unit test, lint, sync, emulator, or device command was run. All milestone gates in the table remain NOT RUN.
- Source asset audit (PowerShell JSON parse): 876 exercise IDs; 3 programs; 10 program days; 50 prescriptions; 0 missing exercise references. This checks the bundled JSON relationship only; it is not an app test or Room seeding verification.
- `ktfmt 0.64 --kotlinlang-style --dry-run --set-exit-if-changed` completed successfully for the Kotlin files in this change. `git diff --cached --check` reported no whitespace errors. Neither check compiles or executes the app.
- Latest M3 source slice: added a GoalRepository and Room goal read/write, saves calculated macro targets with onboarding profile, shows targets on Nutrition, and lets Settings edit available profile fields and manual macro targets. Added a Room repository persistence test source. This slice has not been compiled or tested; all gates remain NOT RUN.
- Shared `Page` now scrolls by default for long forms and screens; pages containing their own lazy lists opt out to avoid nested scrolling constraints. Kotlin formatter dry-run and whitespace checks were repeated after these layout edits; the app was not compiled.
- Recommender assumptions: 2–3 days Full Body, 4 Upper/Lower, 5 PPL with a repeating three-session cycle, 6 two PPL cycles; coarse count `floor(sessionMinutes/9)` clamped 3–8; fixed muscle priority and exercise-ID tie-break; exact normalized equipment match; 3 beginner sets and 4 intermediate/advanced sets. See `docs/STATE.md` for details.

- Pinned AGP 8.7.3 / Gradle 8.9 / Kotlin 2.0.21 / KSP 2.0.21-1.0.28 / Compose BOM 2024.12.01 / Room 2.6.1 / compileSdk 35 / minSdk 26 / targetSdk 35.
- Bundled 876 free-exercise-db metadata records (1,005,327 bytes); exercise images are excluded because their redistribution terms are not established here.
- Downloaded USDA SR Legacy April 2018 data and generated 1,200 complete-macro food rows plus 2,271 portions. The source archive was not committed; selected CSV assets are in the project and documented in `docs/SEED_DATA.md`.
- Transactional exercise/food loaders and DataStore seed-version gating are authored. Runtime inserts are unverified.
- Food CSV parser fixtures cover quoted values, malformed rows, absent optional fiber, required macro rejection, and invalid portion data. Robolectric Room tests assert the exercise loader yields 876 records and the USDA loader yields 1,200 foods; each checks idempotent second-load behavior. Tests are authored, NOT RUN.
- Formatted all Kotlin main/test sources with temporary ktfmt 0.64. The formatter parsed all files after the Nutrition-screen syntax fix; this is not a Kotlin/Android compile and does not change NOT RUN gates.
- Removed the placeholder Room schema (`identityHash: schema-not-generated`). KSP must generate a valid v1 schema before a verified release; no schema file currently exists.
- Secrets are not present. No USDA API key or OFF contact address exists in this source tree. USDA lookup is not implemented; the displayed OFF contact is the generic `unset@example.invalid` placeholder.
- No reference screenshots were found in the supplied project files; M9 was not performed.

## Git publishing

- The project is published to `origin/main`; the branch head can advance during this continuation.
- The `main` tree contains 45 tracked files, including the application source, five unit-test source files, Gradle configuration, `README.md`, `docs/BRIEF.md`, `docs/STATE.md`, scripts, and `LICENSE`.
- The `master` application commit (`70696c8`) and `codex/ironlog-progress` history (`0143efd` plus its ancestors) are reachable from `main`. All six `*-not-run` milestone tags remain present.
- The push of `main` succeeded as a normal fast-forward. `git ls-remote --heads origin` reports only `main`; pruning removed stale `origin/master` and `origin/codex/ironlog-progress` tracking refs. The delete commands responded that those remote refs did not exist, consistent with the server listing.
- The working tree was clean at the time of remote verification. No build or test gates were run; see the gate table above.

## Assumptions and deviations

- The continuation brief supersedes earlier no-build/no-download instructions. Environment checks were run; a wrapper distribution download attempt failed before project dependencies or datasets were downloaded.
- No seed dataset was downloaded in this phase; requested dataset counts are not met or claimed.
- No project existed in the local workspace. This is a new single-module Android project.
- User data schema starts at Room version 1. The complete later migration set and pre-migration backup recovery are not authored yet.
- Any unimplemented requirements are listed in `README.md` and `docs/STATE.md`; no pass is claimed.

- M7 backup implementation added as source: serializable Room rows, SAF JSON export/import, structural checks and transactional restore. Round-trip test authored but NOT RUN.

- M7b: XP/level replay, medals, food/training streaks and quick-workout generator source plus tests authored. Full engagement UI/persistence and all gates remain NOT RUN.

## 2026-10-04 · First verified build (Linux cloud session)

Environment: OpenJDK 21.0.11, Android SDK installed via cmdline-tools at `/opt/android-sdk` (platform 35, build-tools 35.0.0/34.0.0), Gradle 8.9 wrapper. The earlier Windows loopback blocker does not occur here.

- `./gradlew assembleDebug` — **PASS**. Output `app/build/outputs/apk/debug/app-debug.apk` (~11 MB). Room KSP generated `app/schemas/.../1.json`.
- `./gradlew testDebugUnitTest --rerun-tasks` — **PASS**, 33 tests, 0 failures, 0 skipped (incl. Robolectric backup/seed tests).
- Not run: `lintDebug`, device/emulator install, live network food lookup.

Fixes needed to get there:
- Pinned Java/Kotlin JVM target 17 (KSP vs javac target mismatch under JDK 21).
- Compile errors: missing `private val` on `WorkoutViewModel.repository`, missing `Column` import, invalid `weight` imports, smart-cast on delegated state in Home/Train, missing `WeekdaySelector` composable (now added; onboarding persists selected weekdays).
- Tests: `e1rm` now returns the lifted weight for a 1-rep set (code bug); corrected three wrong test expectations (servings fixture count, 0.4 kg/week trend rate, Recommender fixture lacked a lower-body exercise; nullable `bmi` assertion).

## 2026-10-04 · Steps 1-7 (STNDRD-style feature parity)

Each step was built, tested and pushed to `main` separately: redesign and navigation, library, workout logger, exercise demos, progress, nutrition, profile and achievements, two-year grind, wellness.

- Final gate: `./gradlew testDebugUnitTest assembleDebug lintDebug`: 89 tests pass, APK about 40.7 MB, lint 0 errors (warnings are mostly newer dependency versions available).
- Real bugs found and fixed along the way: programs never selected back exercises ("back" vs "lats/middle back"), Settings import button never opened the picker, deleting personal data wiped the bundled library, 1-rep e1RM, the food subset lacked staples (oats, eggs, rice, milk), food search ranking and main-thread scoring, Open Food Facts free-text search endpoint, quadratic PR recounting, navigation off the main thread, set hints misaligned after inserting drop sets, unused CAMERA permission, old Fragment version for activity results.
- Decisions: exercise demos are the public-domain start/end photos animated as a loop (no freely licensed video set exists); levels capped at 50 with a curve tuned by simulation.

## 2026-10-04 · Physique check

- Rule-based analysis (`domain/Physique.kt`): frontal widths from a person mask and pose joints, photo-quality checks (no body, not full body, side-on, tilted, arms touching), ten goal physique types with target ratios, findings, priority muscles and calorie direction. Unit-tested on synthetic bodies with known widths.
- On-device detection with ML Kit pose-detection-accurate and segmentation-selfie (bundled models). Gallery photos are decoded upright from EXIF at about 1024 px.
- Schema v5, onboarding goal-physique step, Physique screen with You-vs-goal figures (original vector drawings), history, entry cards on Home, Progress > Photos and Profile, and a "physique focus" switch in the program builder.
- APK grew to about 70 MB (ML Kit native library); 32-bit x86 dropped and native libraries compressed (was 130 MB before that).
- Gate: 96 tests pass, lint 0 errors. Not verified: ML Kit on a real device and accuracy on real photos.


## 2026-10-04 · Physique and collection medals

- 23 new achievements (183 total): physique checks in different months (up to 48 months, Legend), best goal match, V-taper ladder up to 1.9 (Legend), match improvement over the first check, "Stage Ready" (100% match held for a year, Legend), and collection medals up to "Completionist" (every other medal, Legend).
- Grind simulation unchanged: dedicated cap at week 105, regular week 152. Gate: 98 tests pass, lint 0 errors.

## 2026-10-05 · Google sign-in and Drive backup

- Optional sign-in with Credential Manager (Sign in with Google), account shown in Settings, sign-in button on the onboarding welcome step that fills the name and offers "Restore my data from Google Drive".
- Drive backup/restore of the JSON backup to the hidden appDataFolder (scope drive.appdata), via REST with a testable HTTP seam; one file, created once then updated.
- Backups now include cardio, daily logs, habits, habit checks, measurements and physique checks; old backups still import.
- Web client ID comes from `googleWebClientId` in local.properties or a Gradle property; when absent, the button explains how to enable it.
- Fixed during testing: sign-out waited on Credential Manager forever without Play services; the local account is now cleared first and the call is time-limited.
- Not verified: real Google sign-in and Drive calls (no OAuth client or device here).

## 2026-10-05 · Stick-figure exercise animations

- 25 movement patterns defined as start/end side-view poses (segment angles), built by forward kinematics and pinned at the feet, hands or hips so the anchor stays still; smooth ease-out-and-back loop.
- Exercises map to patterns by name (565 of 657 strength exercises, all staples except sideways leg moves, neck work and the wrist roller, which keep photo demos). Equipment decides what the hands hold.
- Demo card defaults to Motion with a Photos switch; tap pauses. Uses an infinite transition so Compose tests treat it as ambient (tests hold it at the start pose; the photo loop is still checked).
- Visual review via `MotionGalleryTest` (build/screens/18_motion_gallery.png); fixed fly hands passing through the floor and the hip-thrust bar position.
- Gate: 109 tests pass, lint 0 errors, APK about 70 MB.

## 2026-10-05 · Workout reminders and notifications

- Workout reminder on training days at a chosen time (15-minute steps), quiet on rest days, after a workout or during one; names the next program day; comeback wording after 7+ days off.
- Streak saver: evening nudge only when the sessions still needed this week equal the days left, so today decides the weekly goal.
- One inexact, Doze-safe daily alarm per reminder (no exact-alarm permission); each firing books the next day first. Re-booked on app start, BOOT_COMPLETED, MY_PACKAGE_REPLACED, TIME_SET and TIMEZONE_CHANGED (exported receiver checks the action).
- Found and fixed: the app never requested POST_NOTIFICATIONS, so notifications (including the rest timer) were blocked on Android 13+. Notifications now open the app when tapped. Settings nutrition targets showed raw decimals (75.83333333333333); now rounded.
- Tests: rule tests, Robolectric alarm/receiver/notification tests (training day, rest day, blocked permission, reboot, spoofed intent). Gate: 117 tests pass, lint 0 errors.

## 2026-10-05 · Health Connect sync for steps and sleep

- Read-only READ_STEPS and READ_SLEEP. Steps use Health Connect's daily aggregate (deduplicates phone + watch); sleep sums sessions per wake-up date minus awake/out-of-bed stages, ignoring blips under 15 minutes.
- Schema v6 marks imported values. Merge: empty days filled; imported values refreshed; manual sleep never replaced; manual steps only replaced by a higher count; manual edits take a value back.
- Sync on app resume (throttled to 15 min, foreground only as Health Connect requires) and "Sync now"; 30 days first time, then 7. Settings handles unsupported phones, install/update, permission request, revoked access and links to Health Connect's own access screen; required rationale activity + Android 14 permission-usage alias declared.
- Pinned connect-client 1.1.0-beta01: 1.1.0 stable requires compileSdk 36, which needs a newer AGP than 8.7.3.
- Tests: merge/sleep rules, Room sync with a fake source (first vs later window, manual entries kept, throttle), v5->v6 migration, walkthrough shows imported steps tagged and manual sleep kept. Gate: 124 tests pass, lint 0 errors, APK about 72.5 MB.
- Not verified: real Health Connect reads and permission screens on a device.

## 2026-10-05 · Health Connect: weight and workouts

- Weight both ways: other apps' readings fill days without an Ironlog weigh-in (latest per day, corrections refresh the import); Ironlog weigh-ins are written with client IDs `ironlog-weight-<id>` so re-sending updates instead of duplicating.
- Workouts both ways: finished strength workouts (`ironlog-workout-<id>`, STRENGTH_TRAINING) and hand-logged cardio (`ironlog-cardio-<id>`, mapped exercise type) are written; other apps' cardio-type sessions of 5+ minutes are imported with distance when READ_DISTANCE is granted. Other apps' strength sessions are skipped (no sets to show).
- Ironlog's own records are filtered out on read by data origin. Deletions: deleting an Ironlog record queues its Health Connect deletion for the next sync; deleting an import tombstones its ID (a weigh-in day with a deleted import stays empty - found by a test).
- Schema v7 adds `healthId` to body_weight and cardio_session. Each part of a sync runs only with its permission; Settings shows per-type access (in / out / in only / not allowed) with "Allow more data types".
- Gate: 127 tests pass, lint 0 errors. Not verified on a device with real Health Connect.

## 2026-10-05 · Review cleanup: demos, dead code, physique by tape

- Exercise demos default to the real photos again; the stick figure is one tap away (default only when an exercise has no photos).
- Removed the online USDA FoodData Central provider (could never be enabled; all SR Legacy foods are bundled) and the placeholder "unset@example.invalid" contact; Open Food Facts requests identify the app by its project URL.
- Physique check now uses tape-measure circumferences (shoulders, waist, hips, thigh; schema v8 adds shouldersCm) instead of widths estimated from a photo with ML Kit. Targets retuned for circumferences; waist-to-height now comes from the measured waist and profile height. Score falls to 0 five tolerances from target (was three) so progress shows. Saving a check also logs a body measurement. ML Kit removed: APK 72.5 MB -> 43.4 MB. Old photo-based checks remain in history marked "photo estimate".
- Gate: 126 tests pass, lint 0 errors.

## 2026-10-05 · New-phone restore replaces Google sign-in

- Removed Google sign-in and Drive backup: they could not work for anyone without a developer-configured Google Cloud OAuth client, and duplicated the file export.
- Android Auto Backup on: cloud backup holds only files/backup (snapshot.json.gz, about 1 MB with all seed data, and progress photos at 1024 px within a 15 MB budget: the first photo always, then newest first), well inside the 25 MB limit; device-to-device transfer copies database, files and settings at full size.
- The snapshot is rewritten when the app goes to the background and Room's invalidation tracker saw a change; never while no profile exists, so a fresh install cannot overwrite the restored snapshot. "Delete personal data" removes it.
- Onboarding's first screen offers "Welcome back, <name>" with what the snapshot holds, and "Restore from a backup file". Export is now one .zip (JSON + photos); import accepts .zip and older .json. Photo names in archives are restricted to plain file names (zip-slip safe); photo rows without an image are skipped.
- Backups now include progress photo rows.
- Tests: zip round trip with photos, hostile zip entries, legacy JSON, snapshot restore with reduced photos, change detection, and an onboarding UI test of the welcome-back restore. Gate: 129 tests pass, lint 0 errors, APK 43.4 MB.
- Not verified: an actual Google Auto Backup / restore cycle on devices (can be exercised with `adb shell bmgr backupnow app.ironlog.personal` and `adb shell bmgr restore`).

## 2026-10-05 · kg / lb units

- `WeightUnit` converts only at display and input; the database, backups and Health Connect stay in kg. Setting in Settings › Units and on the onboarding body step (switching converts the typed weight); default lb for US, LR, MM locales.
- Converted: set logger (column header, last-time and hints, input), exercise stats and e1RM chart, workout summary/share card, history, Progress weight/strength/volume, Home weight card, profile lifetime volume, achievement progress and descriptions ("Bench press 150 kg" -> "331 lb"), goal weight, profile weight.
- Drift guard: a 100 kg set shows as 220.5 lb; fields compare the displayed text, so viewing never rewrites 100 kg as 100.02 kg (set rows, profile weight, goal weight).
- Drop sets round to the unit's plate step (2.5 kg or 5 lb).
- Fixed a timing flake in HealthTwoWayTest (a workout started and finished in the same millisecond is correctly skipped as zero-length).
- Tests: conversion/format/parse/localize/defaults/plate rounding; walkthrough switches kg on onboarding, then lb at the end and checks the stored kg values are unchanged. Gate: 132 tests pass, lint 0 errors.

## 2026-10-05 · Inches, feet and miles

- `LengthUnit` (cm or in) converts at display and input only; storage stays cm and km. In inches mode: body and physique measurements in inches, height as feet and inches (accepts 5'11", 5' 11, 5 11, 5ft 11in, 71 or 5.9), cardio distance in miles and pace per mile, and cardio medal texts ("Run 5 km or more at under 5:00 /km" -> "Run 3.1 mi or more at under 8:03 /mi").
- Settings › Units has Weight and Lengths toggles; onboarding has a cm/ft toggle next to height that converts what was typed. Defaults imperial for US, LR, MM.
- Drift guards as for weights: Settings height and prefilled physique fields keep stored cm when left unchanged.
- Found by the tests: "71" was parsed as 7'1"; feet and inches now need a mark or a space between them, and plain numbers are inches (or feet below 9).
- Gate: 134 tests pass, lint 0 errors. The walkthrough types 5'11, switches to cm (180.3), and at the end checks stored cm/km are unchanged after viewing in inches and miles.

## 2026-10-05 · Coach, plates, warm-ups, supersets

- Coach (`domain/Coaching.kt`): double progression from earlier sessions' working sets. All sets at the top of the range -> +1 plate step (2.5 kg / 5 lb; doubled for lower-body compounds, and doubled again when average RPE <= 7). In range -> same weight, weakest set + 1 rep. Below the range two sessions running -> deload to 90%. Bodyweight work progresses by reps. Shown on each exercise with "Use" (fills open working sets) and "Plates".
- RPE 6-10 per set from the set menu (existing set_log.rpe column), shown as "@8" and carried into history (LoggedSet.rpe).
- Plate calculator (kg plates 25-1.25, lb plates 45-2.5; 20/15/10 kg or 45/35/25 lb bars) and warm-up generation (bar x10, 40% x5, 60% x3, 80% x1, plate-rounded; dumbbell/machine 50% x8, 75% x3) inserted before the first set.
- Supersets: schema v9 session_exercise.supersetGroup; "Superset with next" (extends into giant sets) and "Leave superset"; cards show "SUPERSET A · then X"; rest starts only after the last exercise of a round.
- Fixed a long-standing walkthrough mistake: closing the demo tapped "Show demo" on the next card instead of "Hide demo".
- Gate: 141 tests pass, lint 0 errors.

## 2026-10-05 · Lock-screen workout controls

- `WorkoutNotifier` observes the active session, its exercises and sets, the rest timer (new `restTimerFlow`) and the weight unit, debounced 150 ms, and posts an ongoing, silent, public-visibility notification: elapsed time (pauses excluded) or a rest countdown chronometer, the next set with the logger's hints, and progress.
- Buttons: Done (completes the next set with the entered or last-time values, then rests unless mid-way through a superset round), +30 s and Skip rest during rest. Next-set order follows superset rounds (round robin within the group).
- Tapping opens the workout directly (MainActivity singleTop + `openWorkoutRequest`). Permission is asked once when the first workout starts. Finishing or discarding removes the notification because the active session disappears.
- The rest-end alarm now clears the finished timer row so the countdown disappears.
- Tests: next-set and superset ordering, and the full notification path (content, Done logs 60 kg x 8 from history and starts the rest, buttons, finish removes it). Gate: 143 tests pass, lint 0 errors.
- Not verified on a device: lock-screen appearance depends on the phone's lock-screen notification settings.


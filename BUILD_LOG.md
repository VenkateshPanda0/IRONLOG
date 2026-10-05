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


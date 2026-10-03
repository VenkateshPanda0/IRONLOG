# AUTONOMOUS MODE (read first)

I will paste this brief once and not supervise. Work through it end to end without asking for review between milestones.

1. **First action:** save this entire brief verbatim to `docs/BRIEF.md` in the project root. Create `docs/STATE.md` and keep it current: last passing milestone/tag, what is in progress, what failed, and the exact next step. If your session or context is running low, update `docs/STATE.md` and `BUILD_LOG.md` BEFORE stopping, so that if I later send only "continue" you can read `docs/BRIEF.md` and `docs/STATE.md` and resume exactly where you left off.
2. **Order:** M1 -> M2 -> M3 -> M4 -> M5 -> M6 -> M7 -> M7b -> M7c -> M8 (produces `Ironlog-core-debug.apk`) -> M10 (extras, one at a time) -> M9 only if `reference/screens/` has images. Do not stop between milestones except under rule 4.
3. **Missing keys are not blockers.** If `local.properties` lacks `FDC_API_KEY` or `OFF_CONTACT`, build anyway: disable the USDA provider, use a generic contact placeholder `unset@example.invalid` for the Open Food Facts User-Agent, show the state in Settings, and note it in `BUILD_LOG.md`. Never invent keys.
4. **Stop only if** (a) the Android SDK or JDK is missing and you cannot install them (try `sdkmanager`/command-line tools if the network allows; if not, stop and tell me exactly what to install), or (b) the project cannot build after the retry limit in section 0 even after rolling back to the last passing tag. In both cases write the exact blocker and the next step into `docs/STATE.md` and `BUILD_LOG.md`.
5. **Final report** (when done or stopped): what passed (with real command output summaries), what was rolled back, what was NOT RUN (device tests, live-network tests), the APK paths, and the install commands. Never claim anything passed that you did not run.

---

# CODEX BRIEF: "Ironlog" — local-first personal Android fitness app

You are building a native Android app from scratch. This brief is self-contained. Read all of it before writing code.

## 0. Rules of engagement (non-negotiable)

1. Inspect the workspace first. If no project exists, create one in the current directory.
2. Detect the environment before choosing versions: `java -version`, `echo $ANDROID_HOME $ANDROID_SDK_ROOT`, `sdkmanager --list_installed`, `./gradlew --version` (after wrapper exists), `adb devices`. Pick AGP, Gradle, Kotlin, KSP, Compose BOM and Room versions that are mutually compatible with the installed JDK and SDK, and **pin them** in `gradle/libs.versions.toml`. If the SDK or JDK is missing and cannot be installed, STOP and report exactly what is missing. Do not fake a build.
3. Work in the milestones in section 9, in order. After each milestone: run the stated build/test commands, fix failures, and append to `BUILD_LOG.md` what you ran and the real result.
4. Never claim a build, test, or device install passed unless you executed it and saw it pass. Never claim device installation was verified unless `adb install` ran against a real device or emulator.
5. Implement real behaviour end to end (UI -> ViewModel -> repository -> Room). A screen that renders mock or hardcoded data does not count as done.
6. No fabricated data: no invented nutrition values, no fake personal records, no placeholder charts with fake points. Empty states must be real empty states.
7. Keep dependencies minimal. No Hilt/Dagger (use a simple manual `AppContainer`). No network libraries. No analytics, crash reporters, or ads.
8. Do not copy or imitate any proprietary content from other fitness apps (names, programs, videos, images, icons). This app has its own name and design.
9. Use git. `git init` if needed, add a `.gitignore` (build outputs, `local.properties`, keystores). Commit after every milestone that passes its gate and tag it (`m1-pass`, `m2-pass`, ...). If a milestone still fails its gate after 3 genuine fix attempts, `git reset --hard` to the last passing tag, ship what passes, and report the failure honestly in `BUILD_LOG.md`. A smaller working app beats a larger broken one; the priority order is: it builds, it installs, workout logging works, everything else.
10. If something in this brief is impossible or contradictory, choose the simplest reasonable option, record it under "Decisions and deviations" in `BUILD_LOG.md`, and continue. Stop and ask only if the decision would change the data model or the permissions.

## 1. Goal and non-goals

**Goal:** an APK I can sideload on my own Android phone with no account that asks me for my details at first launch, suggests a program and targets from them, and logs workouts, meals/macros (with automatic food lookup by name or barcode when online), and body weight, with charts and history, and surviving app kills mid-workout.

**Non-goals (do NOT build):** accounts/login, subscriptions, cloud sync, community feed, leaderboards, friends, challenges, social sharing, Play Store release, wearable integrations, generative-AI workouts (workout and nutrition suggestions are deterministic rules, section 8a and 8d; the only generative AI is the optional photo/progress commentary in 8d-C). Online food lookup, barcode scanning and the engagement layer (section 8c) ARE in scope. Real multi-user community, leaderboards against other people, and any proprietary programs/videos are NOT.

## 2. Identity and build config

- App name: **Ironlog**. Package/applicationId: `app.ironlog.personal`.
- minSdk 26. compileSdk/targetSdk: the highest stable SDK platform installed (expected 35 or 36). Record the choice.
- Single `:app` module, Kotlin, Jetpack Compose, Material 3, Room (KSP), DataStore Preferences, coroutines/Flow, kotlinx-serialization-json (for export/import and seed parsing). Navigation: Navigation Compose.
- Permissions allowed: `INTERNET` and `ACCESS_NETWORK_STATE` (used ONLY by online food lookup, section 8b), `CAMERA` (barcode scanner and progress photos; request at point of use, app works if denied), `RECEIVE_BOOT_COMPLETED` (reschedule reminders), `USE_BIOMETRIC` (app lock, M10), optional Health Connect read permissions for weight and steps (M10 item 14 only), `POST_NOTIFICATIONS` (rest timer), `VIBRATE`. **No storage permissions.** Use the Storage Access Framework (`ActivityResultContracts.CreateDocument` / `OpenDocument`) for export/import. Everything except online food lookup must work with no network. Do not send any personal data (profile, weights, workouts) over the network; only the food search text or barcode goes out.
- Original adaptive launcher icon (simple vector: a stylised plate/dumbbell on a dark background). No third-party artwork.
- Theme: dark by default, light and system optional in Settings. Material 3 with a custom color scheme (near-black surfaces, one saturated accent). Typography: system default with a clear hierarchy (large numeric displays for weight/reps/timer).

## 3. Package structure

```
app.ironlog.personal
  data/db        (entities, DAOs, Database, migrations, converters)
  data/repo      (WorkoutRepository, ProgramRepository, ExerciseRepository, NutritionRepository, BodyRepository, SettingsRepository, BackupRepository)
  data/seed      (SeedLoader, asset parsers, version tracking)
  domain         (pure Kotlin: calculations, unit conversion, models)
  timer          (RestTimerController, RestTimerReceiver, notification helper)
  ui/theme
  ui/nav         (routes, bottom bar)
  ui/home | ui/workouts | ui/workout/active | ui/exercises | ui/nutrition | ui/progress | ui/settings
  AppContainer.kt, MainActivity.kt, IronlogApp.kt
```

## 4. Navigation and screens

Bottom navigation, 5 tabs: **Home, Train, Nutrition, Progress, Settings**.

| Screen | Purpose / contents |
|---|---|
| Home | Today's scheduled workout card (Start / Resume / Rest day), this-week strip (7 days with done/missed/rest markers), streak and workouts-this-week, today's calories and macros vs target (rings or bars), latest weight and 7-day trend delta. "Resume workout" banner if an unfinished session exists. |
| Train | Active program card (name, current week/day, Change program), program list (3 built-in templates + user-created), "Quick workout" (empty session), workout history entry point, exercise library entry point. |
| Program detail | Description, weekly schedule by day, per-day workout preview (exercises x sets x rep range), Activate / Deactivate. |
| Workout preview | Exercises with prescriptions; Start. |
| Active workout | Top bar: elapsed timer, Pause, Finish. List of exercises; per exercise: name, target, previous-session sets for reference, set rows (type chip warm-up/working/drop, weight, reps, done checkbox), add set, replace exercise, skip exercise, reorder, notes. Bottom: rest timer bar when running (remaining time, +15s, -15s, skip). Exercise detail sheet (instructions, muscles, equipment, image if bundled). |
| Replace exercise sheet | Search + filters (muscle, equipment); suggestions ranked by same primary muscle + equipment match; "Revert to original". |
| Workout summary | Duration, total volume, sets completed, PRs hit, per-exercise recap, notes, Save/Discard. |
| History | List grouped by month; detail view of a past session (read-only snapshot, editable notes). |
| Exercise library | Search, filter by muscle/equipment/category, favourites, custom exercise create/edit/delete, exercise detail with history and best sets. |
| Nutrition | Date selector, daily totals vs targets, meals (Breakfast/Lunch/Dinner/Snacks) with entries, add food, copy previous day's meal, edit/delete entries. |
| Food search / add | Search local foods (name, brand), recent and favourites tabs, serving picker (grams default plus food-specific servings), live macro preview, save entry. Create custom food and simple recipe (ingredients -> per-serving macros). |
| Progress | Segments: Weight, Strength, Volume, Nutrition. Weight: line chart with raw points plus 7-day moving average, ranges 1M/3M/6M/1Y/All, goal line, add entry. Strength: pick exercise, e1RM and top-set chart, PR list. Volume: weekly volume bars. Nutrition: 7/30-day average calories and macro bars. |
| Measurements and photos (P2) | Body measurements log; progress photos stored in app-private storage via the system photo picker (no camera permission). |
| Profile and medals | Level, XP bar, medals earned/locked, lifetime Stats screen (section 8c). |
| Quick workouts | Generate by muscle/time/equipment, favourites, fixed quick sessions. |
| Goals and check-in | Goal cards (weight, lifts, measurements) with percent/rate/projection/status, adherence block, what-to-do-next cards (section 8d). |
| AI coach (optional) | Settings-gated provider choice (None / Gemini free text-only / Paid with photos); confirm-then-send flow; JSON-rendered observations/suggestions/caveats; delete result. |
| Weekly review and tools | Weekly review, plate calculator, backup settings (section 8e). |
| Photos and measurements | Photo gallery, side-by-side compare, measurement charts. |
| Onboarding | First-launch flow per section 8a, ending in a summary of computed targets and a recommended program. |
| Barcode scanner | Camera preview, torch, manual entry, result goes to food detail (section 8b). |
| Settings | Profile (edit onboarding answers), units (kg/lb, cm/in), theme, nutrition targets (calories, protein/carb/fat grams or percentages), rest timer default and per-exercise override, week start day, notification toggle, export, import, about/attributions, delete all data (double confirm). |

States: every list/screen needs loading, empty, and error states. Destructive actions need confirmation. Support edge-to-edge and system back.

## 5. Data model (Room)

All timestamps are epoch millis UTC. Calendar dates the user logs against (meals, weight, scheduled days) are stored as ISO `LocalDate` strings (`yyyy-MM-dd`) in the user's local date at the time of logging, so timezone changes do not move historical entries. Weights stored in **kg**, lengths in **cm**, energy in **kcal**, nutrients in **grams**; convert only at the UI edge.

Entities (PK = `id` Long autoGenerate unless noted):

- `exercise(id TEXT PK, name, category, force?, level?, mechanic?, equipment?, primaryMuscles(json), secondaryMuscles(json), instructions(json), imagePaths(json), isCustom, isFavorite, createdAt)` — seed ids come from the dataset; custom ids are `custom_<uuid>`. Index on name, equipment.
- `program(id, name, description, daysPerWeek, isBuiltIn, createdAt)`
- `program_day(id, programId FK, weekIndex, dayIndex, name, isRest)` — weekIndex supports multi-week cycles (v1 templates use 1 week that repeats). Unique (programId, weekIndex, dayIndex).
- `program_day_exercise(id, programDayId FK, exerciseId FK, orderIndex, targetSets, repMin, repMax, restSeconds, notes, setTechnique?, tempo?)` where setTechnique in {NONE, DROP, REST_PAUSE}.
- `active_program(id=1, programId FK, startDate, currentWeek, currentDay, status)` — one row.
- `workout_session(id, startedAt, endedAt?, status {IN_PROGRESS, PAUSED, COMPLETED, DISCARDED}, programId?, programDayName?, name, notes, totalPausedMs, lastActiveAt)`
- `session_exercise(id, sessionId FK, exerciseId, exerciseNameSnapshot, orderIndex, status {PENDING, DONE, SKIPPED}, replacedFromExerciseId?, targetSets, repMin, repMax, restSeconds, notes)` — **snapshots** name and prescription at session start. This is how history stays unchanged when templates or exercises are later edited.
- `set_log(id, sessionExerciseId FK, setIndex, type {WARMUP, WORKING, DROP}, weightKg?, reps?, rpe?, isCompleted, completedAt?)` Index (sessionExerciseId, setIndex).
- `rest_timer(id=1, sessionId?, endAtEpochMs, durationSec, isRunning)` — single row for restart recovery.
- `food(id, name, brand?, source {USDA, CUSTOM, IMPORT, OFF, USDA_ONLINE}, sourceRef?, kcalPer100g, proteinPer100g, carbsPer100g, fatPer100g, fiberPer100g?, isFavorite, createdAt, confidence {HIGH, MEDIUM, USER})` Index name. Nullable macros are NOT allowed for kcal/protein/carbs/fat; foods lacking them are excluded at seed time.
- `food_serving(id, foodId FK, label, grams)` e.g. "1 cup", "1 egg". Every food also has an implicit 100 g / 1 g serving.
- `recipe(id, name, servings)` and `recipe_ingredient(id, recipeId FK, foodId FK, grams)`. Saving a recipe also creates a `food` row (source CUSTOM) with macros computed per 100 g of cooked total weight, or per-serving via a `food_serving`.
- `meal_entry(id, date, mealType {BREAKFAST, LUNCH, DINNER, SNACK}, foodId FK?, foodNameSnapshot, grams, kcal, protein, carbs, fat, fiber?, createdAt)` — **macros are snapshotted** at log time so later food edits never rewrite history.
- `body_weight(id, date, weightKg, note?, createdAt)` Unique on (date, createdAt); multiple per day allowed, daily value = latest of the day.
- `body_measurement(id, date, type, valueCm)`; `progress_photo(id, date, filePath, note?)` (P2).
- Additional entities from section 8f (`cardio_session`, `water_log`, `wellness_checkin`, `habit`, `habit_log`, `day_note`, plus `superset_group`, `rir`, `progressionRule`, `body_weight.source` columns) are added in M10 with migrations (version 2+).
- `user_profile(id=1, ...)` fields per section 8a.
- `goal(id=1, goalWeightKg?, targetDate?, kcalTarget, proteinG, carbsG, fatG)`.
- Settings live in DataStore (units, theme, weekStart, timer defaults, notifications, seedVersion).

Database: `exportSchema = true`, schema JSON checked in under `app/schemas`. Start at version 1; provide a tested migration path pattern (write at least one `MigrationTestHelper` test scaffold even if only v1 exists). Foreign keys ON; cascade deletes only where listed above (deleting a program never deletes sessions).

## 6. Calculation rules (pure Kotlin in `domain`, unit-tested)

- **Meal entry macros:** `value = per100g * grams / 100`. Round for display only; store full precision. Day totals = sum of entry snapshots. Calories shown as integers; macros to 1 decimal.
- **Serving to grams:** `grams = servingCount * serving.grams`.
- **Recipe per-serving:** sum(ingredient per100g * grams/100) / servings.
- **Set volume:** `weightKg * reps` for completed WORKING and DROP sets only. Warm-ups excluded. Session volume = sum. Weekly volume buckets by the user's week-start day.
- **e1RM (Epley):** `w * (1 + reps/30)` for reps 1..12, else not computed. A rep of 1 uses `w`.
- **PRs:** per exercise, (a) heaviest weight at >= N reps for N in 1,3,5,8,10, (b) best e1RM, (c) best session volume. A PR is flagged only if it strictly beats all earlier COMPLETED sessions. Compute on read from history; do not store hardcoded records.
- **Body-weight trend:** daily value = latest entry of that date; trend = trailing 7-calendar-day mean over days that have data (needs >= 3 points else show raw only). Weekly change = trend(today) - trend(today-7d) when both exist.
- **Streak:** consecutive scheduled training days completed; rest days do not break it; a missed scheduled day breaks it. Also show workouts-this-week.
- **Unit conversion:** kg<->lb factor 2.2046226218, cm<->in 2.54. Display rounding: kg to 0.1 (input step 0.25 or 0.5 configurable), lb to 0.1. Never convert stored values repeatedly; always derive from stored kg.
- **Missed workouts:** a scheduled day with no COMPLETED session and date < today is "missed". Provide "Mark done / log retroactively" (creates a COMPLETED session dated that day) and "Skip day". The program does not auto-advance on missed days: next workout stays pending until done or skipped.

## 7. Behaviour specifications

**Active workout and recovery**
- Starting a workout creates `workout_session(IN_PROGRESS)` plus `session_exercise` and pre-filled empty `set_log` rows from the prescription, in ONE transaction.
- Every edit (weight, reps, checkbox, add/remove set, replace, skip) is written to Room immediately (debounced <= 300 ms for typed text, immediate on checkbox). No "save at end" model.
- On launch, if a session is IN_PROGRESS or PAUSED, show a Resume banner on Home and offer Resume / Discard. Resuming restores scroll to the first incomplete exercise.
- Pause stores `pausedAt`; resume adds to `totalPausedMs`. Elapsed = now - startedAt - totalPausedMs.
- Finish: confirm if incomplete sets exist (options: finish anyway marking untouched sets as not done, or go back). Summary then Save. Discard requires confirmation and sets status DISCARDED (kept out of history and stats).
- Previous performance: for each exercise show the most recent COMPLETED session's sets and prefill weight/reps hints (as placeholders, not as saved values).
- Replace exercise: sets `replacedFromExerciseId`, keeps prescription, keeps already-logged sets on the old exercise only if the user chooses; "Revert" restores the original and its prescription. Replacement never edits the program template.
- Skip exercise: status SKIPPED, excluded from volume/PR.
- Editing a completed set after the fact is allowed in History detail and recalculates derived stats on read.

**Rest timer (fixes known flaws in other apps)**
- State is `endAtEpochMs`, persisted in `rest_timer`. Remaining time is always computed from the clock, so it is correct after backgrounding, switching apps, or process death.
- Starts automatically when a set is checked (duration = exercise override, else prescription, else default 90 s). Controls: +15 s, -15 s, skip.
- Schedule an alarm with `AlarmManager.setAndAllowWhileIdle` (inexact is fine; do NOT request exact-alarm permission) that posts ONE notification on a dedicated channel ("Rest timer"), replacing any previous one (fixed notification id), with vibration. Cancel the alarm and notification on skip/finish.
- Tapping elsewhere in the workout screen must never cancel the timer. Logging the next set while the timer runs is allowed.
- If notifications are denied, the in-app timer still works; Settings explains this.

**Nutrition**
- Entry flow: pick meal -> search -> choose food -> choose serving + quantity -> live preview -> save. Edit and delete from the day view.
- Duplicate protection: adding the same food + same grams + same meal within 3 seconds is ignored (double-tap guard). Otherwise duplicates are allowed (user may eat twice).
- Local search: case-insensitive prefix/substring on name and brand, ranked by prefix match then favourites then recents. Bundled foods have no brand. Online results are appended per section 8b.
- Targets: kcal and protein/carbs/fat grams; Settings lets the user enter grams directly or set kcal + percentage split with a computed gram preview (4/4/9 kcal per g). Show a warning if macro-derived kcal differs from the kcal target by > 5%.
- Weekly calorie balance on Progress: daily intake minus target, summed over 7 days. Do NOT estimate TDEE or expenditure (no data).

**Progress**
- Charts are custom Compose `Canvas` composables (line, bar). Requirements: axis labels, touch/drag to inspect a point (tooltip with date and value), range selector, empty-state when < 2 points, accessibility `contentDescription` summarising the series.

**Export/import**
- Export writes one JSON file via SAF: `{ "schemaVersion": 1, "exportedAt": ..., "app": "ironlog", "tables": { ... } }` including all user data (sessions, set logs, custom exercises/foods/recipes, meal entries, weights, measurements, goals, programs (non-built-in), settings; photo files listed by relative path and, optionally, embedded as base64 only if user opts in).
- Import: validate schemaVersion and required keys and referential integrity BEFORE writing anything; run in a single Room transaction; offer Replace-all or Merge (merge dedupes by natural keys; on conflict keep existing). Reject files from newer schema versions with a clear message. Round-trip test: export -> wipe -> import -> equality of all tables.

**Time handling**
- "Today" is evaluated from the device's current local date on each foreground. If the timezone or date changes while the app runs, recompute Home/Nutrition. Entries keep the local date they were logged under.

## 8a. Onboarding and personalised suggestions (deterministic, no generative AI)

**First-launch onboarding** (skippable per field except units; every answer editable later in Settings -> Profile). Persist in a `user_profile` row (add this entity to section 5: name?, birthDate?, sex {MALE, FEMALE, UNSPECIFIED}, heightCm, startWeightKg, units, goal {BUILD_MUSCLE, LOSE_FAT, MAINTAIN, GET_STRONGER}, experience {BEGINNER, INTERMEDIATE, ADVANCED}, daysPerWeek 2..6, sessionMinutes, equipment set {FULL_GYM, DUMBBELLS, BARBELL_RACK, MACHINES_CABLES, BODYWEIGHT_ONLY, BANDS}, avoidList (free-text exercises/movements to avoid), activityLevel {SEDENTARY, LIGHT, MODERATE, VERY_ACTIVE}, goalWeightKg?, targetDate?, dietPreference {NONE, VEGETARIAN, VEGAN, EGGETARIAN, OTHER}, schedule preferred training weekdays). Screens: welcome, about you, body, goal, experience, equipment, schedule, summary with computed targets. Writing the starting weight also creates a `body_weight` entry.

**Targets (estimates, shown with the formula, always editable):** BMR by Mifflin-St Jeor (male: 10w + 6.25h - 5a + 5; female: ... - 161; UNSPECIFIED: average of both), times activity factor (1.2 / 1.375 / 1.55 / 1.725) = maintenance kcal. Goal adjustment defaults: LOSE_FAT -20%, BUILD_MUSCLE +10%, GET_STRONGER +5%, MAINTAIN 0. Protein default 2.0 g/kg bodyweight, fat 25% of kcal, carbs = remainder. Show "Estimates, not medical advice". If age < 18, skip auto-calorie suggestions and require manual targets.

**Program recommender** (`domain/Recommender.kt`, pure Kotlin, unit-tested). Input = `user_profile`. Output = a ranked list of programs with a plain-language "Why this was suggested" line.
- Split by days: 2-3 -> Full Body; 4 -> Upper/Lower; 5-6 -> Push/Pull/Legs (6 repeats it). Use the built-in templates when they fit; otherwise GENERATE a custom program (saved as an editable, non-built-in `program`).
- Generator rules: 2-3 compound movements per day first, then isolation; cover each major muscle group (chest, back, shoulders, quads, hamstrings, glutes, biceps, triceps, calves, abs) at least 2x/week when days allow; weekly sets per muscle: BEGINNER ~8-10, INTERMEDIATE ~10-14, ADVANCED ~12-18; rep ranges by goal: BUILD_MUSCLE 6-12 compounds / 10-15 isolations, GET_STRONGER 3-6 main lifts + 6-10 accessories, LOSE_FAT/MAINTAIN 8-12; rest 2-3 min compounds, 60-90 s isolations; fit exercise count to `sessionMinutes` (~8-10 min per exercise incl. rest).
- Exercise selection filters the seed by equipment availability and the user's avoidList (substring match on name and muscle), prefers `mechanic = compound` for main slots, never selects an exercise whose required equipment the user lacks. Selection is deterministic (stable ordering by a fixed priority list per muscle, tie-break by id), so tests are repeatable.
- **Progressive overload suggestions** during workouts: double progression. When every working set of an exercise hit the TOP of its rep range at the same weight last session, suggest +2.5 kg (upper body/isolation) or +5 kg (lower body compounds) (convert for lb users, round to the nearest plate-friendly step). If the user missed repMin on 2+ sets in two consecutive sessions, suggest -5-10%. Show as a dismissible chip with the reason. Never auto-change logged values.
- Deload suggestion: after 6 consecutive completed training weeks with no skipped week, offer (not force) a lighter week (-40% sets).
- Replacement suggestions rank by same primary muscle, same mechanic, equipment the user has, then favourites.
- Tests: for every combination of days 2-6 x equipment sets x experience x goal (a pruned matrix is fine), assert: no empty training day, no exercise needing unavailable equipment, avoid-list respected, every exercise id exists in the seed, session length within +-20% of target.

## 8b. Food lookup: bundled + online (exact label data where it exists)

Goal: the user types a food name or scans a barcode and gets nutrition details automatically. Honest limits: no database has every food; crowd-sourced data can be wrong. The app must show where each number came from and let the user correct it.

Search order for any query: (1) local foods (custom, recent, favourites, bundled USDA subset), (2) if online, query the providers below, merge results with a source badge, de-duplicate by barcode or (name, brand).

**Provider A: Open Food Facts (packaged foods, barcodes; keyless).**
- Barcode: `GET https://world.openfoodfacts.org/api/v2/product/<barcode>.json?fields=code,product_name,brands,nutriments,serving_size,serving_quantity,quantity,nutrition_data_per`. Text search: use the current official search endpoint (check the API docs at https://openfoodfacts.github.io/openfoodfacts-server/api/ before coding; do not assume an old endpoint). Search only on submit/after a debounce of >= 600 ms, never on every keystroke, and cap results.
- You MUST send a custom `User-Agent`, e.g. `Ironlog/1.0 (personal use; <contact email from local.properties OFF_CONTACT>)`. Respect rate limits: cache every product locally, never re-fetch a barcode already stored, back off on HTTP 429/5xx.
- Use `nutriments` per-100g keys (`energy-kcal_100g`, `proteins_100g`, `carbohydrates_100g`, `fat_100g`, `fiber_100g`). If kcal is missing but kJ exists, convert (kJ / 4.184) and mark `confidence = MEDIUM`. If any of kcal/protein/carbs/fat is missing, show the item as "Incomplete" and require the user to fill in the missing values before logging. Never substitute zero.
- Attribution: show "(c) Open Food Facts contributors" with a link to https://world.openfoodfacts.org/terms-of-use on every screen displaying OFF-derived data and under Settings -> About. Data is ODbL: fine for personal use; the README must warn that sharing an export containing OFF-derived foods is redistribution under ODbL (attribution + share-alike).

**Provider B: USDA FoodData Central API (generic + Branded foods; free API key).**
- Needs a free API key from api.data.gov. Read it from `local.properties` as `FDC_API_KEY`, expose via `BuildConfig`, keep `local.properties` gitignored. If the key is absent, disable Provider B gracefully and say so in Settings. Check the current endpoint docs at https://fdc.nal.usda.gov/api-guide before coding. Branded entries expose label nutrients per serving and per 100 g; prefer label values.
- Source badge "USDA". Cite FoodData Central in About.

**Rules for both providers**
- Network via `HttpURLConnection` or OkHttp (one dependency max), kotlinx-serialization for parsing, 10 s timeouts, no retries beyond one backoff, all calls off the main thread.
- Tapping a result shows a detail screen: per 100 g and per serving, ALL available fields, data source, last-fetched date, a "Verify against your package label" notice, and an Edit button. Saving to the diary stores a local `food` row (source `OFF` or `USDA_ONLINE`, `sourceRef` = barcode or FDC id) and a snapshotted `meal_entry`. Edited values mark `confidence = USER`; a later re-fetch must never overwrite user edits.
- Offline: previously fetched/saved foods stay searchable; show "No connection: showing saved foods only". Failures never block manual entry.
- Barcode scanning: CameraX + ML Kit barcode scanning using the **bundled** model artifact (so scanning works without Google Play services download), request CAMERA only when the scanner opens, support torch toggle and manual barcode entry fallback. On an unknown barcode offer "Create custom food" prefilled with the barcode.
- Indian packaged-food coverage in Open Food Facts is partial; when nothing is found, the user creates a custom food from the label (name, serving, kcal, protein, carbs, fat). Make that form fast: serving size + per-serving values, auto-convert to per-100 g.
- Tests: JSON parsing for each provider using saved sample payloads in `src/test/resources` (do NOT hit the live network in unit tests), missing-nutrient handling, kJ conversion, de-duplication, cache hit avoids refetch, user-edit-not-overwritten.


## 8c. Engagement and tracking layer (local-only substitutes for server features)

These reproduce the *experience* of leaderboards, programs unlocking weekly and an in-app community without any server. All of it is computed from local data; nothing is faked.

- **XP, levels, medals/badges.** XP per completed workout = 100 + 5 per completed working set (cap 250 per session) + 50 for each new PR; +10 per day with a complete food log (all four meals or kcal within +-15% of target). Level thresholds: level n needs 500*n*(n+1)/2 total XP (tests pin the values). XP is derived by replaying history (a pure function), never stored as a mutable counter, so edits and imports stay consistent. Medals (each with a unit test): first workout, 10/50/100/250 workouts, 4-week and 12-week training streaks, first PR, 25 PRs, 100,000 kg lifetime volume, first 7-day food-log streak, 30-day food-log streak, first progress photo, goal weight reached. Profile screen shows level, XP bar, medals earned/locked with unlock dates.
- **"Personal leaderboard" substitute:** a Stats screen with lifetime totals (workouts, volume, sets, PRs, hours trained), best week, current/longest streak, and per-lift all-time bests. No ranking against other users.
- **Weekly cycle substitute for "live programs unlocking week by week":** a program may have `unlockMode = WEEKLY`; later weeks show as locked until the program's own week start date, with a countdown. Default is all weeks open. This is a local pacing option only.
- **Quick / on-demand workouts:** generate on demand (deterministic, section 8a generator) by target muscle group(s), time (15/30/45/60 min) and equipment; saved as favourites; startable in one tap from Train. Also ship ~12 fixed built-in quick sessions (e.g. 30-minute upper, dumbbell-only legs).
- **Set techniques in the logger:** programmed drop sets and rest-pause render as labelled sub-rows with their own weight/reps fields and timers (drop = no rest, rest-pause = 15-20 s mini-rest). Show a short tempo cue (e.g. 3-1-1) when the program day specifies one (`tempo` nullable text on `program_day_exercise`). Per-exercise notes persist across sessions and display next time.
- **History backfill:** from History or a missed day, "Log past workout" creates a COMPLETED session on a chosen past date with editable sets, marked `isBackfilled`; it counts for volume, streaks, PRs and XP.
- **Dashboard activity stats:** Home card with workouts this week/month, total volume trend vs last week, weekly calorie balance (intake minus target, no expenditure guess), average daily protein.
- **Progress photos (camera):** capture with `ActivityResultContracts.TakePicture` into app-private storage via FileProvider, or pick with the system photo picker; request CAMERA only at the moment of capture. Gallery by date, pose tag (front/side/back), side-by-side compare of two dates with a slider, delete, include in export only if the user opts in. Photos never leave the device.
- **Body measurements:** chest, waist, hips, arms, thighs, neck with history and a line chart.
- **Reminders:** optional local notifications: training-day reminder at a chosen time, food-log reminder, weigh-in reminder. Scheduling uses inexact alarms (rescheduled on boot via `BOOT_COMPLETED` receiver, add that permission), respects the user's timezone, can be toggled per type.
- **Share a workout summary:** render the summary as an image/text and use the Android share sheet (`ACTION_SEND`); no accounts, no backend.
- **Home polish:** haptic feedback on set completion and timer end (respect a Settings toggle), smooth sheet dismissal and tab transitions.

Not reproducible here and NOT to be built: other users' leaderboards, friend "pods", a shared community feed, coach videos and proprietary programs.

## 8d. Goal tracking, photo check-ins and optional AI coach

Design principle: **numbers decide, photos inform.** Distance-to-goal comes from measured data (weight, measurements, lifts, adherence) computed deterministically. Photos are for visual comparison and, optionally, qualitative AI commentary. The app must NEVER present a body-fat percentage, "physique score" or similar number derived from a photo, because photo-based estimates are unreliable.

**A. Goal engine (deterministic, `domain/GoalEngine.kt`, unit-tested).** Extend `goal` and add `lift_goal(id, exerciseId, targetWeightKg, targetReps, targetDate?)` and `measurement_goal(id, type, targetCm, targetDate?)`. For each active goal show:
- Weight goal: start, current (7-day trend value), target, remaining, percent complete = (start-current)/(start-target) clamped 0..100 (handle gain goals by sign), actual weekly rate (trend over the last 28 days, needs >= 3 points over >= 14 days, else "not enough data"), required rate to hit `targetDate`, projected arrival date at the current rate (or "not on track / moving away"), and a status chip: AHEAD, ON TRACK, BEHIND, NEEDS DATA.
- Lift goal: current best e1RM at the target exercise vs the target's e1RM, percent, trend slope over the last 8 weeks, projected date.
- Measurement goal (e.g. waist): same pattern using logged measurements.
- Adherence block (last 4 weeks): workouts completed vs scheduled, average daily kcal vs target, protein target hit-rate, weigh-in frequency.
- **Safety clamps (hard-coded, tested):** never suggest a loss rate above 1.0% of bodyweight per week or a daily target below max(1500 kcal for MALE / 1200 kcal for FEMALE and UNSPECIFIED, BMR); if the user's goal would require more, show the required rate and recommend extending the date instead. Do not offer fat-loss suggestions if BMI < 18.5 or age is under 18 or unknown: show a neutral message to speak to a doctor or dietitian instead. Always label outputs "estimates, not medical advice".
- "What to do next" panel: rule-based suggestions with the reason, e.g. behind on weight goal and adherence high -> propose a small kcal change (<= 10% of target) or extending the date; adherence low -> name the lowest-adherence area first; strength stalled 3+ sessions -> suggest deload or rep-range change; protein hit-rate low -> say so. Each suggestion is a card with an Apply button that changes targets only after confirmation. Nothing is changed automatically.

**B. Photo check-in protocol (on-device).**
- Capture screen with a body-outline overlay, front/side/back pose tags, a 10-second timer, a lighting/distance reminder, and a reminder to use the same spot, time of day and clothing each time.
- Use ML Kit pose detection (bundled model, on-device) ONLY for framing quality: warn if the full body is not in frame, the person is cropped, or the pose differs strongly from the previous check-in of the same pose tag. Do not derive body measurements or composition from landmarks.
- Check-in screen: side-by-side and slider compare with any earlier date, plus the weight, waist and workout adherence between the two dates shown underneath, so the photo is read next to the numbers.

**C. Optional AI coach: free-first and privacy-first. OFF by default.**
Provider setting (Settings -> AI coach): `NONE` (default, feature hidden) | `GEMINI_FREE_TEXT_ONLY` | `PAID_WITH_PHOTOS`.
- **GEMINI_FREE_TEXT_ONLY (free):** the user pastes their own free Gemini API key from Google AI Studio. This provider sends ONLY a compact numeric/text summary (goal, trend, rates, adherence, measurements, program name). It never sends photos, names, email, or EXIF. Implement it as a separate request-builder class that has NO image parameter, and add a test asserting the serialized request contains no image/inline-data parts. Show this notice before first use: "Google's free (unpaid) Gemini terms say submitted content can be used to improve its products and may be read by human reviewers. This mode sends only numbers, never photos." Verify the current endpoint, request format, free-tier model ids and rate limits at https://ai.google.dev/gemini-api/docs before coding; the model id is a user-editable Setting; handle HTTP 429 by showing "rate limit reached, try later" (one backoff only). In this mode the AI cannot comment on photos; photos remain for the user's own side-by-side comparison.
- **PAID_WITH_PHOTOS (not free, optional):** only selectable after the user ticks an acknowledgement that photos will leave the device and that API usage is billed to their own account. Provider options: Anthropic (`POST https://api.anthropic.com/v1/messages`, headers `x-api-key`, `anthropic-version: 2023-06-01`; default model id `claude-sonnet-5-5`, user-editable, verify at https://docs.claude.com) or a Gemini key on a paid billing tier. The image-sending code path is reachable ONLY from this mode. Send at most 4 images, base64 JPEG, <= 1024 px long side, EXIF stripped, plus the numeric summary. Do not use the free Gemini tier for photos under any circumstances.
- **All providers:** API keys are entered by the user, stored encrypted with an Android Keystore-backed AES-GCM key in app-private storage, never in source, logs, exports or `local.properties`; provide "Remove key". Every analysis needs an explicit confirmation dialog listing exactly what will be sent (counts of photos, if any, and the numeric summary) with Cancel as default; no background or automatic sends. Block the feature if age < 18, or birthDate is missing and the user has not confirmed 18+.
- **Prompt rules:** give qualitative observations only about the supplied data; NO body-fat percentages, physique scores or numeric estimates from photos; NO medical, diagnostic or eating-disorder advice; neutral, non-judgemental tone about appearance; state uncertainty (lighting, pose, time of day, pump); respond ONLY with JSON `{observations:[string], suggestions:[{area: TRAINING|NUTRITION|RECOVERY|CHECKIN_QUALITY, text, reason}], caveats:[string]}`.
- **Handling:** parse defensively (strip code fences, validate schema, discard unknown fields, cap lengths). Never apply anything automatically; any numeric change goes through the goal engine and its clamps. Label results "Generated by AI, may be wrong", offer Delete, store only the text and covered dates (never images). Handle no-network, 401, 429/5xx honestly.
- **Tests (no live network):** parse saved sample responses including malformed ones; schema validation; clamps never exceeded; age gating; EXIF strip/downscale; text-only builder has no image parts; keys never appear in logs or in the export JSON.

**D. Adding photos from the gallery.**
- "Add from gallery" uses the system Photo Picker (`ActivityResultContracts.PickMultipleVisualMedia`, images only, up to 20 at once); no storage permission. "Take photo" uses the camera (section 8c). Both lead to the same import review screen.
- Import review screen: for each picked photo show a thumbnail, a date (default = EXIF DateTimeOriginal if readable, else file date, else today; editable), and a pose tag (front/side/back/other). Confirm saves all. This lets the user back-fill old transformation photos on a real timeline.
- On import, COPY each image into app-private storage re-encoded as JPEG (quality ~90, longest side capped ~2048 px), STRIP all EXIF including GPS, and store a content hash to skip exact duplicates. Delete never touches the original in the gallery.
- Gallery view groups by month, supports multi-select delete, change date/pose later, and compare any two photos. Photos are excluded from exports unless the user opts in.
- Tests: EXIF date fallback order, GPS stripped from the saved file, duplicate skipped by hash, downscale cap respected.

## 8e. Small additions that earn their place (implement in this order, each is small)

1. **Plate calculator and warm-up generator:** given a target barbell weight and the user's plate set (editable, default 20/10/5/2.5/1.25 kg or lb equivalents, bar 20 kg/45 lb) show plates per side; generate 3 warm-up sets (about 40/60/80% of the working weight, decreasing reps) as WARMUP rows the user can accept.
2. **Weekly review screen:** every week-start (or on demand) summarise last week from local data: workouts done vs planned, volume change, PRs, average kcal/protein vs target, weight trend change, one rule-based "focus for next week" line. Fully deterministic.
3. **Automatic local backup:** let the user pick a folder once (SAF `OpenDocumentTree`, persist the URI permission); on app open, if the last backup is older than 7 days, write a dated export JSON there and keep the newest 8. Show last-backup time in Settings and a warning if it is overdue. Never overwrite the only copy.
4. **CSV export** of sessions/sets, meals, and weights for spreadsheets.
5. **Saved meals:** save any meal as a template and re-add it in one tap.
Do not add features beyond sections 8a-8f without being asked.

## 8f. Extended features (built AFTER the core APK exists; see M10)

Each item is independent, has its own tests, and is rolled back individually if it fails its gate. Priorities: P1 = build, P2 = build if P1 items are done and stable.

**P1**
1. **Supersets/circuits:** `superset_group` (nullable int) on `program_day_exercise` and `session_exercise`. In the logger, grouped exercises render as one card, sets alternate A1, B1, A2, B2, and the rest timer starts after the last exercise of the round (not after each).
2. **RIR/RPE and custom program builder:** optional RIR (0-5) field per set (`rir` nullable on `set_log`) shown in history; a full program builder (create from scratch: name, days, add/reorder/remove exercises, sets, rep range, rest, technique, tempo, superset group, notes), plus per-exercise progression rule `{DOUBLE_PROGRESSION (default), FIXED, PERCENT_1RM_WAVE}`. PERCENT_1RM_WAVE prescribes weight as a percentage of the user's current e1RM per program week (editable percentages, e.g. 70/75/80/65 repeating); suggestions only, never auto-logged.
3. **Cardio and activity logging:** `cardio_session(id, date, type {WALK, RUN, CYCLE, ROW, SWIM, OTHER}, durationSec, distanceM?, avgHr?, kcalEstimate?, note)`. Energy estimate = MET x weightKg x hours using a small built-in MET table (state the table values and label them "estimate"); user can override. Shown as "activity" on Home and in the weekly review. It does NOT automatically raise calorie targets; show it beside the calorie balance as information.
4. **Water tracking:** daily target (default 35 ml/kg, editable), quick +250/+500 ml buttons, undo, history chart, optional reminder.
5. **App lock and privacy:** optional biometric/device-credential lock (`androidx.biometric`) on app open and on returning after N minutes (setting), separate toggle to require auth before opening the photo gallery, and a "hide content in recent apps" toggle using `FLAG_SECURE`.
6. **Better rest timer and workout presence:** while a workout is active show an ongoing notification with elapsed time and the rest countdown (chronometer-based, one notification, updates without sounds), selectable end-of-rest sound/vibration pattern, optional "keep screen on during workout" setting, optional voice-free audio cue. All timing still derives from stored timestamps.
7. **Calendar view:** month calendar with markers for trained / missed / rest / weigh-in / photo; tap a day for that day's summary (workout, meals totals, weight, notes, photos). Add a per-day free-text note (`day_note(date PK, text)`).
8. **Food quick-add and macros-only entry:** add calories/protein/carbs/fat directly without a food record (stored as a `meal_entry` with null `foodId`, name "Quick add"), and a "recent barcodes" list.
9. **Pain/discomfort swap:** in the logger a "Hurts" action swaps the exercise via the replacement flow and offers to add it (or its movement pattern) to the avoid-list so the recommender and generator never pick it again until removed.
10. **Automatic pre-migration backup:** before any Room migration runs, copy the database file to app-private `backups/` (keep last 3) and restore it automatically if the migration throws.

**P2**
11. **Wellness check-in:** optional daily sleep hours, soreness (1-5), energy (1-5), stored in `wellness_checkin`. Deterministic readiness hint on Home ("2 nights under 6 h and high soreness: consider a lighter session") as a suggestion only.
12. **Habits/supplements checklist:** user-defined daily habits (e.g. creatine, vitamins) with streaks and a Home tile; `habit`, `habit_log`.
13. **Home-screen widget (Jetpack Glance):** shows today's workout name with a Start/Resume tap target, plus calories and protein remaining. Read-only data from Room; refreshes on log changes and at midnight.
14. **Health Connect (optional, off by default):** read weight and steps only after the user enables it and grants the specific read permissions; import creates `body_weight` entries with source HEALTH_CONNECT (dedupe by timestamp+value) and a daily steps number shown on Home. Check the current Health Connect integration docs (developer.android.com/health-and-fitness) for manifest declarations, permission-rationale activity and Play-less sideload behaviour before coding; if the device lacks Health Connect, hide the feature. Never write data back.
15. **Body ratios:** waist-to-height ratio and BMI shown with plain-language, non-judgemental labels and the usual caveats; hidden if the user opts out in Settings. (Used only by the safety gating in 8d-A.)
16. **Theme extras:** optional Material You dynamic colour, and an AMOLED-black dark option.

Entities to add in section 5: `cardio_session`, `water_log(id, date, ml, createdAt)`, `wellness_checkin(date PK, sleepHours?, soreness?, energy?)`, `habit(id, name, isActive)`, `habit_log(habitId, date)`, `day_note(date PK, text)`, `superset_group` columns, `rir` and `progressionRule` columns, `body_weight.source` {MANUAL, HEALTH_CONNECT}. All included in export/import and covered by the round-trip test.

## 8. Seed data (bundled in `assets/`, loaded once, versioned)

`SeedLoader` runs on first launch (and when `seedVersion` increases) in a transaction, idempotently (by stable ids), without touching user rows. Show a one-time "Setting up..." state if it takes > 300 ms.

**Exercises** — source: `yuhonas/free-exercise-db`, Unlicense (public domain), ~873 records.
- At build time download `https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/dist/exercises.json` into `app/src/main/assets/seed/exercises.json` and commit it. Fields per record: id, name, force, level, mechanic, equipment, primaryMuscles, secondaryMuscles, instructions, category, images.
- Images: they live under `exercises/<id>/<n>.jpg` in that repo. Check total size first. If bundling all images keeps the debug APK under ~150 MB, bundle them (downscaled if needed) and show them in the exercise detail sheet; otherwise bundle none and show text instructions only. Record the decision. Never hotlink exercise images at runtime (the network is reserved for food lookup).
- If the download is impossible in your sandbox, STOP and tell me; do not hand-write 873 exercises. As a minimum fallback you may seed a smaller hand-verified list ONLY if I approve.

**Foods** — source: USDA FoodData Central, CC0 1.0 (public domain). Requested citation: "FoodData Central, U.S. Department of Agriculture, fdc.nal.usda.gov".
- Use the **SR Legacy** (JSON, April 2018) and/or **Foundation Foods** downloads from `https://fdc.nal.usda.gov/download-datasets`. Write a one-off Kotlin or Python script in `tools/` that selects a practical subset (target 600-1500 common generic foods: meats, fish, eggs, dairy, grains, legumes, vegetables, fruit, nuts, oils, common staples; include cooked variants), extracts kcal/protein/carb/fat/fiber per 100 g, and emits `assets/seed/foods.csv` with a header row. Include portion data (`foodPortions` / household measures) as `food_serving` rows where present.
- Drop any food missing kcal, protein, carbs, or fat. Mark source USDA, confidence HIGH, `sourceRef` = FDC id. Do NOT invent or estimate values. If the download is impossible, ship the app with ZERO seed foods, a working custom-food form, and a CSV food import (columns: name,brand,kcal,protein,carbs,fat,fiber per 100 g), and report the limitation.
- Be honest in the UI: Settings -> About says the bundled database is a limited generic-foods set. Broader coverage comes from online lookup (section 8b), which depends on a connection and on third-party data quality.
- Indian staples: USDA coverage of regional dishes is thin and Open Food Facts coverage is partial. The custom-food + recipe features are the supported path. (Do not scrape or embed any other dataset without a verified licence.)

**Programs** — original templates written by you, using exercise ids that exist in the seed. A unit test must fail if any referenced exercise id is missing from the exercise seed.
1. **Push / Pull / Legs** (3 days/week, 1-week repeating cycle, ~5-6 exercises/day, 3-4 sets, rep ranges 6-12, rest 90-180 s).
2. **Upper / Lower** (4 days/week).
3. **Full Body 3x** (3 days/week, compound-focused).
Provide short neutral descriptions. Do not describe them as endorsed by or modelled on any named athlete or app. Users can duplicate and edit any built-in program into a custom one (built-ins themselves are read-only), including reordering days and exercises.

**Sample data:** a debug-only menu item (Settings -> Developer, hidden in release builds) "Load demo data" that generates ~6 weeks of clearly synthetic sessions, meals, and weights flagged `isDemo` so they can be wiped with one action. It must never run automatically and must never ship enabled in release.

## 9. Milestones (stop and verify at each)

**M1 Foundation.** Gradle project, version catalog, Compose theme, nav shell with 5 tabs, launcher icon, Settings skeleton with DataStore. Gate: `./gradlew assembleDebug` succeeds; app launches on emulator/device if one is available.

**M2 Data layer.** Entities, DAOs, DB, repositories, `SeedLoader`, seed tools, domain calculators. Gate: `./gradlew testDebugUnitTest` green with tests for: all calculators in section 6, unit conversion, seed idempotency, program-seed id integrity, round-trip export/import (Robolectric or instrumented, your choice), Room schema export present.

**M3 Onboarding, recommender, programs and Home.** Onboarding flow and `user_profile`, target calculation, `Recommender` + generator, program list/detail/activate, weekly schedule, Home dashboard, missed-day handling. Gate: build + unit tests including the recommender matrix tests.

**M4 Workout execution (highest priority).** Active workout, set logging, rest timer + notification, replace/skip/revert, pause/resume, process-death recovery, summary, history, PRs. Gate: tests for session creation transaction, recovery, timer remaining-time math, PR logic; manual checklist run on emulator/device if available and reported honestly.

**M5 Nutrition and food lookup.** Local search, serving picker, meal logging, day totals, custom foods, recipes, targets, copy meal, then Open Food Facts + USDA online lookup with caching, attribution, incomplete-data handling, and the barcode scanner. Gate: tests for macro math, snapshot immutability, duplicate guard, provider parsing (saved payloads only), cache and edit-preservation rules. Live-network behaviour: test manually if a device/network exists, else mark NOT RUN.

**M6 Progress.** Weight logging and chart, goal, trend, strength/volume/nutrition charts. Gate: tests for trend, ranges, volume buckets.

**M7 Personal features.** Export/import, custom exercises, program duplication/editing, unit settings everywhere, attributions screen, optional measurements/photos (P2). Gate: round-trip test, import validation tests (corrupt file, newer schema, missing keys).

**M7b Engagement layer.** Everything in section 8c: XP/levels/medals (replay-derived), stats screen, weekly-unlock option, quick workouts, drop/rest-pause/tempo in the logger, backfill, dashboard stats, progress photos + compare, measurements, reminders (incl. boot reschedule), share summary. Gate: unit tests for XP replay, level thresholds, every medal rule, quick-workout generator, backfill effect on streak/PR/XP; build green.

**M7c Goals, photo check-ins, gallery import, optional AI coach, small additions.** Section 8d A, B, D, then 8e items 1-5, then 8d C last. Gate: GoalEngine tests (on-track/behind/needs-data, gain vs loss, clamps, BMI/age gating), parsing and gating tests for the AI layer, build green. If C cannot pass its gate within the retry limit, roll back C only and ship everything else.

**M8 QA and APK (this is APK #1, the one that must be safe).** Tag the commit `apk-core`, copy the APK to `Ironlog-core-debug.apk` in the project root, then run the full unit test suite, lint (`./gradlew lintDebug`), instrumented tests if a device/emulator exists, build debug APK, verify artefact. Fix defects; list anything unresolved in `BUILD_LOG.md`.

**M10 Extended features (ONLY after M8 passes and `Ironlog-core-debug.apk` exists).** Implement section 8f in numeric order, P1 then P2. After each item: build, run its tests and the full suite, commit, tag `m10-<n>-pass`. If an item fails its gate after 3 attempts, `git revert` that item only, record it in `BUILD_LOG.md`, and continue with the next. When done, rebuild and copy the final APK to `Ironlog-full-debug.apk`; keep the core APK so I can fall back to it.

**M9 Visual fidelity pass (ONLY if `reference/screens/` exists and contains images; otherwise skip and say so).**
- The user supplies screenshots of a reference fitness app they personally subscribe to. Study them and write `docs/UI_SPEC.md` describing ONLY what is visible: screen name, element hierarchy, spacing rhythm, type scale, colours (as approximate hex values), component shapes, navigation, states shown. Mark anything not visible as "not observed" and do not guess hidden screens.
- Restyle the existing screens to match the **layout, hierarchy, spacing, navigation pattern and interaction flow**. Keep the app's own name, icon, wording and colours' exact brand values out: do NOT copy logos, icons, mascot or athlete imagery, exact marketing copy, photos, videos, or proprietary fonts. Use system fonts or an open-licensed font bundled in assets (record its licence).
- Do not change the data model or break any M1-M8 test. Re-run all gates. Tag `m9-pass`.

## 9a. Smoke-test checklist (run on a device/emulator if one exists; otherwise write it into README as the manual test for me, and mark it NOT RUN)

1. Fresh install: app opens, seeding finishes, Home shows with empty states.
2. Activate "Push / Pull / Legs"; Home shows today's workout.
3. Start workout; log 3 sets with weight/reps; rest timer starts after each checked set.
4. Background the app for 30 s; return; timer shows correct remaining time.
5. Let a timer finish with the app in background; exactly one notification appears.
6. Force-stop the app mid-workout; reopen; Resume banner appears with logged sets intact.
7. Replace an exercise, then revert it.
8. Finish workout; summary shows volume and any PRs; session appears in History.
9. Edit the program template; confirm the past session is unchanged.
10. Log a custom food and a seeded food; day totals equal hand-calculated values.
11. Log body weights on 4 different dates; chart and 7-day trend render.
12. Switch units kg/lb; displayed values convert, stored values do not drift after switching back.
13. Export; delete all data; import; everything is restored.
14. Airplane mode on: everything above except steps 15-17 still works.
15. Onboarding (do this first on a fresh install): fill every field; targets and a recommended program appear with a "Why" line; changing days/week or equipment changes the suggestion.
16. With network: search a common packaged food by name and scan a real barcode; details, source badge and attribution show; log it; totals correct; re-search the same item with network off and it still appears.
17. Create a custom food from a label when a scan finds nothing.
18. Earn a medal (e.g. first workout) and see XP/level update; edit that workout and confirm XP recomputes consistently.
19. Take a progress photo, then compare two dates; confirm photos are absent from export unless opted in.
20. Set a reminder 2 minutes ahead; confirm it fires; reboot the phone and confirm reminders still schedule.
21. Set a weight goal and a date: percent complete, weekly rate, projected date and status chip show; set an unreachable date and confirm the app recommends extending it instead of an extreme deficit.
22. Take front/side check-in photos with the overlay; the framing warning appears when cropped; compare two dates with weight/waist shown beneath.
23. With provider NONE: no AI coach UI appears. With a free Gemini key in text-only mode: the dialog lists a numbers-only payload, Cancel sends nothing, the result renders with the AI label; confirm no photo option exists in this mode. Remove the key and confirm the feature disappears.
24. Add 3 photos from the gallery: review screen shows EXIF dates, edit one date, save; originals untouched; re-adding the same photo is skipped; saved copy has no GPS data.
25. Plate calculator shows correct plates for 100 kg; warm-up generator adds WARMUP rows; weekly review renders; set a backup folder and confirm a dated file is written.
26. Superset card alternates A1/B1/A2/B2 and rests only after each round; log RIR on a set and see it in history.
27. Build a program from scratch in the builder, activate it, run its first workout.
28. Log a 30-minute run: estimate shows, calorie targets do NOT change.
29. Water: +250 ml x3, undo one, chart updates.
30. Enable app lock; relaunch, background 5+ minutes, return: prompt appears; gallery requires auth when its toggle is on.
31. Start a workout, lock the phone: ongoing notification shows elapsed time and rest countdown; finish and confirm it disappears.
32. Calendar month view shows markers; day tap shows summary; save a day note.
33. Quick-add 400 kcal; tap Hurts on an exercise: swapped and added to avoid-list; regenerate a program and confirm it is excluded.
34. Simulate a failed migration (test hook) and confirm the pre-migration backup restores.
35. If implemented: wellness check-in hint, habits streak, widget shows today's workout, Health Connect import dedupes.

## 10. Quality requirements

- Architecture: unidirectional data flow. ViewModels expose `StateFlow<UiState>`; repositories expose `Flow`; no Room access from composables; DB work off the main thread.
- Accessibility: min 48dp touch targets, `contentDescription` on icon buttons and charts, support font scale up to 200% without clipped essential controls, don't rely on colour alone (use icons/labels for done/missed).
- Responsive: phones in portrait first; layouts must not break in landscape or on narrow (360dp) widths; large numeric inputs usable one-handed.
- Privacy/security: all data in app-private storage; `android:allowBackup="false"` (we provide our own export); no logging of personal data in release; no secrets in the repo (`FDC_API_KEY` and `OFF_CONTACT` live only in gitignored `local.properties`); the only outbound requests are food lookups carrying the search text or barcode and nothing about the user.
- Performance: cold start to Home < 2 s on a mid-range phone after first seed; exercise search < 100 ms perceived.
- Keep ProGuard/R8 off for debug; if you enable minify for release, add keep rules for kotlinx-serialization and test the release build.

## 11. Build, output, install

- Debug build: `./gradlew assembleDebug`
- Expected APK: `app/build/outputs/apk/debug/app-debug.apk`
- Verify existence and metadata:
  - `ls -l app/build/outputs/apk/debug/app-debug.apk`
  - `$ANDROID_HOME/build-tools/<ver>/aapt2 dump badging app/build/outputs/apk/debug/app-debug.apk | head` (confirm package `app.ironlog.personal`, minSdk, versionName)
  - `apkanalyzer apk summary ...` if available
- Install over USB: enable Developer options + USB debugging on the phone, then `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
- Install by file transfer: copy the APK to the phone, open it in Files, allow "Install unknown apps" for that file manager when prompted.
- A debug APK is signed with the debug keystore; that is fine for personal use. A release variant is optional; if you add one, sign it with a locally generated keystore that is **gitignored**, and document the command in `README.md`. Do not commit keystores or passwords.
- Write `README.md` with: what the app is, build/install steps, data sources and licences, how export/import works, known limitations.

## 12. Deferred (do not build now; list in README as future work)

Health Connect, widgets, wear, cardio GPS tracking, supersets UI beyond basic grouping, cloud backup, restaurant-menu databases, licensed Indian food datasets, AI/LLM-based coaching.

## 13. Attributions (must appear under Settings -> About)

- Exercise data: free-exercise-db by yuhonas, released under the Unlicense (public domain).
- Nutrition data: FoodData Central, U.S. Department of Agriculture (CC0 1.0).
- Packaged-food data: (c) Open Food Facts contributors, ODbL 1.0, https://world.openfoodfacts.org/terms-of-use (shown wherever OFF data appears).
- App is independent and not affiliated with any fitness brand.

## 14. Completion criteria

All of the following are TRUE and demonstrated in `BUILD_LOG.md`:
1. `./gradlew testDebugUnitTest` passes (include the actual summary line).
2. `./gradlew assembleDebug` passes and the APK exists at the stated path with correct package metadata.
3. Every P0 flow works with real persistence: start/log/finish a workout, kill the app mid-workout and resume, rest timer survives backgrounding, log meals with correct macro totals, log weight and see the chart, export then import restores identical data.
4. Merged manifest permissions are exactly: INTERNET, ACCESS_NETWORK_STATE, CAMERA, POST_NOTIFICATIONS, VIBRATE, RECEIVE_BOOT_COMPLETED for the core APK; the full APK may additionally declare USE_BIOMETRIC and, only if item 14 shipped, the Health Connect read permissions for weight and steps (show `aapt2 dump permissions` for both APKs). The only code that uses the network is the food-lookup provider layer and the optional AI coach.
5. With network disabled, all features except online food lookup work; state this, and do not claim it was tested unless you tested it.
6. `BUILD_LOG.md` lists device/emulator testing actually performed (or states none was available), unresolved defects, seed decisions (image bundling, food count), and every deviation from this brief.

Begin with section 0: inspect the environment, report what you find, then start M1.
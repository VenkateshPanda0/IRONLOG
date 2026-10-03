# Ironlog build state

- Last passed check: `gradlew.bat --version` reported Gradle 8.9 / JDK 17.0.18. No app milestone gate has passed; assemble, tests, lint, sync, and device gates remain NOT RUN.
- Source authored: prior partial M1-M7b work; this continuation adds wrapper, `.gitattributes`, 876 exercise records, 1,200 USDA foods / 2,271 portions, transactional/versioned loaders, seed readiness state, ViewModel factory and usage, feature screen split, full-screen workout/history views, seed fixtures/idempotency tests, and ktfmt formatting. Kotlin files parse under ktfmt but none are compiled.
- Current repo: local `main` tracks `origin/main`; app histories/tags remain preserved. Environment has JDK 17.0.18; SDK under `C:\Users\VENKATESH PANDA\AppData\Local\Android\Sdk` with platforms 33/34/36/36.1 and build tools; no `sdkmanager` on PATH; no attached devices. `C:\Android\Sdk` does not exist in this shell. Gradle/Google/Maven HEAD probes returned 200.
- In progress: Phase 1 remains open because its build/test/lint gate cannot run. Source-only Phase 3 work has also begun: deterministic split selection and exercise prescriptions now use seeded strength metadata, equipment/avoid-list filters, experience-based sets, goal rep ranges, duration limits, and stable ID tie-breaks. The onboarding preview shows generated days and exercises. Recommendation test sources cover the split matrix and filters; tests are authored but NOT RUN. Additional profile fields shown in onboarding are not persisted, generated recommendations are not saved to programs, and the built-in program seed/catalog remains absent.
- Failed/blocker: `gradlew.bat assembleDebug --no-daemon` and retries stop before Gradle project configuration with `java.io.IOException: Unable to establish loopback connection`. No source compilation, tests, or lint ran.
- Version assumptions: AGP 8.7.3, Gradle 8.9, Kotlin 2.0.21, KSP 2.0.21-1.0.28, Compose BOM 2024.12.01, Room 2.6.1, compile/target SDK 35, min SDK 26, Java 17. Sync/build compatibility is unverified.
- Data assumptions: exercise metadata and SR Legacy values are bundled, but Room seeding is unverified; no exercise images are bundled. USDA API key/contact values are absent; USDA online lookup is disabled. OFF uses generic contact `unset@example.invalid`.
- M9 skipped: no supplied `reference/screens/` images.
- Exact next step: fix the Java loopback socket failure or open/sync the project in Android Studio; then run `assembleDebug`, `testDebugUnitTest`, and `lintDebug` and generate the Room v1 schema. Before calling M3 source-complete, persist the full onboarding profile, write program seed JSON with an exercise-ID integrity check, save/activate recommendations, and implement the program catalogue/detail/next-day UI. Do not mark any gate passed until executed successfully.

## Assumptions

- Training split selection is deterministic: 2–3 days use a Full Body cycle, 4 use Upper/Lower, 5 use a PPL cycle with the three sessions available as the repeating sequence, and 6 use two PPL cycles. The 5-day weekly schedule/rest behavior is not implemented yet.
- Exercise selection matches normalized primary-muscle labels and exact normalized equipment labels. Unknown equipment is excluded whenever a non-empty equipment set is selected. Muscle priorities are fixed in source and equal-priority ties resolve by exercise ID.
- Session duration maps to `floor(minutes / 9)`, clamped to 3–8 exercises. This is a coarse estimate, not a verified session-time calculation.
- Beginner plans use three sets per exercise; intermediate and advanced use four. Compound/accessory weekly volume targets are not yet distributed. Strength goal compounds use 3–6 reps; other exercises use 6–10.
- Current onboarding equipment choices are broad `GYM` or `BODYWEIGHT` presets. The full brief's equipment set, age/birth-date rules, units, activity categories, diet, schedule, target date/weight, and profile editing are still unimplemented. The broad gym preset allows all exercise-dataset equipment except foam-roll-only and equipment-free rows.
- Recommendations consume local exercise metadata only. No recommendation is saved or activated automatically.
- Workout domain matching uses exact normalized canonical muscle labels from the current seed; aliases outside the seed's labels are not inferred.

- M6 progress source: daily latest-weight aggregation, range filtering and a chart of real recorded values; strength/volume/nutrition charts remain incomplete.

- M7 source: JSON backup/import bundle, SAF create/open document pickers, transactional import validation, custom exercise creation, stored unit conversion helpers, and backup test source. Photo/measurement and program edit UI remain incomplete.

- M7b domain slice: XP/level replay, listed medal rule calculations, food/training streak logic, quick-workout filtering and test source authored. Not a complete M7b UI/persistence implementation; gate NOT RUN.

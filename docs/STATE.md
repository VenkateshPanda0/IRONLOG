# Ironlog build state

- Last passing milestone/tag: none; all build, test, lint, sync, and device gates remain NOT RUN by user instruction.
- Source authored: partial M1 foundation; partial M2 Room/repository/domain layer; partial M3 onboarding and rule-based recommendation; partial M4 persistent sessions, set logging, pause/resume, absolute-time rest timer and PR calculation; partial M5 local nutrition/custom food and Open Food Facts lookup/cache; partial M6 weight trend; partial M7 backup/import; partial M7b engagement domain slice. These are source notes, not verified milestone completions.
- Current repo: local `main` is clean and tracks `origin/main` at `cf18abde4639f403a82bfc2a88b8f96ca33ac176`. `origin/main` was fetched and verified to contain 45 tracked files, including the Android app, tests, Gradle files, docs, scripts, and LICENSE. The code from both prior app histories is reachable from `main`; tags are preserved.
- In progress: continue source-only work on remaining brief requirements and keep build/test/download gates NOT RUN.
- Failed/remaining Git issue: a normal push of consolidated `main` succeeded. Attempting to delete remote `master` and `codex/ironlog-progress` returned `remote ref does not exist` for both, while a later fetch still lists both refs. Investigate GitHub branch state/permissions before retrying; do not force-push or discard history.
- Version assumptions: AGP 8.7.3, Gradle 8.9, Kotlin 2.0.21, KSP 2.0.21-1.0.28, Compose BOM 2024.12.01, Room 2.6.1, compile/target SDK 35, min SDK 26, Java 17. Sync/build compatibility is unverified.
- Data assumptions: no third-party data downloaded; seed loader inserts nothing without `assets/seed/exercises.json`. USDA API key/contact values are absent; USDA network lookup is disabled. OFF uses generic contact `unset@example.invalid`.
- M9 skipped: no supplied `reference/screens/` images.
- Exact next step: continue implementation source-only per the brief, with all gates still NOT RUN; separately inspect why remote branch deletion is rejected despite `main` being published.

- M6 progress source: daily latest-weight aggregation, range filtering and a chart of real recorded values; strength/volume/nutrition charts remain incomplete.

- M7 source: JSON backup/import bundle, SAF create/open document pickers, transactional import validation, custom exercise creation, stored unit conversion helpers, and backup test source. Photo/measurement and program edit UI remain incomplete.

- M7b domain slice: XP/level replay, listed medal rule calculations, food/training streak logic, quick-workout filtering and test source authored. Not a complete M7b UI/persistence implementation; gate NOT RUN.

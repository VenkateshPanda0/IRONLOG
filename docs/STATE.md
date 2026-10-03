# Ironlog build state

- Last passing milestone/tag: none; all gates are deliberately NOT RUN.
- Source authored: partial M1 foundation; partial M2 Room/repository/domain layer; partial M3 onboarding and rule-based recommendation; partial M4 persistent sessions, set logging, pause/resume, absolute-time rest timer and PR calculation; partial M5 local nutrition/custom food and Open Food Facts lookup/cache. These are implementation notes, not verified completions.
- In progress: continue source-only work and document incomplete requirements. Build/test/download requests remain disabled by user instruction.
- Failed: normal `.git` writes blocked by deny-write ACL; Git push failed for missing credentials; GitHub connector branch and content write calls returned HTTP 403. No remote commits have succeeded.
- Version assumptions: AGP 8.7.3, Gradle 8.9, Kotlin 2.0.21, KSP 2.0.21-1.0.28, Compose BOM 2024.12.01, Room 2.6.1, compile/target SDK 35, min SDK 26, Java 17. Sync/build compatibility is unverified.
- Data assumptions: no third-party data downloaded; seed loader inserts nothing without `assets/seed/exercises.json`. USDA API key/contact values are absent; USDA network lookup is disabled. OFF uses generic contact `unset@example.invalid`.
- M9 skipped: no supplied `reference/screens/` images.
- Exact next step: continue implementing as much of M6-M8 and M10 source as possible without builds/tests/downloads; update README and gate records; add local commits/tags marked `*-not-run`; retry remote publication only after GitHub write access or a credentialed terminal becomes available.

- M6 progress source: daily latest-weight aggregation, range filtering and a chart of real recorded values; strength/volume/nutrition charts remain incomplete.

- M7 source: JSON backup/import bundle, SAF create/open document pickers, transactional import validation, custom exercise creation, stored unit conversion helpers, and backup test source. Photo/measurement and program edit UI remain incomplete.

- M7b domain slice: XP/level replay, listed medal rule calculations, food/training streak logic, quick-workout filtering and test source authored. Not a complete M7b UI/persistence implementation; gate NOT RUN.

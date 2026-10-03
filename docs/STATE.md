# Ironlog build state

- Last passed check: `gradlew.bat --version` reported Gradle 8.9 / JDK 17.0.18. No app milestone gate has passed; assemble, tests, lint, sync, and device gates remain NOT RUN.
- Source authored: prior partial M1-M7b work; this continuation adds wrapper, `.gitattributes`, 876 exercise records, 1,200 USDA foods / 2,271 portions, transactional/versioned loaders, seed readiness state, ViewModel factory and usage, feature screen split, and full-screen workout/history views. This code is uncompiled.
- Current repo: local `main` tracks `origin/main`; app histories/tags remain preserved. Environment has JDK 17.0.18; SDK under `C:\Users\VENKATESH PANDA\AppData\Local\Android\Sdk` with platforms 33/34/36/36.1 and build tools; no `sdkmanager` on PATH; no attached devices. `C:\Android\Sdk` does not exist in this shell. Gradle/Google/Maven HEAD probes returned 200.
- In progress: Phase 1 seed pipeline and compile-safe source cleanup. CSV fixtures and Robolectric Room idempotency/count tests for both 876 exercises and 1,200 USDA foods are authored; all are uncompiled/unrun. See `docs/COMPILE_RISKS.md`.
- Failed/blocker: `gradlew.bat assembleDebug --no-daemon` and retries stop before Gradle project configuration with `java.io.IOException: Unable to establish loopback connection`. No source compilation, tests, or lint ran.
- Version assumptions: AGP 8.7.3, Gradle 8.9, Kotlin 2.0.21, KSP 2.0.21-1.0.28, Compose BOM 2024.12.01, Room 2.6.1, compile/target SDK 35, min SDK 26, Java 17. Sync/build compatibility is unverified.
- Data assumptions: exercise metadata and SR Legacy values are bundled, but Room seeding is unverified; no exercise images are bundled. USDA API key/contact values are absent; USDA online lookup is disabled. OFF uses generic contact `unset@example.invalid`.
- M9 skipped: no supplied `reference/screens/` images.
- Exact next step: resolve Gradle loopback/daemon connectivity or run tasks in Android Studio, then run assemble, unit tests, and lint; generate the Room v1 schema. Review and format remaining dense repository/domain source before moving past Phase 1.

- M6 progress source: daily latest-weight aggregation, range filtering and a chart of real recorded values; strength/volume/nutrition charts remain incomplete.

- M7 source: JSON backup/import bundle, SAF create/open document pickers, transactional import validation, custom exercise creation, stored unit conversion helpers, and backup test source. Photo/measurement and program edit UI remain incomplete.

- M7b domain slice: XP/level replay, listed medal rule calculations, food/training streak logic, quick-workout filtering and test source authored. Not a complete M7b UI/persistence implementation; gate NOT RUN.

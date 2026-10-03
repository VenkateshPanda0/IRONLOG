# Ironlog build state

- Last passing milestone/tag: none; all gates are deliberately NOT RUN.
- Local source milestones authored so far: M1 project foundation and partial M2 Room/domain scaffold. No remote GitHub commit has succeeded.
- In progress: finish source-only implementation/documentation with all verification disabled by user request.
- Failed: ordinary workspace Git write blocked by `.git` deny-write ACL; direct CLI push failed due missing GitHub credentials; GitHub connector write calls returned 403. No build/test was attempted.
- Environment assumptions: AGP 8.7.3, Gradle 8.9, Kotlin 2.0.21, KSP 2.0.21-1.0.28, Compose BOM 2024.12.01, Room 2.6.1, compile/target SDK 35, min SDK 26, Java 17. These versions are pinned but compatibility/sync is unverified.
- Data assumptions: no third-party data was downloaded; seed loader inserts nothing. USDA API key and OFF personal contact are absent. USDA provider is not implemented; OFF placeholder is `unset@example.invalid`.
- M9: skipped because there are no supplied `reference/screens/` images.
- Exact next step: continue source authoring without running builds/tests/downloads; add explicit unit test source; complete README and feature/source status; create local milestone commits/tags marked `*-not-run`; then retry GitHub publishing through an authenticated route if one becomes available. Finish by updating this file and `BUILD_LOG.md` with all remaining limitations.

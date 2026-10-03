# Ironlog build state

- Last passing milestone/tag: none.
- In progress: environment recheck complete; M1 not started.
- Failed: no build attempted; environment check failed to find an accessible SDK and outbound Gradle/Maven access.
- Environment: Java 17.0.18 and Android Studio are installed. This shell's `ANDROID_HOME`/`ANDROID_SDK_ROOT` still point to the prior inaccessible location, despite the user-stated move to `C:\Android\Sdk`; that location does not exist here. `sdkmanager` and `adb` are not on PATH. Gradle distribution and Maven Central probes fail with curl exit 7.
- Exact next step: expose `C:\Android\Sdk` to this process with a platform, build-tools, and platform-tools; refresh this process's SDK environment variables and PATH; enable outbound access to `services.gradle.org` and Maven Central. Then create/build M1 and continue in milestone order.

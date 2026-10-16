# Daily Gallery

An Android app that shows one artwork a day from public museum collections, with the story
behind it, its materials and technique, its history, and what critics and scholars wrote
about it.

Work in progress: see [TODO.md](TODO.md).

## Build

Requires JDK 17 and the Android SDK with platform 35. `local.properties` (git-ignored) must
point at the SDK: `sdk.dir=C\:\\path\\to\\Android\\Sdk`.

```powershell
.\gradlew testDebugUnitTest   # unit and Robolectric UI tests; no device needed
.\gradlew lintDebug
.\gradlew assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
```

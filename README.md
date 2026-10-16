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

## Run on the emulator (VS Code)

[`scripts/dev.ps1`](scripts/dev.ps1) drives an Android emulator, and the VS Code tasks in
[`.vscode/tasks.json`](.vscode/tasks.json) call it (**Terminal > Run Task**):

| Task | Does |
| --- | --- |
| **Set up emulator** | Once: downloads the emulator and an Android 15 image to `D:\android\sdk` and creates a Pixel 7 device in `D:\android\avd` (about 5 GB). |
| **Run on emulator** (Ctrl+Shift+B) | Builds, installs and opens the app; starts the emulator first if needed. |
| **Start emulator** | Boots the emulator. |
| **Tests** | Unit and Robolectric UI tests on the PC. |
| **Emulator screenshot** | Saves the emulator's screen to `build/emulator.png`. |

The emulator needs hardware acceleration. On Windows, turn it on once, from PowerShell as
Administrator, then restart:

```powershell
Enable-WindowsOptionalFeature -Online -FeatureName HypervisorPlatform -All
```

Paths default to this PC's: set `JAVA_HOME`, `EMULATOR_SDK_ROOT` or `ANDROID_AVD_HOME` to
override them. The build SDK and `adb` come from `local.properties`.

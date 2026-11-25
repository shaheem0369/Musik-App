# Contributing to Musik

Musik is customized by Shaheem Aroos ([shaheem0369](https://github.com/shaheem0369)). See the [README](README.md) for features, module structure, and setup.

## Development setup

1. Open the project folder in Android Studio.
2. Use JDK 21 for Gradle and install Android SDK platform 36.
3. Set your SDK location through Android Studio or `sdk.dir` in `local.properties`.
4. Sync Gradle and choose `universalDebug`.

Keep API keys and machine-specific paths in `local.properties` or your environment. Do not commit credentials, signing keys, or local SDK paths.

## Verification

Run from the project root in PowerShell:

```powershell
.\gradlew.bat :app:assembleUniversalDebug :app:testUniversalDebugUnitTest
.\gradlew.bat :app:assembleUniversalDebugAndroidTest
.\gradlew.bat :app:lintUniversalDebug
```

On macOS and Linux, use `./gradlew`. Android instrumentation tests require a connected device or emulator. Review the lint report even when the Gradle task succeeds; this project can report lint errors without failing the task.

After package or generated component renames, use `clean` before building to remove stale Hilt and Kotlin outputs.

## Changes and bug reports

- Keep changes focused and preserve upstream author and license notices.
- Add regression coverage for behavior changes when useful.
- Verify playback, imports, navigation, or widgets on a device when your changes affect those features.
- Include reproduction steps, Android version, device model, build variant, and relevant logs in bug reports.
- Remove private information and credentials from logs before sharing them.

Release builds need the signing configuration in `app/build.gradle.kts` and appropriate signing credentials. Do not publish debug APKs as signed production releases.

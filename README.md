<div align="center">
  <img src="assets/branding/musik-logo.png" alt="Musik logo" width="120" />
  <h1>Musik</h1>
  <p>An Android music player with a Liquid Glass interface, synced lyrics, and local audio support.</p>
  <p>Designed and customized by <strong>Shaheem Aroos</strong> · <a href="https://github.com/shaheem0369">@shaheem0369</a></p>
</div>

## Features

- Stream music and organize your library, playlists, likes, and listening history.
- Import local MP3, FLAC, ALAC/M4A, WAV, DSD, AIFF, AAC, and Ogg files.
- Play music in the background with notification controls and a home screen widget.
- Follow synchronized lyrics with translation support.
- Customize the player with Liquid Glass controls, Material colors, and animated artwork.
- Adjust playback speed, pitch, equalization, and other audio settings.
- Explore listening statistics and shared listening features.

## Listen together across phones

Open the **Together** bottom tab for **Musik Jam**. Choose **Create Room**, enter a display name, and select **Collaborative** or **Host Only**. The host sets a server URL and token once under **Custom Server Settings**, then shares an invite. Guests open or paste the invite to join without the host token; a six-character room code also works when the same server URL is already configured. The live room shows playback, the shared queue, listeners, and Copy Code / Share Invite controls.

A runnable room server and setup instructions are included in [server/README.md](server/README.md). Run it on a computer for same-network listening, or host it behind HTTPS for phones on different networks. The existing direct LAN mode remains available. Local imported files are not streamed to other phones; device and Bluetooth latency can affect speaker alignment.

Some local formats are converted to a compatible FLAC playback copy while the original import is retained. DSD is decoded to PCM; native DSD output is not provided. Online features depend on their respective services.

## Developer

**Shaheem Aroos** — application designer and maintainer of this Musik customization.

[Visit my GitHub profile](https://github.com/shaheem0369)

Source code: [shaheem0369/Musik-App](https://github.com/shaheem0369/Musik-App).

Build instructions are below. A release download page and store listing have not been configured.

## Android packages

| Build | Application ID |
| --- | --- |
| Release | `com.shaheem.yt` |
| Debug | `com.shaheem.yt.debug` |

The renamed package installs separately from earlier packages. Android does not automatically transfer their private app data.

## Build locally

### Requirements

- Android Studio with support for the project's Android Gradle Plugin.
- JDK 21 for Gradle and Kotlin toolchains.
- Android SDK platform 36 and the build tools requested by Gradle.
- Android 8.0 (API 26) or later on the target device.

### Setup

1. Open this project folder in Android Studio.
2. Set the Gradle JDK to JDK 21.
3. Configure your Android SDK through Android Studio or `sdk.dir` in `local.properties`.
4. Sync Gradle and select the `universalDebug` build variant.

From PowerShell on Windows:

```powershell
.\gradlew.bat :app:assembleUniversalDebug
.\gradlew.bat :app:testUniversalDebugUnitTest
```

On macOS or Linux, use `./gradlew` instead of `.\gradlew.bat`.

The debug APK is generated at:

```text
app/build/outputs/apk/universal/debug/app-universal-debug.apk
```

After renaming packages or generated components, run a clean build:

```powershell
.\gradlew.bat clean :app:assembleUniversalDebug :app:testUniversalDebugUnitTest :app:assembleUniversalDebugAndroidTest
```

The last task builds the Android test APK. Running device tests requires a connected device or emulator.

## Project structure

| Module | Purpose |
| --- | --- |
| `app` | Compose UI, library, database, playback, imports, and settings |
| `innertube` | YouTube Music integration and stream resolution |
| `betterlyrics`, `lrclib`, `kugou`, `simpmusic` | Lyrics integrations and parsing |
| `canvas` | Animated artwork support |
| `kizzy` | Discord presence integration |
| `lastfm` | Last.fm integration |

The app uses Kotlin, Jetpack Compose, Material 3, Media3/ExoPlayer, Hilt, Room, Ktor, and Kotlin Coroutines/Flow. Dependencies are defined in `gradle/libs.versions.toml`.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for development and verification steps. When reporting a bug, include the device model, Android version, build variant, reproduction steps, and relevant logs. Remove private information and tokens before sharing logs.

## Credits and license

Musik builds on the work of the [original project](https://github.com/nikhilvishwakarma00/Velune) and contributors to ArchiveTune, InnerTune, Metrolist, Kizzy, SimpMusic, BetterLyrics, and other dependencies. Original author notices are retained in the source.

Licensed under [GNU GPL v3](LICENSE). Native audio dependency notices are included in [audio-decoder-notices.txt](app/src/main/assets/audio-decoder-notices.txt).

Musik is an independent project and is not affiliated with YouTube or Google.

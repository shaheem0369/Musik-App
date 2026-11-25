# Musik – Liquid Glass build

## Musik Jam room flow (2026-11-25)
- Adapted the room connection interaction from the user-provided LastWave screenshots and public Jam source (`https://github.com/Clash-Projects/LastWave-native`): Join/Create tabs, display name, Collaborative/Host Only choices, prominent room code, Copy Code, Share Invite, live playback/queue/listeners, and end/leave session. Implemented using Musik's own UI and existing session engine; no LastWave source code, backend addresses, or unrelated features were imported.
- Custom Server Settings are collapsed by default. Hosts configure their own server once. Invites contain a six-character room code and server origin, never the host token. Guests join without a shared server token; creation remains authenticated and WebSockets require the room-scoped host/guest key.
- Added `musik://jam` invite routing and validation while retaining existing direct LAN links and the advanced LAN screen. The included Node relay now generates uppercase alphanumeric codes. No public Musik relay is deployed or preconfigured.
- Share Invite uses an HTTP/HTTPS server URL with a code. The relay serves a small invite page with an Open Musik app link; pasted links also fill the server and code. Host tokens are never embedded in those links.
- Verification: 38 JVM tests and four Node relay integration tests passed. The Android/Ktor host/guest integration test passed on Samsung SM-G986U1 with the guest using no server token (`reports/musik-jam-relay-device.txt`). The Jam invite intent launches without a process crash; the phone lock screen prevented visual inspection of its content. Audible two-phone speaker alignment remains unverified.

## Together tab and runnable relay (2026-11-25)
- Added Together to bottom navigation, backed by the existing playback/session engine. Online mode uses a user-configured server origin and token rather than hardcoded external endpoints or mismatched host/guest credentials. Direct LAN mode remains available.
- Included a Node.js WebSocket room relay under `server/` with six-digit room codes, host/guest authentication, approval, permissions, queue/playback state, clock samples, bans, rate/message/room limits, and host disconnect cleanup. See `server/README.md` for local-network and HTTPS hosting instructions.
- Fixed clock-offset direction and removed the fixed 1.2-second online playback adjustment. Drift correction now uses measured clock samples. Authenticated WebSockets do not fall back from WSS to cleartext WS, and API connections are closed after requests.
- Verified 35 JVM tests, four Node relay integration tests, and an Android/Ktor host-and-guest protocol integration test on Samsung SM-G986U1 through a temporary relay over USB (`reports/together-relay-device.txt`). Test traffic used synthetic online-track metadata, not streamed audio. Two-physical-phone audible speaker alignment and Bluetooth latency were not verified. The phone lock screen prevented visual inspection of the new tab during this run.
- The relay shares online-track metadata and playback controls, not imported local audio files. It implements Musik rooms and does not interoperate with BeatSync rooms. No public server has been deployed.

## Musik branding and profile README (2026-11-25)
- Renamed themes, loader, widget, canvas, resource identifiers, translated resource files, and source filenames to Musik. Updated Crowdin paths.
- New listening links use `musik://`; the legacy scheme remains accepted so previously shared links still open.
- Rewrote README and contributor setup for Shaheem Aroos, linking to `https://github.com/shaheem0369`. Removed unverified release/store badges and clone links.
- Preserved real upstream/service URLs, author credits, server protocol tokens, and stored preference/artwork keys for compatibility. These are not user-facing app branding.
- Verified app and Android test APK assembly and all 30 JVM tests, including new-scheme encoding and legacy-link decoding. Device instrumentation tests were not run. Final build log: `reports/musik-branding-final-build.log`.

## Package rename (2026-11-25)
- Renamed the application ID and namespace to `com.shaheem.yt` (`com.shaheem.yt.debug` for debug builds).
- Updated package declarations, imports, resource class references, manifest classes, ProGuard rules, tests, and package directories across all source modules. Updated executable/tooling references as well.
- Android installs this package separately from the old application. Existing app-private data is not automatically transferred. Upstream copyright notices and original repository links remain accurate.
- Historical validation logs and pre-existing APKs describe the packages they were built with; the new APK is generated under `app/build/outputs/apk/universal/debug`.
- A clean rebuild is required after this rename so Hilt does not reuse generated references to the old application class. Use `./gradlew clean :app:assembleUniversalDebug :app:testUniversalDebugUnitTest :app:assembleUniversalDebugAndroidTest`.
- Verified on 2026-11-25: clean app and Android test APK builds succeeded; all 30 JVM unit tests passed. Android instrumentation tests were compiled, not run on a device. Build log: `reports/package-rename-build.log`.

## NewPipe dependency resolution (2026-11-10)
- Pinned `com.github.TeamNewPipe:NewPipeExtractor` to the published `v0.26.5` release. The moving `master-SNAPSHOT` dependency resolved to `master-6ce85c1af0-1`, whose POM returned HTTP 401 during Gradle sync. The release POM responds to HEAD with HTTP 200 without credentials.
- Keep the release pin in `gradle/libs.versions.toml`; increasing HTTP timeouts or adding credentials does not repair an unavailable public snapshot.
- Fixed the missing Material 3 `Text` import used by the Home screen greeting, which caused Kotlin compilation to fail after dependency resolution succeeded.
- Validation: `:app:assembleUniversalDebug :app:testUniversalDebugUnitTest` succeeded with JDK 21. Build output: `reports/newpipe-release-check.log`.

Base: original upstream project (HEAD 3385c13), with Liquid Glass edits and the changes below. See README for upstream attribution.

## What changed
- `ui/component/LiquidGlass.kt` – gradient-free: flat translucent fill, solid hairline rim, flat 1dp top highlight. `AuroraBackground` is now a single flat tinted colour.
- `ui/component/GlassNavigationBar.kt` – removed the gradient on the selected-tab indicator.
- `constants/PreferenceKeys.kt` – new `PlayerBackgroundStyle.GLASS`.
- `ui/player/PlayerComponents.kt` – `PlayerBackground(GLASS)`: one flat colour taken from the album art, cross-faded between tracks. `PlayerControlsContent` switches to the glass controls when Liquid Glass is on.
- `ui/player/GlassPlayerControls.kt` (new) – now-playing controls: glass like/menu buttons, flat seek bar (tap + drag, thickens while held), elapsed / stream chip (real codec + bitrate) / remaining, large transport row, glass shuffle/repeat/share row, springy presses + haptics.
- `ui/player/Player.kt`, `Thumbnail.kt`, `LyricsScreen.kt`, `AppearanceSettings.kt` – handle the new style. With Liquid Glass on, the player always uses the GLASS backdrop.

## Icon and bug fixes (2026-10-29)
- Replaced all launcher densities and the store icon with the supplied Musik logo. Adaptive icons keep the artwork within the safe area; themed icons use a matching monochrome music mark.
- Original artwork: `assets/branding/musik-logo.png`. Regenerate resized assets with `python tools/generate_launcher_icons.py` (Pillow required).
- Restored missing Gradle wrapper properties and added the default resource locale required by automatic per-app language generation. Both files are explicitly allowed through `.gitignore`.
- Glass seek gestures now use the latest callbacks after recomposition.
- Automatic queue additions run in a keyed effect instead of during composition.
- Shared-listening requests preserve the requested playback position when adding a new track; corrected the outdated test expectation to target the requested track explicitly.
- Removed unremembered fallback state from playlist suggestions, replaced an unnecessary lyrics constraint layout, fixed volume-slider indentation, and provided pre-Android-12 launcher color fallbacks.

## Validation
Build and check with JDK 21 + Android SDK 36: `./gradlew :app:assembleUniversalDebug :app:testUniversalDebugUnitTest :app:lintUniversalDebug`.
Verified on 2026-10-29: APK assembly succeeded and all 19 unit tests passed. Lint completed with 138 errors, 434 warnings, and 38 hints; the project has `abortOnError = false`, so successful task execution does not mean a clean lint report. Remaining errors concern resource lookup patterns, translated plural rules, and unused Scaffold padding. See `app/build/reports/lint-results-universalDebug.html` for details.
Device playback and gesture checks require a connected Android device or emulator.

## Black-and-white branding, player, and local music (2026-10-29)
- The launcher, header, and Settings logo now use `assets/branding/musik-logo.png`, the supplied black-and-white waveform artwork. Notification/themed icons use a corresponding monochrome mark.
- Settings shows **Application designer — Shaheem Aroos** below the app version.
- The glass player uses a full-width orange progress track, layered waveform decoration, white outlined thumb, elapsed time, and total duration to match the reference. The waveform is decorative, not an audio amplitude visualization. Tap, drag, and accessibility seek actions are supported.
- **Add local music** is available in Library and Songs. Android's file picker accepts multiple audio files; the app copies them into its private `files/local_music` directory, extracts title/artist/duration/artwork, deduplicates by SHA-256, and opens **Songs → Local** after import. Originals are left intact; no broad storage permission is required. Import failures are counted and incomplete copies are cleaned up.
- Local files bypass YouTube stream resolution and automatic recommendations. Likes remain local, redundant download actions are hidden, and remote library sync preserves imported tracks.
- Unit coverage includes duplicate imports, interrupted/empty copy cleanup, and preserving local identity when saving metadata. Device import/playback checks still require a connected phone or emulator.

## Local format error 3003 and expanded imports (2026-11-09)
- Root cause: the shared media source factory enabled only MP4/fragmented MP4/Matroska extractors. It now uses Media3's full default extractor set, including MP3, FLAC, WAV, AAC, and Ogg. Existing imported `.audio` files retain their IDs and can be recognized by content.
- The picker allows files even when a document provider supplies a generic MIME type. FFprobe validates the actual audio stream and reads tags; Android's metadata reader is optional rather than an import gate. Video documents without an attached-cover-only video stream are rejected.
- Local ALAC/M4A, AIFF, DSD (DSF/DFF), and audio requiring conversion are prepared with FFmpeg Kit Audio 6.0.1 into a local FLAC playback copy. Sources are retained. DSD is converted to 88.2 kHz PCM; this is not native DSD/DoP output. Conversion runs off the UI thread, is cancellable, and publishes the copy atomically.
- Old imports are also prepared at playback time, with a new cache key so cached original bytes cannot override a converted stream. No database migration or reimport is required for previously imported MP3 files.
- Native dependency source/license details are in `app/src/main/assets/audio-decoder-notices.txt`. Regenerate the APK and tests with `./gradlew :app:assembleUniversalDebug :app:testUniversalDebugUnitTest :app:assembleUniversalDebugAndroidTest`.
- Verified final APK on 2026-11-09: build succeeded; 30 JVM tests passed; all 8 on-device format tests passed on an isolated x86_64 Android 37.1 emulator with a 16 KB page-size image. Synthetic MP3, FLAC, ALAC/M4A, WAV (24-bit), DSF/DSD, AIFF (24-bit), AAC, and Ogg/Vorbis fixtures were imported with the legacy `.audio` suffix, decoded, seeked, and played through completion. The supplied screenshot's original song file was not available for testing. These tests do not certify every encoding variant or a physical phone/DAC. Results: `reports/audio-format-tests.txt`.

## Android Back navigation and GitHub profile (2026-11-09)
- User confirmed the failing navigation is the Android Back gesture/button, and requested the in-app GitHub profile link rather than repository/account integration.
- Bottom sheets now register Back callbacks consistently with an enabled flag. Removed the duplicate player-wide Back handler so the active sheet owns its Back action; expected order is Lyrics → Queue → full player → underlying app screen.
- About, the support prompt, the designer name in Settings, and default Discord profile links use `https://github.com/shaheem0369`. The About title is Musik and designer is Shaheem Aroos. Original project attribution remains under Inspiration; no GitHub repository URL or release API was invented from a profile URL.
- Added Android regression tests for Back key dispatch, repeated Queue open/close, Lyrics/Queue/player ordering, and the system edge-back gesture.
- Verified: APK assembly and 30 JVM tests passed; all four Back-navigation instrumentation tests passed on the isolated Android emulator, including a real right-edge swipe with gesture navigation enabled. Results are saved in `reports/back-navigation-tests.txt`. The emulator required `hw.mainKeys=no` to expose software navigation, and its OS native-library compatibility notice was dismissed before input tests; this run does not certify 16 KB native-library alignment or Samsung-specific behavior.
Existing gradient scrims elsewhere in the app (fading list edges, Home background blobs when blur is on) are from the original code and were left alone.

## Rename
The launcher label, in-app text, store titles, widget, and export names use Musik. The current package is `com.shaheem.yt`; source classes and themes use names such as `MusikLoader` and `Theme.MusikApp`. Compatibility keys and real upstream/service addresses are retained where required.

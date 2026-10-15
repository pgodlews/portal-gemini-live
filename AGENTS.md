# Agent instructions

These instructions apply to the entire repository. Read `README.md`, `SECURITY.md`, and `docs/ARCHITECTURE.md` before changing application behavior.

## Project

Portal Gemini Live is an unofficial, sideloaded Android client for Gemini Live, developed for Meta Portal+ Gen 2. It uses Kotlin, Jetpack Compose, Camera2, Android audio APIs, OkHttp WebSockets, and coroutines. The application ID is `dev.portalgemini`.

Keep changes focused on the requested task. Follow the surrounding Kotlin and Compose style. Avoid unrelated dependency upgrades, broad refactors, or changes to device behavior.

## Build and validation

Use JDK 17 and the included Gradle wrapper. Configure the Android SDK through `ANDROID_HOME` or ignored `local.properties`; never commit machine-specific SDK or JDK paths.

```bash
./gradlew assembleDebug lintDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. Building does not require a Gemini API key or a connected device. Compilation uses API 35; minimum API is 28 and target API is 29. The older target supports the Portal deployment; its Google Play policy lint finding is intentionally a warning. Do not raise the target SDK merely to silence that warning.

For documentation-only changes, check formatting and referenced paths; rebuilding the app is unnecessary. For application changes, run build and lint, and add focused regression coverage when practical. Do not describe a build as a hardware or live API test.

`scripts/install.sh` builds, installs, and launches the app on a connected device. Use it only when installation is within the task scope. Check `adb devices` first and select a device explicitly if several are connected. Hardware validation should cover the changed behavior, such as audio playback and interruption, camera preview and transmission, settings persistence, or QR setup/export. Report any checks that could not be performed.

## Code map

Source lives in `app/src/main/java/dev/portalgemini/`:

- `MainActivity.kt`: permissions, preferences, session orchestration, and UI state.
- `api/GeminiLiveClient.kt`: WebSocket lifecycle, protocol messages, transcripts, and callbacks.
- `audio/`: microphone capture, playback, mute gating, and interruption handling.
- `camera/`: Camera2 preview, JPEG encoding, and frame pacing.
- `server/PortalHttpServer.kt`: temporary local setup and conversation export server.
- `telemetry/`: network and session metrics, including approximate cost estimates.
- `ui/`: Compose screens, settings, history, QR dialogs, and visualizers.
- `util/`: conversation export and network helpers.

Use Camera2 for the existing capture pipeline; CameraX dependencies in the build do not mean CameraX is the active implementation. Preserve the distinction between local capture/preview and network transmission when changing mute or camera controls. Do not infer current Gemini model availability or pricing from hardcoded defaults or estimates.

## Credentials and privacy

- Keys must be supplied at runtime. Never embed real credentials in source, examples, tests, commands, screenshots, or documentation.
- Do not print credential-bearing Git URLs, environment dumps, app preference files, or raw device logs. Inspect only the configuration needed for the task and redact sensitive values before reporting.
- Do not log API keys or fragments, setup/export tokens, prompts, transcripts, raw protocol payloads, server response bodies, or exceptions that may contain authenticated URLs.
- Preserve disabled Android app backups and the local server's random access token, no-cache headers, and referrer policy.
- Local QR setup/export uses unencrypted HTTP. Keep the trusted-network limitation clear; do not describe a token-protected link as encrypted transport.
- Keep generated APKs, signing material, local configuration, device captures, and personal exports out of Git. Ignore rules do not protect files already staged or tracked.
- Only explicitly reviewed screenshots belong in `docs/images/`. The two existing README screenshots were approved for publication. Review every new capture for credentials, QR links, conversations, and personal surroundings before adding it.

Before release, review the actual staged or committed files and run a secret scan with redacted output. Automated scans do not replace visual review of images. Do not introduce private development history into a prepared public snapshot.

## Documentation and licensing

Update relevant documentation when changing setup, configuration, privacy, or user-visible behavior. Use portable paths and clearly marked placeholder values. Keep README image links relative to the repository.

Preserve the MIT license, Piotr Godlewski's copyright notice, and third-party license notices. Summarize changes, validation performed, and material limitations in the handoff.

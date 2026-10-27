# VocaGate / 麦哨

**Open mic is noisy. Holding push-to-talk is tiring. Let your camera press the key for you.**

[中文首页](README.md) · [Downloads](https://github.com/klamo/VocaGate/releases) · [Report an issue](https://github.com/klamo/VocaGate/issues) · [MIT License](LICENSE)

VocaGate watches mouth movement locally and holds a configurable push-to-talk (PTT) key on your computer while you speak. Your game or voice app continues to use its existing microphone. VocaGate does not provide a virtual microphone, speech transcription, or audio denoising.

## Two ways to use it

| Mode | Recognition | Setup |
| --- | --- | --- |
| Android phone | CameraX + ML Kit on the phone; only gate events go to the computer | Android app + desktop companion, manually paired over local Wi-Fi or connected through ADB reverse |
| Computer camera | OpenCV capture + MediaPipe Face Landmarker sidecar on the computer | Desktop companion + built-in or USB camera |

Phone mode does **not** stream the phone microphone into your computer.

## Download and start

Get the files actually available on the [Releases page](https://github.com/klamo/VocaGate/releases).

- **Android 6.0+:** install the release APK. A debug-signed installation must be uninstalled before installing a release APK; this deletes its settings and test recordings.
- **macOS Apple Silicon:** withheld from this first preview because the sidecar health check failed in the current environment.
- **Windows x64:** fully extract the portable ZIP and run `VocaGate.exe` inside its folder.

Desktop release packages bundle Java, MediaPipe and the model. No separate Java or Python installation is needed. USB mode still requires system ADB and Android USB-debugging authorization. Desktop packages do not have publisher signing/notarization and may trigger OS warnings.

1. Select the phone or a local camera as the input source.
2. Set the same PTT key in VocaGate and your game or voice app.
3. Start VocaGate, speak, and verify both key press and release in the target app before relying on it.

See the Chinese [quick-start guide](docs/QUICK_START.md) for connection and troubleshooting steps.

## Early-preview status

The first `0.2.0` binaries were built in July 2026. The `main` branch contains later work, including Android audio-assisted visual gating. **Do not assume those binaries contain every current source feature.** The [release notes](docs/RELEASE_0.2.0_PREVIEW.md) record artifact provenance and verification limits. Sidecar health checks and bundled-Java checks are not full GUI, camera, or game-PTT acceptance.

Current source includes early key press, continuous-speech hold, Android recording comparisons, tuning controls and a screen shield. Audio assistance requires visual mouth-motion evidence; sound alone cannot open the gate. Android currently requires both camera and microphone permission at startup.

Manual local-network pairing and system-ADB USB connections exist. Automatic discovery, QR pairing, bundled ADB and a Windows EXE installer are not yet delivered.

## Data and updates

Recognition stays local. Android recording tests save audio locally. Optional update checks contact the configured server; the existing anonymous-statistics switch controls platform/version/channel query parameters, not a complete analytics service. The default update callers do not yet configure a mandatory signature-verification public key. For an initial trial, disable algorithm updates and use the bundled algorithm.

## Development and license

Start with [development instructions](docs/DEVELOPMENT.md), [release packaging](docs/RELEASE_PACKAGING.md), and [contribution guidance](CONTRIBUTING.md). The shared state machine lives in `gate-core`; changes require Android and desktop regression checks.

VocaGate's own source code is licensed under the [MIT License](LICENSE). Third-party dependencies, models and external media retain their own licenses.

# VocaGate {{version}} Release Notes

## Platform artifacts

- Android: `VocaGate-{{version}}-android.apk`
- Android publishing verifies the configured release certificate SHA-256 fingerprint.
- macOS Apple Silicon: `VocaGate-{{version}}-macos-arm64.dmg`
- Windows x64 portable build: `VocaGate-{{version}}-windows-x64.zip`

## Debug-to-release Android migration

The release APK uses a different signing identity from debug builds. Uninstall the debug app before installing the release APK. This migration removes local app data and settings.

## Desktop pre-release signing

Desktop artifacts are self-contained pre-release packages with bundled Java and MediaPipe runtimes, but they are not code signed yet. macOS may require an explicit first-open confirmation; Windows may show a SmartScreen warning. Download only from the official GitHub Release and verify `SHA256SUMS.txt` before use.

## Wi-Fi/USB limits

USB debugging and local Wi-Fi connectivity remain environment-dependent. Wi-Fi setup does not replace USB access for initial device authorization or recovery.

## Model/sidecar changes

Document bundled model and desktop sidecar changes here. Remote model downloads are not included in this release.

## Telemetry

Telemetry is not included in this release.

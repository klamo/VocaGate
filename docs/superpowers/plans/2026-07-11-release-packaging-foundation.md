# VocaGate Release Packaging Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce a signed Android release APK and self-contained macOS ARM/Windows x64 desktop installers that bundle Java, MediaPipe and the fixed recognition model without requiring users to install Java, Python or pip.

**Architecture:** Keep release metadata and signing inputs outside source control, generate platform-specific desktop staging directories, then use `jlink` and `jpackage` on the target operating system. Desktop recognition resolves a packaged native MediaPipe sidecar first, retaining existing Python/script discovery only for source-tree development. This plan deliberately leaves automatic Wi-Fi pairing and bundled ADB transport to the dependent connection plan.

**Tech Stack:** Gradle Kotlin DSL, Android Gradle Plugin, Java 21 `jlink`/`jpackage`, PyInstaller `--onedir`, MediaPipe Python, SHA-256, shell and batch release scripts.

---

## Scope and Sequencing

This is Plan A of two. It produces installable releases that retain the existing manually configured Wi-Fi and existing system-ADB USB behavior. Plan B, `2026-07-11-zero-config-connection.md`, adds mDNS/QR pairing and the audited bundled ADB runtime after the installer staging layout exists.

Do not add Telemetry, CloudBase, account, payment, automatic app updates or remote model downloads in either plan.

## File Structure

| File | Responsibility |
|---|---|
| `.gitignore` | Ignore release keys, signing properties, staging output and generated binaries. |
| `gradle.properties` | Single public version name and version code used by Android and desktop packaging. |
| `app/build.gradle.kts` | Android release signing configuration and universal release APK task. |
| `gate-core/src/main/java/com/vocagate/core/update/UpdatePreferences.java` | Remove the misleading pre-Telemetry anonymous-statistics preference. |
| `gate-core/src/main/java/com/vocagate/core/update/UpdateClientConfig.java` | Remove the pre-Telemetry anonymous-statistics flag from update checks. |
| `gate-core/src/main/java/com/vocagate/core/update/UpdateCheckClient.java` | Never append platform/version query fields under an “anonymous statistics” label. |
| `app/src/main/java/com/vocagate/demo/UpdateSettingsActivity.java` | Show algorithm-update controls only; remove the nonexistent statistics toggle. |
| `desktop/src/main/java/com/vocagate/desktop/app/DesktopUpdatePreferences.java` | Persist algorithm-update settings without a telemetry field. |
| `desktop/src/main/java/com/vocagate/desktop/app/DesktopCompanionApp.java` | Remove the telemetry checkbox from the desktop update panel. |
| `desktop/src/main/python/requirements-release.txt` | Exact, audited build-time Python dependencies for the sidecar bundle. |
| `desktop/src/main/python/models/face_landmarker.lock.json` | Versioned source URL, SHA-256 and license metadata for the package model. |
| `desktop/src/main/python/vocagate_mediapipe_sidecar.py` | Add deterministic `--healthcheck` that loads the packaged model without entering the frame loop. |
| `desktop/src/main/java/com/vocagate/desktop/mediapipe/DesktopSidecarLaunchSpec.java` | Resolve development overrides and installed sidecar executable paths without mixing the two modes. |
| `desktop/src/main/java/com/vocagate/desktop/mediapipe/MediaPipeFaceLandmarkerFrameAnalyzer.java` | Launch the resolved packaged sidecar and expose its health-check errors cleanly. |
| `desktop/src/test/java/com/vocagate/desktop/mediapipe/DesktopSidecarLaunchSpecTest.java` | Unit-test installed versus development path selection. |
| `desktop/build.gradle.kts` | Add test support, platform-specific native dependency selection and release staging tasks. |
| `scripts/package_android_release.sh` | Validate local signing inputs, create the Android release file and verify its certificate. |
| `scripts/prepare_mediapipe_sidecar.sh` | Fetch the lock-file model, validate SHA-256, build the native sidecar staging directory and emit notices. |
| `scripts/package_macos_arm64.sh` | Build macOS ARM staging, jlink runtime and DMG on macOS ARM. |
| `scripts/package_windows_x64.bat` | Build Windows x64 staging, jlink runtime and EXE on Windows. |
| `scripts/verify_release_artifacts.sh` / `.bat` | Verify the installer that can be inspected on the current host: name, SHA-256, bundled runtime, sidecar, model, notices and no development-only files. |
| `scripts/aggregate_release_artifacts.sh` | After Mac, Windows and Android outputs are copied into one directory, require the complete release set and generate final checksums. |
| `scripts/prepare_release_notes.sh` / `.bat` | Materialize `release/<version>/RELEASE_NOTES.md` from the committed template on its build host and reject unresolved placeholders. |
| `docs/release-notes-template.md` | Required release-note structure; prevents an artifact set without migration and pre-release disclosures. |
| `desktop/src/main/java/com/vocagate/desktop/app/DesktopPermissionGuide.java` | Platform-specific first-run guidance for camera, macOS Accessibility and Windows elevated target apps. |
| `desktop/src/test/java/com/vocagate/desktop/app/DesktopPermissionGuideTest.java` | Verify first-run messages describe the correct platform boundary. |
| `docs/RELEASE_PACKAGING.md` | Maintainer-only release prerequisites and reproducible build/verification commands. |
| `README.md`, `readme-zh.md` | Replace development-package instructions with end-user release installation instructions and preserve source-build guidance separately. |
| `tools/ReleasePackagingSmokeTest.java` | Validate release metadata, model lock parsing and version propagation without a native installer host. |

## Task 1: Remove the Misleading Pre-Telemetry Statistics Toggle

**Files:**
- Modify: `gate-core/src/main/java/com/vocagate/core/update/UpdatePreferences.java`
- Modify: `gate-core/src/main/java/com/vocagate/core/update/UpdateClientConfig.java`
- Modify: `gate-core/src/main/java/com/vocagate/core/update/UpdateCheckClient.java`
- Modify: `app/src/main/java/com/vocagate/demo/UpdateSettingsActivity.java`
- Modify: `app/src/main/java/com/vocagate/demo/MainActivity.java`
- Modify: `desktop/src/main/java/com/vocagate/desktop/app/DesktopUpdatePreferences.java`
- Modify: `desktop/src/main/java/com/vocagate/desktop/app/DesktopCompanionApp.java`
- Modify: `tools/UpdateClientSmokeTest.java`
- Test: `tools/UpdateClientSmokeTest.java`

- [ ] **Step 1: Extend the update smoke test with the public-release contract**

Add an assertion that the manifest request remains exactly `<server>/v1/manifest.json` and does not add `platform`, `app_version`, `channel`, `install_id`, or any telemetry query string.

```java
UpdateClientConfig config = new UpdateClientConfig(
        "https://updates.example.test",
        "android",
        "0.2.0",
        "stable",
        "android-mlkit-v1",
        true
);
assertEquals("https://updates.example.test/v1/manifest.json", UpdateCheckClient.manifestUrl(config));
```

- [ ] **Step 2: Run the smoke test to verify it fails**

Run:

```bash
mkdir -p /tmp/vocagate-update-client-smoke
javac -d /tmp/vocagate-update-client-smoke -cp gate-core/src/main/java tools/UpdateClientSmokeTest.java
java -cp /tmp/vocagate-update-client-smoke:gate-core/src/main/java UpdateClientSmokeTest
```

Expected: fail because the current client configuration still requires the statistics flag or appends update metadata when that toggle is enabled.

- [ ] **Step 3: Remove the statistics field from the update protocol settings**

Change the constructor shape to keep only update-check settings:

```java
public UpdateClientConfig(
        String updateServerUrl,
        String platform,
        String appVersion,
        String channel,
        String engine,
        boolean updateChecksEnabled
) { ... }
```

Make `UpdateCheckClient.manifestUrl()` return only `config.updateServerUrl() + "/v1/manifest.json"`. Remove stale preference keys and UI controls rather than preserving a toggle that does not control an implemented feature. Continue reading legacy preference keys only if needed to avoid crashing existing users; do not render them.

- [ ] **Step 4: Update Android and desktop callers**

Update both update settings surfaces to show only:

```text
检查更新
更新服务器 URL
立即检查算法更新
```

Do not create a replacement Telemetry UI in this task.

- [ ] **Step 5: Run the smoke test and the app compilation checks**

Run:

```bash
mkdir -p /tmp/vocagate-update-client-smoke
javac -d /tmp/vocagate-update-client-smoke -cp gate-core/src/main/java tools/UpdateClientSmokeTest.java
java -cp /tmp/vocagate-update-client-smoke:gate-core/src/main/java UpdateClientSmokeTest
gradle :gate-core:compileJava :app:compileDebugJavaWithJavac :desktop:compileJava
```

Expected: `UpdateClientSmokeTest passed` and all three Gradle compilation tasks succeed.

- [ ] **Step 6: Commit**

```bash
git add gate-core/src/main/java/com/vocagate/core/update/UpdatePreferences.java gate-core/src/main/java/com/vocagate/core/update/UpdateClientConfig.java gate-core/src/main/java/com/vocagate/core/update/UpdateCheckClient.java app/src/main/java/com/vocagate/demo/UpdateSettingsActivity.java app/src/main/java/com/vocagate/demo/MainActivity.java desktop/src/main/java/com/vocagate/desktop/app/DesktopUpdatePreferences.java desktop/src/main/java/com/vocagate/desktop/app/DesktopCompanionApp.java tools/UpdateClientSmokeTest.java
git commit -m "fix: remove placeholder usage statistics toggle"
```

## Task 2: Establish Version, Release-Secret and Output Boundaries

**Files:**
- Modify: `.gitignore`
- Modify: `gradle.properties`
- Modify: `app/build.gradle.kts`
- Create: `scripts/package_android_release.sh`
- Create: `scripts/prepare_release_notes.sh`
- Create: `scripts/prepare_release_notes.bat`
- Create: `docs/release-notes-template.md`
- Create: `tools/ReleasePackagingSmokeTest.java`
- Test: `tools/ReleasePackagingSmokeTest.java`

- [ ] **Step 1: Write the failing release-metadata smoke test**

Parse `gradle.properties` as Java properties and require a non-empty public version and positive version code.

```java
Properties properties = new Properties();
try (Reader reader = Files.newBufferedReader(Paths.get("gradle.properties"))) {
    properties.load(reader);
}
assertTrue(properties.getProperty("vocagate.version").matches("\\d+\\.\\d+\\.\\d+"));
assertTrue(Integer.parseInt(properties.getProperty("vocagate.versionCode")) > 0);
```

- [ ] **Step 2: Run the smoke test to verify it fails**

Run:

```bash
mkdir -p /tmp/vocagate-release-smoke
javac -d /tmp/vocagate-release-smoke tools/ReleasePackagingSmokeTest.java
java -cp /tmp/vocagate-release-smoke ReleasePackagingSmokeTest
```

Expected: fail because public release metadata does not exist yet.

- [ ] **Step 3: Add public version metadata and ignore private inputs**

Add to `gradle.properties`:

```properties
vocagate.version=0.2.0
vocagate.versionCode=2
```

Add these ignore rules without ignoring source metadata or release notes:

```gitignore
release/
release-signing.properties
*.jks
*.keystore
desktop/build/release-staging/
desktop/build/sidecar/
```

Use a local `release-signing.properties` with exactly `storeFile`, `storePassword`, `keyAlias`, and `keyPassword`; never add it to Git.

Create `docs/release-notes-template.md`, `scripts/prepare_release_notes.sh` and `scripts/prepare_release_notes.bat`. Each host-local helper must create `release/<version>/RELEASE_NOTES.md` from the template and refuse to finish if any `{{placeholder}}` remains. The template must include: supported platform files, debug-to-release Android migration, desktop pre-release signing warnings, known Wi-Fi/USB limits, model/sidecar changes and a statement that Telemetry is not yet included. The `.bat` implementation must use only Windows-native commands and must not rely on a file generated on macOS because `release/` is ignored.

- [ ] **Step 4: Configure Android release signing without debug fallback**

Load the local signing file only when a release task is requested. The release script must refuse to run before Gradle if the file or any key is absent. Configure `versionCode` and `versionName` from the root properties and configure `buildTypes.release` with the release signing config. Do not make debug builds depend on the private file.

`scripts/package_android_release.sh` must:

1. require JDK, Android SDK and `release-signing.properties`;
2. run `gradle :app:assembleRelease`;
3. copy the result to `release/<version>/VocaGate-<version>-android.apk`;
4. run `apksigner verify --verbose --print-certs` against the copied APK;
5. reject a debug certificate subject or an unexpected release certificate SHA-256 supplied by the signing properties.

- [ ] **Step 5: Make the smoke test pass and build the unsigned development path**

Run:

```bash
mkdir -p /tmp/vocagate-release-smoke
javac -d /tmp/vocagate-release-smoke tools/ReleasePackagingSmokeTest.java
java -cp /tmp/vocagate-release-smoke ReleasePackagingSmokeTest
gradle :app:assembleDebug
```

Expected: metadata smoke passes and debug builds still work without release credentials.

- [ ] **Step 6: Commit**

```bash
git add .gitignore gradle.properties app/build.gradle.kts scripts/package_android_release.sh scripts/prepare_release_notes.sh scripts/prepare_release_notes.bat docs/release-notes-template.md tools/ReleasePackagingSmokeTest.java
git commit -m "build: add Android release packaging foundation"
```

## Task 3: Lock and Stage the MediaPipe Model

**Files:**
- Create: `desktop/src/main/python/requirements-release.txt`
- Create: `desktop/src/main/python/models/face_landmarker.lock.json`
- Create: `scripts/prepare_mediapipe_sidecar.sh`
- Create: `scripts/prepare_mediapipe_sidecar.bat`
- Modify: `desktop/src/main/python/vocagate_mediapipe_sidecar.py`
- Modify: `.gitignore`
- Test: `tools/ReleasePackagingSmokeTest.java`

- [ ] **Step 1: Extend the release smoke test for the model lock**

Require a JSON object with concrete `model_id`, `version`, `url`, `sha256`, and `license_source` fields. Reject `latest` URLs and hashes that are not 64 lowercase hex characters.

```java
assertFalse(lock.get("url").toString().contains("/latest/"));
assertTrue(lock.get("sha256").toString().matches("[0-9a-f]{64}"));
```

- [ ] **Step 2: Run the smoke test to verify it fails**

Run the Task 2 smoke command. Expected: fail because no lock file exists.

- [ ] **Step 3: Create exact build inputs**

Create `requirements-release.txt` using exact versions verified on each target host, not range constraints. Start from the versions currently validated by the repository, then record the final tested values, for example:

```text
mediapipe==0.10.35
numpy==1.26.4
pyinstaller==6.16.0
```

Create the lock file only after downloading a concrete model release and calculating its hash. The committed file must have this complete shape, with actual values rather than placeholders:

```json
{
  "model_id": "face_landmarker",
  "version": "<concrete-upstream-version>",
  "url": "https://.../face_landmarker.task",
  "sha256": "<64-lowercase-hex>",
  "license_source": "<upstream-license-or-model-card-url>"
}
```

Do not commit the model binary; keep the existing `*.task` ignore rule.

- [ ] **Step 4: Implement deterministic staging and sidecar health check**

`prepare_mediapipe_sidecar.*` must read the committed lock, download only during the build, calculate SHA-256 and fail on a mismatch. Then create a target-specific PyInstaller `--onedir` bundle named `vocagate-mediapipe` under `desktop/build/sidecar/<target>/` and copy the verified model beside it.

Add `--healthcheck` to the Python sidecar. It must resolve the model, construct `FaceLandmarker`, print one machine-readable line, close it and exit zero:

```python
if args.healthcheck:
    with FaceLandmarker.create_from_options(options):
        print(json.dumps({"type": "health", "status": "ok"}), flush=True)
    return 0
```

The existing streaming protocol remains unchanged when `--healthcheck` is absent.

- [ ] **Step 5: Run lock and health checks**

Run on the target build host:

```bash
scripts/prepare_mediapipe_sidecar.sh macos-arm64
desktop/build/sidecar/macos-arm64/vocagate-mediapipe/vocagate-mediapipe --model desktop/build/sidecar/macos-arm64/models/face_landmarker.task --healthcheck
```

Expected: JSON containing `"type": "health"` and `"status": "ok"`; no network request occurs during the second command.

- [ ] **Step 6: Commit**

```bash
git add desktop/src/main/python/requirements-release.txt desktop/src/main/python/models/face_landmarker.lock.json desktop/src/main/python/vocagate_mediapipe_sidecar.py scripts/prepare_mediapipe_sidecar.sh scripts/prepare_mediapipe_sidecar.bat tools/ReleasePackagingSmokeTest.java .gitignore
git commit -m "build: bundle locked MediaPipe sidecar inputs"
```

## Task 4: Resolve Installed Sidecars Without Breaking Development Mode

**Files:**
- Create: `desktop/src/main/java/com/vocagate/desktop/mediapipe/DesktopSidecarLaunchSpec.java`
- Modify: `desktop/src/main/java/com/vocagate/desktop/mediapipe/MediaPipeFaceLandmarkerFrameAnalyzer.java`
- Modify: `desktop/build.gradle.kts`
- Create: `desktop/src/test/java/com/vocagate/desktop/mediapipe/DesktopSidecarLaunchSpecTest.java`
- Test: `desktop/src/test/java/com/vocagate/desktop/mediapipe/DesktopSidecarLaunchSpecTest.java`

- [ ] **Step 1: Add a failing path-resolution test**

Cover these cases using temporary directories only:

```java
assertEquals(List.of("/tmp/app/sidecar/vocagate-mediapipe", "--model", "/tmp/app/sidecar/models/face_landmarker.task"),
        DesktopSidecarLaunchSpec.resolve(installedAppHome, Map.of(), "Mac OS X").command());
assertTrue(DesktopSidecarLaunchSpec.resolve(workspace, Map.of("VOCAGATE_PYTHON", "/custom/python"), "Mac OS X")
        .command().contains("/custom/python"));
```

Add JUnit Jupiter or the repository’s existing JUnit version to the desktop test configuration and configure `useJUnitPlatform()` only if Jupiter is selected.

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
gradle :desktop:test --tests com.vocagate.desktop.mediapipe.DesktopSidecarLaunchSpecTest
```

Expected: compilation failure because `DesktopSidecarLaunchSpec` does not exist.

- [ ] **Step 3: Add a focused launch-spec value object**

The resolver priority must be:

1. explicit development overrides `VOCAGATE_MEDIAPIPE_SIDECAR` and `VOCAGATE_MEDIAPIPE_MODEL`;
2. installed executable `<appHome>/sidecar/vocagate-mediapipe[.exe]` plus `<appHome>/sidecar/models/face_landmarker.task`;
3. source-tree Python script plus local venv/Python only when the installed executable does not exist.

Use a value object so `MediaPipeFaceLandmarkerFrameAnalyzer` receives an immutable command and diagnostic description rather than mixing path discovery with process I/O.

- [ ] **Step 4: Add packaged health-check handling**

Before desktop local-camera mode is enabled, execute the resolved command with `--healthcheck` and a bounded timeout. Parse only the JSON success record. On failure, preserve phone mode, keep the gate closed and report a short reason such as `识别引擎不完整` or `识别模型校验失败`.

- [ ] **Step 5: Run tests and current desktop compilation**

Run:

```bash
gradle :desktop:test --tests com.vocagate.desktop.mediapipe.DesktopSidecarLaunchSpecTest
gradle :desktop:compileJava
```

Expected: all tests pass; the source-tree `scripts/run_desktop.sh` path still works when an installed sidecar is absent.

- [ ] **Step 6: Commit**

```bash
git add desktop/build.gradle.kts desktop/src/main/java/com/vocagate/desktop/mediapipe/DesktopSidecarLaunchSpec.java desktop/src/main/java/com/vocagate/desktop/mediapipe/MediaPipeFaceLandmarkerFrameAnalyzer.java desktop/src/test/java/com/vocagate/desktop/mediapipe/DesktopSidecarLaunchSpecTest.java
git commit -m "feat: resolve packaged MediaPipe sidecar"
```

## Task 5: Add First-Run Permission Guidance

**Files:**
- Create: `desktop/src/main/java/com/vocagate/desktop/app/DesktopPermissionGuide.java`
- Create: `desktop/src/test/java/com/vocagate/desktop/app/DesktopPermissionGuideTest.java`
- Modify: `desktop/src/main/java/com/vocagate/desktop/app/DesktopCompanionApp.java`
- Modify: `desktop/src/main/java/com/vocagate/desktop/core/RobotKeyActuator.java`
- Test: `desktop/src/test/java/com/vocagate/desktop/app/DesktopPermissionGuideTest.java`

- [ ] **Step 1: Write failing platform-guidance tests**

Test that the guide produces the expected non-technical path for each supported platform:

```java
assertTrue(DesktopPermissionGuide.forOs("Mac OS X").cameraText().contains("摄像头"));
assertTrue(DesktopPermissionGuide.forOs("Mac OS X").keyboardText().contains("辅助功能"));
assertTrue(DesktopPermissionGuide.forOs("Windows 11").keyboardText().contains("管理员"));
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
gradle :desktop:test --tests com.vocagate.desktop.app.DesktopPermissionGuideTest
```

Expected: compilation failure because the guide does not exist.

- [ ] **Step 3: Implement permission-aware first-run behavior**

Create a small immutable guidance object instead of embedding OS strings throughout the Swing view. Wire it into the app so that:

1. selecting a local camera requests the normal macOS camera permission through actual capture and displays the guide only if it is denied;
2. failure to initialize or use `RobotKeyActuator` on macOS offers the System Settings Accessibility path and keeps the gate safely blocked;
3. Windows displays a one-time explanation that VocaGate must run with matching elevation when the target game is elevated; it must never silently elevate itself;
4. the app persists only whether a guide was dismissed, never whether system permission was granted.

- [ ] **Step 4: Run tests and desktop compilation**

Run:

```bash
gradle :desktop:test --tests com.vocagate.desktop.app.DesktopPermissionGuideTest
gradle :desktop:compileJava
```

Expected: test passes, and permission guidance does not block phone-camera mode.

- [ ] **Step 5: Commit**

```bash
git add desktop/src/main/java/com/vocagate/desktop/app/DesktopPermissionGuide.java desktop/src/test/java/com/vocagate/desktop/app/DesktopPermissionGuideTest.java desktop/src/main/java/com/vocagate/desktop/app/DesktopCompanionApp.java desktop/src/main/java/com/vocagate/desktop/core/RobotKeyActuator.java
git commit -m "feat: guide desktop permissions on first run"
```

## Task 6: Add Target-Platform Desktop Staging and Native Dependency Selection

**Files:**
- Modify: `desktop/build.gradle.kts`
- Create: `desktop/src/main/resources/packaging/THIRD_PARTY_NOTICES.txt`
- Create: `desktop/src/main/resources/packaging/macos/Info.plist`
- Create: `tools/test_desktop_release_staging.sh`
- Test: `tools/test_desktop_release_staging.sh`

- [ ] **Step 1: Write a failing staging inspection test**

Create a shell smoke test that accepts `macos-arm64` or `windows-x64`, then requires:

```text
app/lib/
app/sidecar/
app/sidecar/models/face_landmarker.task
app/THIRD_PARTY_NOTICES.txt
```

It must reject `.venv-mediapipe`, `pip`, `setup_mediapipe_sidecar.*`, unrelated platform native archives, and any file matching `*.jks` or `*signing*`.

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
bash tools/test_desktop_release_staging.sh macos-arm64 desktop/build/release-staging/macos-arm64
```

Expected: fail because no staging task or directory exists.

- [ ] **Step 3: Make Gradle choose one native dependency set**

Require `-PtargetPlatform=macos-arm64` or `-PtargetPlatform=windows-x64` for release staging. Map each value to only its JavaCPP/OpenCV/OpenBLAS runtime classifiers; fail for an unsupported target instead of silently bundling all platform classifiers.

Add a `stageReleaseApp` task that depends on `installDist`, copies the target-specific Java distribution, verified sidecar bundle, model and notices into `desktop/build/release-staging/<target>/app`.

Use the resource `Info.plist` to define the app’s display name, package identifier and `NSCameraUsageDescription` without copying Java or Python paths into the app metadata.

- [ ] **Step 4: Run staging validation**

Run on the Mac ARM build host:

```bash
gradle :desktop:stageReleaseApp -PtargetPlatform=macos-arm64
bash tools/test_desktop_release_staging.sh macos-arm64 desktop/build/release-staging/macos-arm64
```

Expected: staging inspection passes and only macOS ARM native content is present.

- [ ] **Step 5: Commit**

```bash
git add desktop/build.gradle.kts desktop/src/main/resources/packaging/THIRD_PARTY_NOTICES.txt desktop/src/main/resources/packaging/macos/Info.plist tools/test_desktop_release_staging.sh
git commit -m "build: stage platform specific desktop release"
```

## Task 7: Produce Native Desktop Installers

**Files:**
- Create: `scripts/package_macos_arm64.sh`
- Create: `scripts/package_windows_x64.bat`
- Create: `scripts/verify_release_artifacts.sh`
- Create: `scripts/verify_release_artifacts.bat`
- Create: `scripts/aggregate_release_artifacts.sh`
- Modify: `desktop/build.gradle.kts`
- Test: `scripts/verify_release_artifacts.sh`, `scripts/verify_release_artifacts.bat`

- [ ] **Step 1: Write failing artifact checks**

Each host verifier must reject the matching platform candidate without the required platform file:

```text
VocaGate-<version>-macos-arm64.dmg    (macOS verifier)
VocaGate-<version>-windows-x64.exe    (Windows verifier)
VocaGate-<version>-android.apk        (Android release check)
RELEASE_NOTES.md                      (all verifiers)
```

It must also mount or expand the current host artifact in a temporary directory and call the staged sidecar `--healthcheck` where the host platform matches. `aggregate_release_artifacts.sh` is the only command allowed to require all three installers; it runs after the Windows EXE and macOS DMG have been copied into the same `release/<version>/` directory.

- [ ] **Step 2: Run the verifier to verify it fails**

Run:

```bash
scripts/prepare_release_notes.sh 0.2.0
scripts/verify_release_artifacts.sh --platform macos-arm64 release/0.2.0
```

Expected: fail with the missing macOS DMG or required release notes, without requiring a Windows EXE on macOS.

- [ ] **Step 3: Implement native package scripts**

`package_macos_arm64.sh` must require macOS ARM, JDK 21, PyInstaller and `jpackage`; then stage `macos-arm64`, create a jlink runtime image, and invoke `jpackage --type dmg` with the staged app contents.

`package_windows_x64.bat` must require Windows x64, JDK 21, PyInstaller, `jpackage` and the Windows installer backend required by the installed JDK; then stage `windows-x64`, create its runtime image, and invoke `jpackage --type exe`.

Both scripts must copy final artifacts into `release/<version>/` and never use a cross-platform artifact from the other host. Before its verifier runs, macOS invokes `scripts/prepare_release_notes.sh <version>` and Windows invokes `scripts\\prepare_release_notes.bat <version>`; each produces the same validated release-note content locally. The per-host verifier records a provisional checksum for the artifact it can inspect. The artifact-handoff operator copies only installers, provisional checksum files and the validated `RELEASE_NOTES.md` into the final aggregation directory. The aggregator writes final `SHA256SUMS.txt` only after all three artifacts and `RELEASE_NOTES.md` exist.

- [ ] **Step 4: Verify artifacts on target hosts**

Run on macOS ARM:

```bash
scripts/package_macos_arm64.sh
scripts/prepare_release_notes.sh 0.2.0
scripts/verify_release_artifacts.sh --platform macos-arm64 release/0.2.0
```

Run on Windows x64:

```bat
scripts\package_windows_x64.bat
scripts\prepare_release_notes.bat 0.2.0
scripts\verify_release_artifacts.bat --platform windows-x64 release\0.2.0
```

Expected: each target produces only its own installer and the matching host verifier passes. After the Windows EXE has been copied into the same release directory, run `scripts/aggregate_release_artifacts.sh release/0.2.0` on macOS or Linux to produce final checksums.

- [ ] **Step 5: Commit**

```bash
git add scripts/package_macos_arm64.sh scripts/package_windows_x64.bat scripts/prepare_release_notes.sh scripts/prepare_release_notes.bat scripts/verify_release_artifacts.sh scripts/verify_release_artifacts.bat scripts/aggregate_release_artifacts.sh desktop/build.gradle.kts docs/release-notes-template.md
git commit -m "build: package native desktop installers"
```

## Task 8: Update Public and Maintainer Documentation

**Files:**
- Create: `docs/RELEASE_PACKAGING.md`
- Modify: `README.md`
- Modify: `readme-zh.md`
- Modify: `docs/UPDATE_SERVER.md`
- Test: `tools/test_desktop_distribution_permissions.sh`

- [ ] **Step 1: Write documentation assertions into the existing distribution smoke script**

Add checks that source-build helper scripts remain executable for developers but are not referenced by the public installer sections as a prerequisite.

- [ ] **Step 2: Update maintainer documentation**

Document exact build hosts, private signing file shape, model lock update procedure, JDK/PyInstaller/jpackage prerequisites, release artifact verification, pre-release signing warnings, and rollback to the prior GitHub Release artifact.

- [ ] **Step 3: Replace public installation instructions**

For both languages:

- Android: download the Release APK, allow the chosen file source to install it, grant camera permission.
- macOS: open DMG, drag to Applications, disclose the temporary unsigned Pre-release warning.
- Windows: run EXE, disclose the temporary SmartScreen warning.
- Keep source commands in a separate developer section.
- State explicitly that the first release replaces existing debug test installs by uninstall/reinstall and loses Android sandbox data.
- State that Telemetry is not yet included.

- [ ] **Step 4: Run documentation and source distribution checks**

Run:

```bash
bash tools/test_desktop_distribution_permissions.sh
rg -n "app-debug.apk|desktop.zip|setup_mediapipe_sidecar" README.md readme-zh.md docs/RELEASE_PACKAGING.md
```

Expected: old strings only appear in a clearly labeled source/developer migration section, never in public installation steps.

- [ ] **Step 5: Commit**

```bash
git add docs/RELEASE_PACKAGING.md README.md readme-zh.md docs/UPDATE_SERVER.md tools/test_desktop_distribution_permissions.sh
git commit -m "docs: publish release installation workflow"
```

## Task 9: Final Release Foundation Verification

**Files:**
- Modify only if a verification failure requires a scoped fix.

- [ ] **Step 1: Run shared regression tests**

Run:

```bash
mkdir -p /tmp/vocagate-core-smoke
javac -d /tmp/vocagate-core-smoke -cp gate-core/src/main/java:app/src/main/java:desktop/src/main/java tools/CoreSmokeTest.java
java -cp /tmp/vocagate-core-smoke:gate-core/src/main/java:app/src/main/java:desktop/src/main/java CoreSmokeTest
gradle :gate-core:compileJava :app:assembleDebug :desktop:test
```

Expected: `CoreSmokeTest passed` and Gradle exits zero.

- [ ] **Step 2: Build and inspect available release artifacts**

Run on the corresponding host(s):

```bash
scripts/package_android_release.sh
scripts/package_macos_arm64.sh
scripts/prepare_release_notes.sh 0.2.0
scripts/verify_release_artifacts.sh --platform macos-arm64 release/0.2.0
git diff --check
git status --short
```

Expected: signed Android release and macOS installer verify locally; Windows installer is produced and verified on its Windows build host. After artifact handoff, run `scripts/aggregate_release_artifacts.sh release/0.2.0` and require the three installer files, release notes and final checksum manifest before a GitHub Release draft is created.

- [ ] **Step 3: Perform real-device acceptance**

Record results for:

1. Android fresh install, release-to-release upgrade and debug-to-release uninstall/reinstall migration.
2. macOS ARM install from DMG, local camera permission, sidecar health check, Accessibility guidance/denial behavior, keyboard-control permission and phone Wi-Fi link.
3. Windows install from EXE, local camera, sidecar health check, elevated-game guidance, keyboard control and phone Wi-Fi link.

- [ ] **Step 4: Commit only scoped fixes, then tag the pre-release candidate**

```bash
git add <scoped-fix-files>
git commit -m "fix: prepare release packaging candidate"
git tag -a v0.2.0-rc1 -m "VocaGate 0.2.0 release packaging candidate"
```

Do not publish a stable GitHub Release until macOS and Windows code signing policy is satisfied. Use a GitHub Pre-release for the unsigned candidate.

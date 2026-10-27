# VocaGate Zero-Config Connection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make phone-to-desktop connection work for normal users through automatic Wi-Fi discovery and pairing, while shipping an audited minimal ADB runtime for a formally supported USB low-latency path that requires no separate Platform-Tools download.

**Architecture:** Extend the existing TCP gate protocol instead of replacing it. The desktop server advertises a versioned mDNS service; Android discovers and resolves it through NSD, then confirms the existing pairing code over the existing TCP protocol. USB uses a VocaGate-owned loopback-only ADB server socket, a fixed audited runtime and serial-specific `adb reverse`, so it never kills or hijacks another application’s default ADB server.

**Tech Stack:** Java 17/21, Android `NsdManager`, JmDNS, CameraX/ML Kit barcode scanning, ZXing desktop QR generation, Android Debug Bridge, SHA-256, SBOM/NOTICE metadata, shell/batch packaging scripts.

---

## Preconditions

1. Complete the release staging tasks in `2026-07-11-release-packaging-foundation.md` first; this plan adds ADB runtime files to that staging layout.
2. Do not add Telemetry, CloudBase, account, payment or automated app updates.
3. Keep the existing manual Wi-Fi IP/port form as an advanced fallback until automatic discovery and QR pairing have passed real-device tests.
4. Do not claim that USB avoids Android developer options, RSA authorization or all Windows OEM drivers.

## File Structure

| File | Responsibility |
|---|---|
| `gate-core/src/main/java/com/vocagate/core/connection/PairingDescriptor.java` | Versioned, serializable pairing data shared by desktop QR and Android parsing. |
| `gate-core/src/main/java/com/vocagate/core/connection/PairingDescriptorParser.java` | Strict parser and validation for `vocagate://pair` payloads. |
| `gate-core/src/test/java/com/vocagate/core/connection/PairingDescriptorParserTest.java` | Reject malformed, expired and unsupported QR payloads. |
| `gate-core/src/main/java/com/vocagate/core/connection/PairingCredential.java` | Client ID and opaque reconnect-token value object; never logs token material. |
| `desktop/src/main/java/com/vocagate/desktop/connection/PairingGrantStore.java` | Persist hash-only authorized client grants and pairing nonce expiry on the desktop. |
| `desktop/build.gradle.kts` | Add JmDNS and ZXing dependencies; include ADB runtime staging from the packaging foundation. |
| `desktop/src/main/java/com/vocagate/desktop/connection/DesktopMdnsAdvertiser.java` | Publish `_vocagate._tcp.local.` service records without pairing secrets. |
| `desktop/src/main/java/com/vocagate/desktop/connection/DesktopPairingQr.java` | Generate QR pixels from a short-lived descriptor. |
| `desktop/src/main/java/com/vocagate/desktop/app/DesktopUsbReverseSetup.java` | Replace system-ADB candidate guessing with audited runtime discovery, dedicated server socket and typed USB states. |
| `desktop/src/main/java/com/vocagate/desktop/app/AdbRuntime.java` | Resolve the installed ADB runtime, manifest and platform-specific executable. |
| `desktop/src/main/java/com/vocagate/desktop/app/AdbServerLease.java` | Own only a child `adb server nodaemon` process protected by a local file lock. |
| `desktop/src/test/java/com/vocagate/desktop/app/AdbRuntimeTest.java` | Verify runtime path, manifest hash and platform detection. |
| `desktop/src/test/java/com/vocagate/desktop/app/DesktopUsbReverseSetupTest.java` | Verify device/unauthorized/offline/multiple/not-found states and serial-scoped commands. |
| `desktop/src/main/java/com/vocagate/desktop/app/DesktopCompanionApp.java` | Start/stop mDNS advertisements, render discovery/QR/USB statuses and persist validated connection preference. |
| `app/build.gradle.kts` | Add ML Kit barcode scanning dependency if the in-app QR scanner is used. |
| `app/src/main/AndroidManifest.xml` | Register a non-exported QR pairing activity and optionally a `vocagate` deep link handler. |
| `app/src/main/java/com/vocagate/demo/pc/PcLinkDiscovery.java` | Discover and resolve compatible desktop services through Android NSD. |
| `app/src/main/java/com/vocagate/demo/pc/PcLinkSettings.java` | Store validated auto-discovered endpoint metadata and channel preference. |
| `app/src/main/java/com/vocagate/demo/pc/PcLinkClient.java` | Prefer verified USB, fall back to verified Wi-Fi, and classify reconnect outcomes without blocking recognition. |
| `app/src/main/java/com/vocagate/demo/PairDesktopActivity.java` | Discover, display, select and QR-scan desktop endpoints. |
| `app/src/main/java/com/vocagate/demo/MainActivity.java` | Open pairing flow from settings and update visible PC connection status. |
| `scripts/prepare_adb_runtime.sh`, `scripts/prepare_adb_runtime.bat` | Materialize a fixed minimal ADB runtime with manifest, license and NOTICE into platform staging. |
| `third_party/adb/<target>/adb-manifest.json` | Committed source provenance, hashes and license inventory for the chosen ADB runtime. |
| `third_party/adb/<target>/LICENSES/` | Committed Apache-2.0, NOTICE and direct dependency license texts. |
| `tools/AdbRuntimeSmokeTest.java` | Verify manifest shape and the expected least-privilege command layout. |
| `tools/ConnectionLatencyHarness.java` | Use an NTP-style offset handshake to calculate Wi-Fi/USB transport P50/P95/P99 and reconnect time. |
| `docs/CONNECTIONS.md` | User and maintainer documentation for automatic Wi-Fi, QR fallback, USB debug authorization and OEM driver limits. |

## Task 1: Define a Versioned Pairing Descriptor and Keep Secrets out of mDNS

**Files:**
- Create: `gate-core/src/main/java/com/vocagate/core/connection/PairingDescriptor.java`
- Create: `gate-core/src/main/java/com/vocagate/core/connection/PairingDescriptorParser.java`
- Create: `gate-core/src/test/java/com/vocagate/core/connection/PairingDescriptorParserTest.java`
- Modify: `gate-core/build.gradle.kts`

- [ ] **Step 1: Write failing parser tests**

Require a payload shaped like this, with a short expiry and no pairing code:

```text
vocagate://pair?v=1&host=192.168.1.20&port=27183&id=desktop-uuid&nonce=base64url&exp=1730000000000
```

Test accepted values and rejection of unsupported versions, non-private/loopback Wi-Fi hosts, invalid ports, missing nonce and expiry more than ten minutes in the future.

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
gradle :gate-core:test --tests com.vocagate.core.connection.PairingDescriptorParserTest
```

Expected: fail because the descriptor types are absent.

- [ ] **Step 3: Implement strict parsing and serialization**

Use only URL-safe encoding, validate all query names, cap input length, and expose an immutable value object. Pairing code remains shown on the desktop and entered/confirmed by the user; it must never be inserted into mDNS TXT records or QR payloads.

- [ ] **Step 4: Run the parser tests**

Run the same Gradle command. Expected: pass.

- [ ] **Step 5: Commit**

```bash
git add gate-core/build.gradle.kts gate-core/src/main/java/com/vocagate/core/connection/PairingDescriptor.java gate-core/src/main/java/com/vocagate/core/connection/PairingDescriptorParser.java gate-core/src/test/java/com/vocagate/core/connection/PairingDescriptorParserTest.java
git commit -m "feat: define secure desktop pairing descriptor"
```

## Task 2: Add Confirmed Pairing and Persistent Reconnect Credentials

**Files:**
- Create: `gate-core/src/main/java/com/vocagate/core/connection/PairingCredential.java`
- Modify: `app/src/main/java/com/vocagate/demo/pc/PcLinkProtocol.java`
- Modify: `app/src/main/java/com/vocagate/demo/pc/PcLinkClient.java`
- Modify: `desktop/src/main/java/com/vocagate/desktop/core/GateMessage.java`
- Modify: `desktop/src/main/java/com/vocagate/desktop/core/GateProtocolParser.java`
- Modify: `desktop/src/main/java/com/vocagate/desktop/server/DesktopGateServer.java`
- Create: `desktop/src/main/java/com/vocagate/desktop/connection/PairingGrantStore.java`
- Create: `desktop/src/test/java/com/vocagate/desktop/connection/PairingGrantStoreTest.java`
- Modify: `tools/CoreSmokeTest.java`

- [ ] **Step 1: Write failing protocol and grant-store tests**

Define a bidirectional pairing exchange:

```text
client -> HELLO 2 <client-id>
client -> PAIR <descriptor-nonce> <six-digit-code>
server -> PAIR_OK <base64url-32-byte-token>

advanced manual fallback:
client -> HELLO 2 <client-id>
client -> PAIR_MANUAL <six-digit-code>
server -> PAIR_OK <base64url-32-byte-token>

later reconnect:
client -> HELLO 2 <client-id>
client -> AUTH <base64url-token>
server -> AUTH_OK
```

Test that the desktop stores only `SHA-256(token)` keyed by client ID; a duplicate `PAIR` rotates the grant; invalid nonce, expired nonce, wrong code, malformed client ID and more than five attempts in sixty seconds are rejected. Test that `PAIR_MANUAL` succeeds only with the currently displayed pairing code and is subject to the same rate limit. Test that no log or status string contains the credential value.

- [ ] **Step 2: Run tests to verify failure**

Run:

```bash
mkdir -p /tmp/vocagate-core-smoke
javac -d /tmp/vocagate-core-smoke -cp gate-core/src/main/java:app/src/main/java:desktop/src/main/java tools/CoreSmokeTest.java
java -cp /tmp/vocagate-core-smoke:gate-core/src/main/java:app/src/main/java:desktop/src/main/java CoreSmokeTest
gradle :desktop:test --tests com.vocagate.desktop.connection.PairingGrantStoreTest
```

Expected: fail because the current protocol is client-write-only and no grant store exists.

- [ ] **Step 3: Implement server acknowledgement and rate-limited grants**

Make `DesktopGateServer` retain a writer for the active client during handshake, send `PAIR_OK` only after successful code and nonce verification, and send `AUTH_OK` only after a constant-time token-hash comparison. `PAIR_MANUAL` intentionally skips the discovery nonce because the user has entered the current code shown by the desktop; it still uses the same strict rate limit and short code lifetime. `PcLinkClient` must wait for this acknowledgement before persisting an endpoint or sending gate state. Store the Android credential using the Android keystore-backed encrypted preference facility; do not put it in UI text, logs, exported storage or a QR image.

Store the current pairing nonce with a five-minute expiry and rotate it whenever the desktop server starts or the current nonce is within thirty seconds of expiry. The random six-digit code is a human confirmation factor, not a persistent credential. Limit unsuccessful pairing attempts by source address and client ID; close the socket on a limit breach.

- [ ] **Step 4: Run protocol tests**

Run the Step 2 commands. Expected: pairing cannot be persisted without `PAIR_OK`, reconnect works with `AUTH_OK`, and stale QR nonces fail safely.

- [ ] **Step 5: Commit**

```bash
git add gate-core/src/main/java/com/vocagate/core/connection/PairingCredential.java app/src/main/java/com/vocagate/demo/pc/PcLinkProtocol.java app/src/main/java/com/vocagate/demo/pc/PcLinkClient.java desktop/src/main/java/com/vocagate/desktop/core/GateMessage.java desktop/src/main/java/com/vocagate/desktop/core/GateProtocolParser.java desktop/src/main/java/com/vocagate/desktop/server/DesktopGateServer.java desktop/src/main/java/com/vocagate/desktop/connection/PairingGrantStore.java desktop/src/test/java/com/vocagate/desktop/connection/PairingGrantStoreTest.java tools/CoreSmokeTest.java
git commit -m "feat: confirm and persist phone pairing"
```

## Task 3: Advertise and Discover Desktop Services over mDNS

**Files:**
- Modify: `desktop/build.gradle.kts`
- Create: `desktop/src/main/java/com/vocagate/desktop/connection/DesktopMdnsAdvertiser.java`
- Create: `desktop/src/test/java/com/vocagate/desktop/connection/DesktopMdnsAdvertiserTest.java`
- Create: `app/src/main/java/com/vocagate/demo/pc/PcLinkDiscovery.java`
- Create: `app/src/test/java/com/vocagate/demo/pc/PcLinkDiscoveryTest.java`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add failing desktop advertisement tests**

Assert that the service type is exactly `_vocagate._tcp.local.`, that the advertised port is the live `DesktopGateServer.port()`, and that TXT data contains `v`, `id`, `nonce`, `exp` and protocol capabilities, but never the six-digit pairing code or reconnect credential.

- [ ] **Step 2: Run the desktop test to verify it fails**

Run:

```bash
gradle :desktop:test --tests com.vocagate.desktop.connection.DesktopMdnsAdvertiserTest
```

Expected: compilation failure because the advertiser does not exist.

- [ ] **Step 3: Implement the desktop advertiser**

Add JmDNS as a desktop dependency. Bind advertisements to active non-loopback interfaces after `DesktopGateServer.start()` returns the actual bound port. Use a stable installation-local service ID and a readable display name; close all registrations before the server stops. Schedule a refresh before the five-minute nonce expires: rotate the nonce through the pairing-session owner, replace/update the mDNS TXT record, and notify the QR view to redraw with the new descriptor.

The TXT record contract is:

```text
v=1
id=<random-stable-desktop-install-id>
nonce=<current-short-lived-pairing-nonce>
exp=<nonce-expiry-epoch-ms>
cap=wifi,usb
```

No pairing code, reconnect token, user name or telemetry data belongs in TXT records. The service host and port are supplied by mDNS resolution; the descriptor nonce only binds an initial pairing attempt to the currently advertised desktop session.

- [ ] **Step 4: Implement Android NSD discovery**

Wrap `NsdManager` in `PcLinkDiscovery` with callback methods for `onServiceFound`, `onServiceResolved`, `onDiscoveryError`, and `onStopped`. Resolve only `_vocagate._tcp.` services with `v=1`, a valid base64url nonce and an unexpired `exp`; ignore stale records and keep discovery lifecycle tied to the pairing screen, not the always-running face-recognition Activity.

Add unit coverage for a service record that expires during discovery and for a desktop nonce refresh that changes both TXT metadata and the generated QR descriptor.

- [ ] **Step 5: Run desktop and Android unit tests**

Run:

```bash
gradle :desktop:test --tests com.vocagate.desktop.connection.DesktopMdnsAdvertiserTest
gradle :app:testDebugUnitTest --tests com.vocagate.demo.pc.PcLinkDiscoveryTest
```

Expected: both pass without requiring a physical mDNS network.

- [ ] **Step 6: Commit**

```bash
git add desktop/build.gradle.kts desktop/src/main/java/com/vocagate/desktop/connection/DesktopMdnsAdvertiser.java desktop/src/test/java/com/vocagate/desktop/connection/DesktopMdnsAdvertiserTest.java app/build.gradle.kts app/src/main/java/com/vocagate/demo/pc/PcLinkDiscovery.java app/src/test/java/com/vocagate/demo/pc/PcLinkDiscoveryTest.java
git commit -m "feat: add local desktop discovery"
```

## Task 4: Add User-Facing Automatic Pairing and QR Fallback

**Files:**
- Modify: `desktop/build.gradle.kts`
- Create: `desktop/src/main/java/com/vocagate/desktop/connection/DesktopPairingQr.java`
- Modify: `desktop/src/main/java/com/vocagate/desktop/app/DesktopCompanionApp.java`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/vocagate/demo/PairDesktopActivity.java`
- Modify: `app/src/main/java/com/vocagate/demo/pc/PcLinkSettings.java`
- Modify: `app/src/main/java/com/vocagate/demo/MainActivity.java`
- Test: `tools/CoreSmokeTest.java`

- [ ] **Step 1: Write failing pairing-flow tests**

Add pure-Java tests for these transitions:

```text
discovered -> selected -> pairing-code-confirmed -> saved
discovery-failed -> qr-scan -> pairing-code-confirmed -> saved
manual-fallback -> PAIR_MANUAL acknowledgement -> saved
nonce-expired -> refresh discovery/QR -> pairing-code-confirmed -> saved
```

The old manual endpoint must remain unavailable to the normal first-run path but remain callable through an “高级连接” action.

- [ ] **Step 2: Run tests to verify failure**

Run the relevant `CoreSmokeTest` compilation command. Expected: failure because the new connection flow state does not exist.

- [ ] **Step 3: Implement desktop QR and Android pairing screen**

Use ZXing on desktop to render a QR image from `PairingDescriptor`. Add ML Kit barcode scanning to a dedicated Android pairing Activity; pause the face camera while scanning and return the parsed descriptor only after it passes the shared gate-core parser.

The Android screen order is:

```text
自动发现电脑
  -> 选择 “VocaGate Desktop”
  -> 输入/确认桌面显示的六位码
  -> 保存并连接

未发现电脑
  -> 扫描桌面二维码
  -> 输入/确认六位码
  -> 保存并连接

仍失败
  -> 高级连接（IP、端口、配对码）
  -> PAIR_MANUAL 确认后保存
```

- [ ] **Step 4: Wire persistence and reconnect policy**

Extend `PcLinkSettings` with a validated Wi-Fi endpoint, stable desktop ID, client ID and opaque reconnect credential. Persist them only after an explicit `PAIR_OK`; later connections must receive `AUTH_OK` before gate events are sent. Existing manually configured values remain readable and are labelled as advanced setup.

- [ ] **Step 5: Run unit tests and build Android**

Run:

```bash
mkdir -p /tmp/vocagate-core-smoke
javac -d /tmp/vocagate-core-smoke -cp gate-core/src/main/java:app/src/main/java:desktop/src/main/java tools/CoreSmokeTest.java
java -cp /tmp/vocagate-core-smoke:gate-core/src/main/java:app/src/main/java:desktop/src/main/java CoreSmokeTest
gradle :app:assembleDebug :desktop:compileJava
```

Expected: smoke passes, Android compiles, and desktop keeps a manual fallback if mDNS cannot work on a router.

- [ ] **Step 6: Commit**

```bash
git add desktop/build.gradle.kts desktop/src/main/java/com/vocagate/desktop/connection/DesktopPairingQr.java desktop/src/main/java/com/vocagate/desktop/app/DesktopCompanionApp.java app/build.gradle.kts app/src/main/AndroidManifest.xml app/src/main/java/com/vocagate/demo/PairDesktopActivity.java app/src/main/java/com/vocagate/demo/pc/PcLinkSettings.java app/src/main/java/com/vocagate/demo/MainActivity.java tools/CoreSmokeTest.java
git commit -m "feat: add automatic phone pairing flow"
```

## Task 5: Define and Verify the Minimal ADB Runtime Supply Chain

**Files:**
- Create: `third_party/adb/macos-arm64/adb-manifest.json`
- Create: `third_party/adb/windows-x64/adb-manifest.json`
- Create: `third_party/adb/macos-arm64/LICENSES/Apache-2.0.txt`
- Create: `third_party/adb/windows-x64/LICENSES/Apache-2.0.txt`
- Create: `third_party/adb/macos-arm64/LICENSES/NOTICE.txt`
- Create: `third_party/adb/windows-x64/LICENSES/NOTICE.txt`
- Create: `scripts/prepare_adb_runtime.sh`
- Create: `scripts/prepare_adb_runtime.bat`
- Create: `tools/AdbRuntimeSmokeTest.java`
- Modify: `scripts/verify_release_artifacts.sh`

- [ ] **Step 1: Write a failing ADB manifest smoke test**

Require one manifest per target with this complete shape:

```json
{
  "component": "Android Debug Bridge",
  "target": "macos-arm64",
  "adb_version": "1.0.41",
  "source_revision": "<fixed-AOSP-or-official-release-revision>",
  "source_url": "https://...",
  "license": "Apache-2.0",
  "files": [
    {"path": "adb", "sha256": "<64-lowercase-hex>"}
  ]
}
```

Require a corresponding `NOTICE.txt`, Apache license text, and a generated dependency inventory file. Reject `fastboot`, `sqlite3`, `systrace`, `dmtracedump` or any unlisted executable in the staged runtime.

- [ ] **Step 2: Run the smoke test to verify it fails**

Run:

```bash
mkdir -p /tmp/vocagate-adb-smoke
javac -d /tmp/vocagate-adb-smoke tools/AdbRuntimeSmokeTest.java
java -cp /tmp/vocagate-adb-smoke AdbRuntimeSmokeTest
```

Expected: fail because no audited manifests exist.

- [ ] **Step 3: Select and audit a concrete source**

Prefer a fixed AOSP revision. If the first runtime derives from Google’s Platform-Tools archive, record its exact official version, download URL, archive SHA-256, AOSP prebuilt revision and preserved `NOTICE.txt`. On macOS run `otool -L adb`; on Windows use a dependency scanner appropriate to the build environment. Record every redistributed runtime file and license before it is copied into the staging directory.

Do not manually cherry-pick an `adb` binary from an arbitrary developer machine. Do not use `latest` download URLs.

- [ ] **Step 4: Implement reproducible staging**

`prepare_adb_runtime.*` must copy only manifest-listed files to `desktop/build/release-staging/<target>/app/adb/`, verify every SHA-256, copy the license directory and create an SPDX or CycloneDX SBOM fragment. A missing hash, notice, dependency inventory or unexpected executable must fail the build.

- [ ] **Step 5: Run audit verification**

Run:

```bash
scripts/prepare_adb_runtime.sh macos-arm64
mkdir -p /tmp/vocagate-adb-smoke
javac -d /tmp/vocagate-adb-smoke tools/AdbRuntimeSmokeTest.java
java -cp /tmp/vocagate-adb-smoke AdbRuntimeSmokeTest
```

Expected: staged runtime contains only approved files and manifest hashes match.

- [ ] **Step 6: Commit**

```bash
git add third_party/adb scripts/prepare_adb_runtime.sh scripts/prepare_adb_runtime.bat tools/AdbRuntimeSmokeTest.java scripts/verify_release_artifacts.sh
git commit -m "build: audit minimal adb runtime"
```

## Task 6: Isolate ADB Server Lifecycle and USB Device States

**Files:**
- Create: `desktop/src/main/java/com/vocagate/desktop/app/AdbRuntime.java`
- Create: `desktop/src/main/java/com/vocagate/desktop/app/AdbServerLease.java`
- Modify: `desktop/src/main/java/com/vocagate/desktop/app/DesktopUsbReverseSetup.java`
- Create: `desktop/src/test/java/com/vocagate/desktop/app/AdbRuntimeTest.java`
- Create: `desktop/src/test/java/com/vocagate/desktop/app/AdbServerLeaseTest.java`
- Create: `desktop/src/test/java/com/vocagate/desktop/app/DesktopUsbReverseSetupTest.java`
- Modify: `desktop/src/main/java/com/vocagate/desktop/app/DesktopCompanionApp.java`

- [ ] **Step 1: Write failing USB state-machine tests**

Use the existing injected command seam plus a new `AdbServerLauncher` seam. Assert a VocaGate server is started as a child `server nodaemon` process and commands use the VocaGate server socket and explicit serial:

```java
assertEquals(List.of(adbPath, "-L", "tcp:localhost:27184", "server", "nodaemon"), launcher.command());
assertEquals(List.of(adbPath, "-P", "27184", "-s", "SERIAL", "reverse", "tcp:27183", "tcp:27183"), runner.commands().get(3));
```

Add one test per state: `device`, `unauthorized`, `offline`, more than one authorized device, no device, malformed output, reverse already exists and reverse create failure. Add ownership tests for a busy `27184` port with no VocaGate lock, a valid current-process lock and a stale lock. Assert no command contains `kill-server`, `-a`, `fastboot`, or a command without `-s` after device selection.

- [ ] **Step 2: Run tests to verify failure**

Run:

```bash
gradle :desktop:test --tests com.vocagate.desktop.app.DesktopUsbReverseSetupTest
```

Expected: fail because the old class uses system ADB candidates, one global device check and no typed states.

- [ ] **Step 3: Implement `AdbRuntime` and typed results**

`AdbRuntime` resolves packaged `app/adb/adb[.exe]`, parses and verifies its manifest before allowing execution, and exposes the dedicated loopback server endpoint. Development mode may fall back to `ADB`, `ANDROID_HOME`, `ANDROID_SDK_ROOT`, then PATH, but packaged mode must not.

`AdbServerLease` obtains an exclusive lock file before launch, starts `adb -L tcp:localhost:27184 server nodaemon` as a child process, and records the child PID plus binary hash in the lock. If port 27184 is occupied without a valid live lease owned by the current process, return `SERVER_BUSY` and do not send any stop command. On application exit, terminate only the retained child `Process`; never issue `adb kill-server`.

Replace string-only `Result` with a typed state enum:

```java
enum UsbState { READY, UNAUTHORIZED, OFFLINE, MULTIPLE, NOT_FOUND, RUNTIME_INVALID, SERVER_BUSY, REVERSE_FAILED }
```

Start and communicate with the VocaGate-only child server on `localhost:27184`; do not execute `adb kill-server` against any server. When the app exits, close only the retained lease process. If the lease cannot prove ownership, leave the socket untouched and expose a recoverable `SERVER_BUSY` status.

- [ ] **Step 4: Apply UI behavior and channel fallback**

Map state values to clear UI text and actions. In Auto mode, successful USB becomes the active channel; loss of USB lets the existing client reconnect to a verified Wi-Fi endpoint. Do not drop an active Wi-Fi channel merely because an unauthorized USB device is present.

- [ ] **Step 5: Run tests and desktop build**

Run:

```bash
gradle :desktop:test --tests com.vocagate.desktop.app.AdbRuntimeTest --tests com.vocagate.desktop.app.AdbServerLeaseTest --tests com.vocagate.desktop.app.DesktopUsbReverseSetupTest
gradle :desktop:assembleDist
```

Expected: tests pass; existing users with a system `adb` retain development behavior, while a packaged runtime fails safely if manifest verification fails.

- [ ] **Step 6: Commit**

```bash
git add desktop/src/main/java/com/vocagate/desktop/app/AdbRuntime.java desktop/src/main/java/com/vocagate/desktop/app/AdbServerLease.java desktop/src/main/java/com/vocagate/desktop/app/DesktopUsbReverseSetup.java desktop/src/main/java/com/vocagate/desktop/app/DesktopCompanionApp.java desktop/src/test/java/com/vocagate/desktop/app/AdbRuntimeTest.java desktop/src/test/java/com/vocagate/desktop/app/AdbServerLeaseTest.java desktop/src/test/java/com/vocagate/desktop/app/DesktopUsbReverseSetupTest.java
git commit -m "feat: add isolated bundled adb USB transport"
```

## Task 7: Prioritize Verified USB and Wi-Fi Endpoints on Android

**Files:**
- Modify: `app/src/main/java/com/vocagate/demo/pc/PcLinkSettings.java`
- Modify: `app/src/main/java/com/vocagate/demo/pc/PcLinkClient.java`
- Create: `app/src/test/java/com/vocagate/demo/pc/PcLinkSettingsTest.java`
- Create: `app/src/test/java/com/vocagate/demo/pc/PcLinkClientPolicyTest.java`
- Modify: `app/src/main/java/com/vocagate/demo/MainActivity.java`

- [ ] **Step 1: Write failing endpoint-policy tests**

Assert these invariants:

```text
AUTO + verified USB + verified Wi-Fi -> USB first
AUTO + USB unavailable + verified Wi-Fi -> Wi-Fi connects without reopening settings
AUTO + unauthorized USB + verified Wi-Fi -> Wi-Fi remains usable
WIFI mode -> never attempts 127.0.0.1
USB mode -> never sends Wi-Fi pairing code
```

- [ ] **Step 2: Run tests to verify failure**

Run:

```bash
gradle :app:testDebugUnitTest --tests com.vocagate.demo.pc.PcLinkSettingsTest --tests com.vocagate.demo.pc.PcLinkClientPolicyTest
```

Expected: fail because existing endpoint order treats USB availability only as a connection attempt and does not preserve verified-channel state.

- [ ] **Step 3: Implement endpoint state and reconnection rules**

Persist only validated Wi-Fi endpoints. Have the desktop status protocol expose enough channel information for Android to avoid falsely treating loopback failures as a global PC failure. Keep all network attempts on the existing background worker; no discovery or reconnect operation may block camera analysis or the main thread.

- [ ] **Step 4: Run tests and Android build**

Run:

```bash
gradle :app:testDebugUnitTest --tests com.vocagate.demo.pc.PcLinkSettingsTest --tests com.vocagate.demo.pc.PcLinkClientPolicyTest
gradle :app:assembleDebug
```

Expected: tests pass and the APK builds.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/vocagate/demo/pc/PcLinkSettings.java app/src/main/java/com/vocagate/demo/pc/PcLinkClient.java app/src/test/java/com/vocagate/demo/pc/PcLinkSettingsTest.java app/src/test/java/com/vocagate/demo/pc/PcLinkClientPolicyTest.java app/src/main/java/com/vocagate/demo/MainActivity.java
git commit -m "feat: prefer verified USB with wifi fallback"
```

## Task 8: Measure Transport Reliability and Document Real Boundaries

**Files:**
- Create: `tools/ConnectionLatencyHarness.java`
- Create: `docs/CONNECTIONS.md`
- Modify: `README.md`
- Modify: `readme-zh.md`
- Test: `tools/ConnectionLatencyHarness.java`

- [ ] **Step 1: Write a failing latency-summary test**

Use a deterministic set of four-timestamp handshake samples and assert calculated clock offset, round-trip time, `count`, `min`, `p50`, `p95`, `p99`, `max`, and reconnect duration. Define percentile selection in the test so reports do not silently change with a library implementation.

- [ ] **Step 2: Run the harness test to verify failure**

Run:

```bash
mkdir -p /tmp/vocagate-connection-smoke
javac -d /tmp/vocagate-connection-smoke tools/ConnectionLatencyHarness.java
java -cp /tmp/vocagate-connection-smoke ConnectionLatencyHarness --self-test
```

Expected: fail because the harness does not exist.

- [ ] **Step 3: Implement timestamp tracing**

Add a clock-offset handshake before recording events. For client timestamps `t0`/`t3` and desktop timestamps `t1`/`t2`, estimate the desktop-minus-Android offset as:

```text
offset = ((t1 - t0) + (t2 - t3)) / 2
rtt = (t3 - t0) - (t2 - t1)
```

Collect several samples and retain the lowest-RTT offset. For each `PRE_OPEN`, record Android event creation `ta`, desktop receipt `td`, and desktop key-actuator invocation `tk`. Convert receipt to the Android clock domain using `td - offset`, then calculate transport latency as `(td - offset) - ta` and desktop actuation latency as `tk - td`. Renew the offset estimate after reconnection and discard samples with negative values or RTT above the configured bound.

The harness consumes these local diagnostic records and prints separate Wi-Fi/USB summaries. It does not upload anything and is not Telemetry; it never records audio, video, landmarks, key content beyond the configured action, game name or IP history.

- [ ] **Step 4: Run local and real-device measurements**

Run the self-test command, then collect at least 100 `PRE_OPEN` events for each channel on the same phone/desktop pair. Record P50/P95/P99/max and reconnect time in a non-committed local test note or GitHub issue.

- [ ] **Step 5: Document the user-facing behavior**

Explain:

- Wi-Fi automatic discovery needs a non-isolated local network.
- QR and advanced manual entry are fallbacks.
- USB ships ADB runtime but still needs USB debugging and RSA confirmation.
- Some Windows phones require OEM drivers.
- USB does not claim a fixed latency advantage; release notes may state measured results for tested hardware only.

- [ ] **Step 6: Run full connection regression and commit**

Run:

```bash
mkdir -p /tmp/vocagate-connection-smoke
javac -d /tmp/vocagate-connection-smoke tools/ConnectionLatencyHarness.java
java -cp /tmp/vocagate-connection-smoke ConnectionLatencyHarness --self-test
gradle :gate-core:test :app:testDebugUnitTest :desktop:test
git diff --check
```

Expected: all tests pass and no whitespace errors appear.

Commit:

```bash
git add tools/ConnectionLatencyHarness.java docs/CONNECTIONS.md README.md readme-zh.md
git commit -m "docs: describe automatic wifi and USB connection"
```

## Task 9: Integrate with Packaging and Perform Cross-Platform Acceptance

**Files:**
- Modify only scoped files required by failures in this task.

- [ ] **Step 1: Add ADB runtime to release staging checks**

Require desktop staging to contain:

```text
app/adb/adb[.exe]
app/adb/adb-manifest.json
app/adb/LICENSES/Apache-2.0.txt
app/adb/LICENSES/NOTICE.txt
app/THIRD_PARTY_NOTICES.txt
```

Verify there is no `fastboot` binary and no bind address other than localhost in the runtime command configuration.

- [ ] **Step 2: Build platform candidates**

Run:

```bash
scripts/package_android_release.sh
scripts/package_macos_arm64.sh
scripts/prepare_release_notes.sh 0.2.0
scripts/verify_release_artifacts.sh --platform macos-arm64 release/0.2.0
```

Run `scripts/package_windows_x64.bat`, `scripts\prepare_release_notes.bat 0.2.0` and `scripts\verify_release_artifacts.bat --platform windows-x64 release\0.2.0` on Windows x64. Windows must generate its own release notes before verification; do not rely on ignored `release/` output from macOS. After cross-host artifact handoff, run `scripts/aggregate_release_artifacts.sh release/0.2.0` before drafting the GitHub Pre-release.

- [ ] **Step 3: Run real-device acceptance matrix**

| Scenario | Required result |
|---|---|
| macOS + Android, same Wi-Fi | automatic discovery, six-digit confirmation, gate event reaches desktop |
| mDNS blocked | QR pairing or advanced manual fallback works; clear error text |
| macOS + Android USB | internal ADB sees authorized device, reverse works, no default 5037 server is killed |
| Windows + supported Android USB driver | internal ADB works after RSA authorization |
| Windows + missing OEM driver | status explains driver limitation; Wi-Fi remains usable |
| USB unplug during active session | key releases safely, then verified Wi-Fi reconnects if available |
| multiple USB devices | user is asked to select a device; no arbitrary reverse command |

- [ ] **Step 4: Prepare pre-release evidence**

Attach checksums, ADB provenance/SBOM, model lock provenance, installer size, real-device test matrix and transport percentile measurements to the GitHub Pre-release draft. Do not label it stable until macOS/Windows signing policy is complete.

- [ ] **Step 5: Commit scoped fixes only**

```bash
git add <scoped-fix-files>
git commit -m "fix: finalize zero-config connection candidate"
```

# Auto USB Wi-Fi Connection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make VocaGate support USB and Wi-Fi desktop links together, with Auto / USB / Wi-Fi selection on desktop and Android.

**Architecture:** The desktop companion listens on one TCP port for both loopback USB reverse clients and LAN Wi-Fi clients. A small connection policy decides which session is active, preferring USB in Auto mode, while Android chooses endpoints based on the selected mode and saved Wi-Fi pairing settings.

**Tech Stack:** Java 17, Swing, Java sockets, Android Java, existing text-line VocaGate protocol.

---

### Task 1: Core Protocol And Policy

**Files:**
- Modify: `desktop/src/main/java/com/vocagate/desktop/core/GateMessage.java`
- Modify: `desktop/src/main/java/com/vocagate/desktop/core/GateProtocolParser.java`
- Create: `desktop/src/main/java/com/vocagate/desktop/server/ConnectionChannel.java`
- Create: `desktop/src/main/java/com/vocagate/desktop/server/ConnectionMode.java`
- Create: `desktop/src/main/java/com/vocagate/desktop/server/ConnectionPolicy.java`
- Modify: `tools/DesktopCompanionSmokeTest.java`
- Modify: `app/src/main/java/com/vocagate/demo/pc/PcLinkProtocol.java`
- Modify: `tools/CoreSmokeTest.java`

- [ ] Add failing tests for `PRE_OPEN`, `PAIR <code>`, and USB-over-Wi-Fi priority in Auto mode.
- [ ] Implement the minimal parser and policy classes.
- [ ] Run `DesktopCompanionSmokeTest` and `CoreSmokeTest`.

### Task 2: Desktop Server And UI

**Files:**
- Modify: `desktop/src/main/java/com/vocagate/desktop/server/DesktopGateServer.java`
- Modify: `desktop/src/main/java/com/vocagate/desktop/app/DesktopCompanionApp.java`

- [ ] Bind the desktop server to all interfaces, not only `127.0.0.1`.
- [ ] Classify sessions as USB for loopback clients and Wi-Fi for non-loopback clients.
- [ ] Add Auto / USB / Wi-Fi selector, current channel label, local IP label, and pairing code label.
- [ ] Ensure channel switching releases the held key before changing active session.
- [ ] Run desktop smoke tests.

### Task 3: Android Endpoint Selection

**Files:**
- Modify: `app/src/main/java/com/vocagate/demo/pc/PcLinkClient.java`
- Modify: `app/src/main/java/com/vocagate/demo/MainActivity.java`
- Modify: `app/src/test/java/com/vocagate/demo/core/CoreAlgorithmTest.java`

- [ ] Add mode, Wi-Fi host, pairing code, and endpoint selection tests.
- [ ] Add Android settings controls for Auto / USB / Wi-Fi, Wi-Fi IP, and pairing code.
- [ ] Persist Wi-Fi settings with `SharedPreferences`.
- [ ] Send `PAIR <code>` before state messages when using Wi-Fi.
- [ ] Send `PRE_OPEN` when state machine enters `PRE_OPEN`.

### Task 4: Docs And Verification

**Files:**
- Modify: `docs/DESKTOP_COMPANION.md`
- Modify: `docs/DESKTOP_COMPANION_DESIGN.md`
- Modify: `docs/ANDROID_DEMO.md`
- Modify: `README.md`

- [ ] Document Auto / USB / Wi-Fi behavior and first Wi-Fi pairing flow.
- [ ] Run script smoke tests.
- [ ] Run Android unit tests and build APK.
- [ ] Run desktop build and install distribution.

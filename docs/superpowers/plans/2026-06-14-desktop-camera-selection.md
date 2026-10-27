# Desktop Camera Selection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add macOS/Windows desktop camera source selection, preview, and last-device memory while keeping the existing phone-driven recognition path unchanged.

**Architecture:** The Swing desktop companion gets a new input-source selector: `Phone VocaGate` keeps the current TCP/USB/Wi-Fi flow, while `Local Camera` enables camera enumeration, selection, and preview only. Camera selection and persisted preferences live in small app-layer classes so they can be tested without opening real camera hardware.

**Tech Stack:** Java 17, Swing, Gradle `application`, webcam-capture for desktop camera enumeration/preview, Java `Preferences` for last-device memory.

---

### Task 1: Camera Selection Model

**Files:**
- Create: `desktop/src/main/java/com/vocagate/desktop/app/DesktopInputSource.java`
- Create: `desktop/src/main/java/com/vocagate/desktop/app/DesktopCameraDevice.java`
- Create: `desktop/src/main/java/com/vocagate/desktop/app/DesktopCameraPreferences.java`
- Modify: `tools/DesktopCompanionSmokeTest.java`

- [ ] Write failing smoke tests for source labels, remembered camera id, and fallback selection.
- [ ] Implement the model and preferences wrappers without using camera hardware.
- [ ] Run `javac ... tools/DesktopCompanionSmokeTest.java` and `java -cp ... DesktopCompanionSmokeTest`.

### Task 2: Camera Provider And Preview

**Files:**
- Create: `desktop/src/main/java/com/vocagate/desktop/camera/DesktopCameraProvider.java`
- Create: `desktop/src/main/java/com/vocagate/desktop/camera/WebcamCaptureCameraProvider.java`
- Modify: `desktop/build.gradle.kts`
- Modify: `desktop/src/main/java/com/vocagate/desktop/app/DesktopCompanionApp.java`

- [ ] Add webcam-capture dependency.
- [ ] Add a provider wrapper that lists cameras by stable-ish id/name and returns Swing preview components.
- [ ] Add UI controls for input source, camera device, refresh, and preview area.
- [ ] Save selected source and camera id when changed.
- [ ] Stop preview on source changes and window close.

### Task 3: Documentation And Verification

**Files:**
- Modify: `README.md`
- Modify: `docs/DESKTOP_COMPANION.md`
- Modify: `docs/DESKTOP_COMPANION_DESIGN.md`

- [ ] Document that local camera mode is preview/device-selection only in this phase.
- [ ] Verify phone recognition flow remains unchanged.
- [ ] Run `tools/DesktopCompanionSmokeTest`, `tools/test_pc_link_script.sh`, `:desktop:distZip`, and inspect `desktop.zip`.

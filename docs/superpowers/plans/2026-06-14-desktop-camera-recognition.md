# Desktop Local Camera Recognition Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a desktop local-camera recognition technical preview that shows live speaking-gate evidence when the desktop camera source is selected.

**Architecture:** Keep the phone VocaGate path unchanged. Desktop local camera uses the existing webcam preview session, exposes snapshots to a small analyzer, and renders recognition score/state in the Swing UI without driving the keyboard yet.

**Tech Stack:** Java Swing, webcam-capture, `BufferedImage` feature analysis, existing desktop smoke tests.

---

### Task 1: Desktop Recognition Analyzer

**Files:**
- Modify: `tools/DesktopCompanionSmokeTest.java`
- Create: `desktop/src/main/java/com/vocagate/desktop/vision/DesktopRecognitionResult.java`
- Create: `desktop/src/main/java/com/vocagate/desktop/vision/DesktopMotionScoreAnalyzer.java`

- [x] **Step 1: Write failing tests for static and changed mouth-region frames**
- [x] **Step 2: Run smoke compile/test and confirm missing recognition classes fail**
- [x] **Step 3: Implement the minimal analyzer and result value object**
- [x] **Step 4: Run smoke compile/test and confirm the new tests pass**

### Task 2: Preview Snapshot Hook

**Files:**
- Modify: `desktop/src/main/java/com/vocagate/desktop/app/DesktopCameraPreviewSession.java`
- Modify: `desktop/src/main/java/com/vocagate/desktop/camera/WebcamCaptureCameraProvider.java`

- [x] **Step 1: Add a snapshot method to preview sessions**
- [x] **Step 2: Return `webcam.getImage()` from the webcam provider**
- [x] **Step 3: Keep unavailable/mock paths safe with nullable snapshots**

### Task 3: Swing UI Status Loop

**Files:**
- Modify: `desktop/src/main/java/com/vocagate/desktop/app/DesktopCompanionApp.java`

- [x] **Step 1: Add recognition status labels below the preview**
- [x] **Step 2: Start a lightweight scheduled loop when a local camera preview opens**
- [x] **Step 3: Stop the loop when switching source, switching camera, or closing the app**
- [x] **Step 4: Mark the feature as a technical preview that does not control PTT yet**

### Task 4: Docs and Packaging Verification

**Files:**
- Modify: `README.md`
- Modify: `docs/DESKTOP_COMPANION.md`
- Modify: `docs/DESKTOP_COMPANION_DESIGN.md`

- [x] **Step 1: Document the desktop local-camera recognition preview boundary**
- [x] **Step 2: Run desktop smoke tests and shell script checks**
- [x] **Step 3: Build `:desktop:distZip` and `:desktop:installDist`**
- [x] **Step 4: Inspect the desktop zip contents**

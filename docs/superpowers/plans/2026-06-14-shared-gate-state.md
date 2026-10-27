# Shared Gate State Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Android and desktop local-camera recognition use the same gate state machine before desktop recognition is allowed to drive PTT.

**Architecture:** Extract only `GateConfig`, `GateState`, and `GateStateMachine` into a small JVM `:gate-core` module. Android keeps ML Kit mouth feature extraction and score estimation; desktop keeps camera capture and the prototype motion analyzer, but both feed scores into the shared state machine.

**Tech Stack:** Gradle multi-module build, Java 17, Android app module, Java desktop module, smoke tests.

---

### Task 1: Shared State Contract Test

**Files:**
- Modify: `tools/DesktopCompanionSmokeTest.java`

- [x] **Step 1: Write failing desktop test that expects a shared `GateState` from local recognition**
- [x] **Step 2: Run desktop smoke compile and confirm it fails because the shared state is not available**

### Task 2: Extract Gate Core Module

**Files:**
- Modify: `settings.gradle.kts`
- Create: `gate-core/build.gradle.kts`
- Create: `gate-core/src/main/java/com/vocagate/core/GateConfig.java`
- Create: `gate-core/src/main/java/com/vocagate/core/GateState.java`
- Create: `gate-core/src/main/java/com/vocagate/core/GateStateMachine.java`
- Delete: `app/src/main/java/com/vocagate/demo/core/GateConfig.java`
- Delete: `app/src/main/java/com/vocagate/demo/core/GateState.java`
- Delete: `app/src/main/java/com/vocagate/demo/core/GateStateMachine.java`

- [x] **Step 1: Add the `:gate-core` module**
- [x] **Step 2: Move state machine classes into `com.vocagate.core`**
- [x] **Step 3: Remove the app-local duplicate state machine classes**

### Task 3: Wire Android and Desktop to Shared Gate Core

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `desktop/build.gradle.kts`
- Modify: `app/src/main/java/com/vocagate/demo/MainActivity.java`
- Modify: `app/src/main/java/com/vocagate/demo/core/UserTuning.java`
- Modify: `app/src/test/java/com/vocagate/demo/core/CoreAlgorithmTest.java`
- Modify: `tools/CoreSmokeTest.java`
- Modify: `desktop/src/main/java/com/vocagate/desktop/vision/DesktopRecognitionResult.java`
- Modify: `desktop/src/main/java/com/vocagate/desktop/vision/DesktopMotionScoreAnalyzer.java`
- Modify: `tools/DesktopCompanionSmokeTest.java`

- [x] **Step 1: Add project dependencies**
- [x] **Step 2: Update Android imports to `com.vocagate.core`**
- [x] **Step 3: Update desktop analyzer to hold a shared `GateStateMachine`**
- [x] **Step 4: Run smoke tests until green**

### Task 4: Documentation and Verification

**Files:**
- Modify: `README.md`
- Modify: `docs/DESKTOP_COMPANION.md`
- Modify: `docs/DESKTOP_COMPANION_DESIGN.md`

- [x] **Step 1: Document that gate state is now shared**
- [x] **Step 2: Run Java smoke tests**
- [x] **Step 3: Run Android unit tests / assemble and desktop distZip/installDist**
- [x] **Step 4: Run script checks and `git diff --check`**

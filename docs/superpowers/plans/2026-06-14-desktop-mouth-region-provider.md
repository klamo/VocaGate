# Desktop Mouth Region Provider Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Route desktop local-camera recognition through a replaceable mouth-region provider before connecting a heavier face/mouth model.

**Architecture:** Introduce a `DesktopMouthRegionProvider` interface that returns the mouth ROI and tracking status for a frame. Keep the current Java-only demo safe by using a centered face fallback provider, while making `DesktopMotionScoreAnalyzer` consume provider output instead of hard-coded lower-center sampling.

**Tech Stack:** Java 17, Swing desktop module, `BufferedImage`, shared `gate-core`, smoke tests.

---

### Task 1: Provider Contract Test

**Files:**
- Modify: `tools/DesktopCompanionSmokeTest.java`

- [x] **Step 1: Add a failing test with a fake mouth-region provider**
- [x] **Step 2: Verify compile fails because provider classes and result metadata do not exist**

### Task 2: Provider Types

**Files:**
- Create: `desktop/src/main/java/com/vocagate/desktop/vision/DesktopMouthRegion.java`
- Create: `desktop/src/main/java/com/vocagate/desktop/vision/DesktopMouthRegionProvider.java`
- Create: `desktop/src/main/java/com/vocagate/desktop/vision/CenteredMouthRegionProvider.java`

- [x] **Step 1: Implement immutable mouth region metadata**
- [x] **Step 2: Implement provider interface**
- [x] **Step 3: Implement centered fallback provider for the current development demo**

### Task 3: Analyzer Integration

**Files:**
- Modify: `desktop/src/main/java/com/vocagate/desktop/vision/DesktopMotionScoreAnalyzer.java`
- Modify: `desktop/src/main/java/com/vocagate/desktop/vision/DesktopRecognitionResult.java`
- Modify: `desktop/src/main/java/com/vocagate/desktop/app/DesktopCompanionApp.java`

- [x] **Step 1: Inject provider into analyzer**
- [x] **Step 2: Sample only the returned ROI**
- [x] **Step 3: Expose tracking/ROI label in recognition result**
- [x] **Step 4: Show ROI metadata in the desktop UI score line**

### Task 4: Documentation and Verification

**Files:**
- Modify: `README.md`
- Modify: `docs/DESKTOP_COMPANION.md`
- Modify: `docs/DESKTOP_COMPANION_DESIGN.md`

- [x] **Step 1: Document that the provider slot exists and the default is a fallback**
- [x] **Step 2: Run Java smoke tests and script checks**
- [x] **Step 3: Run Android/desktop Gradle verification**
- [x] **Step 4: Inspect desktop zip contents**

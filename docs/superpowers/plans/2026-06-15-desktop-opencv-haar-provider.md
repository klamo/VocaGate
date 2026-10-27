# Desktop OpenCV Haar Provider Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an optional OpenCV/Haar desktop mouth-region provider while keeping the existing centered fallback safe.

**Architecture:** Keep `DesktopMouthRegionProvider` as the stable contract. Add a factory that tries to create an OpenCV Haar face provider and wraps it with a cascading fallback; if OpenCV native loading, cascade resources, or face detection fail, desktop local-camera recognition continues through `CenteredMouthRegionProvider`.

**Tech Stack:** Java 17, JavaCPP OpenCV preset, bundled OpenCV Haar cascade XML, existing Swing desktop module and smoke tests.

---

### Task 1: Provider Selection Tests

**Files:**
- Modify: `tools/DesktopCompanionSmokeTest.java`

- [x] **Step 1: Add failing tests for cascading fallback and factory usability**
- [x] **Step 2: Verify pure Java compile fails because factory/cascade provider classes do not exist**

### Task 2: Provider Selection Implementation

**Files:**
- Create: `desktop/src/main/java/com/vocagate/desktop/vision/CascadingMouthRegionProvider.java`
- Create: `desktop/src/main/java/com/vocagate/desktop/vision/DesktopMouthRegionProviderFactory.java`
- Modify: `desktop/src/main/java/com/vocagate/desktop/vision/DesktopMotionScoreAnalyzer.java`

- [x] **Step 1: Implement cascading provider**
- [x] **Step 2: Implement reflection-based factory with safe fallback**
- [x] **Step 3: Make the analyzer default constructor use the factory**

### Task 3: OpenCV Haar Provider

**Files:**
- Modify: `desktop/build.gradle.kts`
- Create: `desktop/src/main/java/com/vocagate/desktop/vision/OpenCvHaarMouthRegionProvider.java`
- Add resource: `desktop/src/main/resources/opencv/haarcascade_frontalface_default.xml`

- [x] **Step 1: Add JavaCPP OpenCV desktop dependency**
- [x] **Step 2: Bundle the frontal-face Haar cascade XML**
- [x] **Step 3: Implement OpenCV face detection and estimate mouth ROI from the detected face**
- [x] **Step 4: Keep provider creation failure non-fatal**

### Task 4: Documentation and Verification

**Files:**
- Modify: `README.md`
- Modify: `docs/DESKTOP_COMPANION.md`
- Modify: `docs/DESKTOP_COMPANION_DESIGN.md`

- [x] **Step 1: Document OpenCV Haar provider as optional technical validation**
- [x] **Step 2: Run Java smoke tests and script checks**
- [x] **Step 3: Run Gradle Android/desktop verification**
- [x] **Step 4: Inspect desktop zip contents for OpenCV resources and libs**

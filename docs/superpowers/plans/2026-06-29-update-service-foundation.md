# VocaGate Update Service Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first secure remote-update foundation for VocaGate: signed static manifests, algorithm profiles, model/profile integrity checks, and Tencent Cloud deploy documentation.

**Architecture:** Keep the server as an untrusted static file host and put trust in client-side signature and hash verification. Add a small dependency-free Java update protocol package under `gate-core` so Android and desktop can later share the same manifest/profile validation logic. Ship a static server sample under `server/update-service` that can be copied to a Tencent Cloud CVM behind Caddy or Nginx.

**Tech Stack:** Java 17 standard library, Ed25519 signatures, SHA-256, Gradle multi-module project, static HTTP files, Caddy/Nginx deployment docs.

---

### Task 1: Update Protocol Smoke Test

**Files:**
- Create: `tools/UpdateProtocolSmokeTest.java`

- [ ] **Step 1: Write the failing smoke test**

Create a smoke test that:
- parses a manifest containing android and desktop profile assets;
- parses an Android algorithm profile into `GateConfig`;
- verifies SHA-256 for payload bytes;
- generates an Ed25519 keypair and verifies a detached signature.

- [ ] **Step 2: Run the smoke test to verify it fails**

Run:

```bash
mkdir -p /tmp/vocagate-update-smoke && javac -d /tmp/vocagate-update-smoke -cp gate-core/src/main/java tools/UpdateProtocolSmokeTest.java
```

Expected: compilation fails because update protocol classes do not exist yet.

### Task 2: Shared Update Protocol Core

**Files:**
- Create: `gate-core/src/main/java/com/vocagate/core/update/AlgorithmProfile.java`
- Create: `gate-core/src/main/java/com/vocagate/core/update/ManifestAsset.java`
- Create: `gate-core/src/main/java/com/vocagate/core/update/UpdateManifest.java`
- Create: `gate-core/src/main/java/com/vocagate/core/update/UpdateProtocolException.java`
- Create: `gate-core/src/main/java/com/vocagate/core/update/UpdateProtocolParser.java`
- Create: `gate-core/src/main/java/com/vocagate/core/update/UpdateVerifier.java`
- Create: `gate-core/src/main/java/com/vocagate/core/update/SimpleJsonParser.java`

- [ ] **Step 1: Implement only the fields required by the smoke test**

Use Java standard library only. Do not add external JSON dependencies.

- [ ] **Step 2: Run the smoke test**

Run:

```bash
mkdir -p /tmp/vocagate-update-smoke && javac -d /tmp/vocagate-update-smoke -cp gate-core/src/main/java tools/UpdateProtocolSmokeTest.java && java -cp /tmp/vocagate-update-smoke:gate-core/src/main/java UpdateProtocolSmokeTest
```

Expected: `UpdateProtocolSmokeTest passed`.

### Task 3: Static Update Server Sample

**Files:**
- Create: `server/update-service/public/v1/manifest.json`
- Create: `server/update-service/public/v1/manifest.json.sig.example`
- Create: `server/update-service/public/v1/profiles/android-mlkit-v1/2026-06-29.json`
- Create: `server/update-service/public/v1/profiles/desktop-mediapipe-v1/2026-06-29.json`
- Create: `server/update-service/public/v1/docs/update-api.md`
- Create: `server/update-service/Caddyfile.example`
- Create: `server/update-service/README.md`

- [ ] **Step 1: Add static examples**

Keep examples safe placeholders: no private key, no real model binary.

- [ ] **Step 2: Make examples parse with the smoke parser**

Update `UpdateProtocolSmokeTest` or add small assertions if needed.

### Task 4: User Documentation

**Files:**
- Create: `docs/UPDATE_SERVER.md`
- Modify: `README.md`
- Modify: `readme-zh.md`

- [ ] **Step 1: Document deployment**

Cover Tencent Cloud Tokyo CVM, security group ports, Caddy static hosting, offline signing, rollback behavior, and privacy/logging.

- [ ] **Step 2: Link docs from README files**

Add a short section to both English and Chinese README.

### Task 5: Verification

**Files:**
- No new files.

- [ ] **Step 1: Run Java smoke tests**

Run:

```bash
mkdir -p /tmp/vocagate-core-smoke && javac -d /tmp/vocagate-core-smoke -cp gate-core/src/main/java:app/src/main/java:desktop/src/main/java tools/CoreSmokeTest.java && java -cp /tmp/vocagate-core-smoke:gate-core/src/main/java:app/src/main/java:desktop/src/main/java CoreSmokeTest
mkdir -p /tmp/vocagate-update-smoke && javac -d /tmp/vocagate-update-smoke -cp gate-core/src/main/java tools/UpdateProtocolSmokeTest.java && java -cp /tmp/vocagate-update-smoke:gate-core/src/main/java UpdateProtocolSmokeTest
```

- [ ] **Step 2: Run Gradle build**

Run:

```bash
gradle :gate-core:compileJava :app:assembleDebug :desktop:assembleDist
```

- [ ] **Step 3: Run diff hygiene**

Run:

```bash
git diff --check
git status --short
```

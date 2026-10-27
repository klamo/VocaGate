# VocaGate Update Server

This document describes the first deployable remote-update foundation for VocaGate.

The update server is intentionally simple: it is a static HTTPS file host. It does not make trust decisions. The clients verify signed manifests, SHA-256 hashes, and compatible engine versions before applying any downloaded profile or model.

## Scope

The current implementation supports:

- Static update manifest samples under `server/update-service/public/v1/`.
- Android and desktop algorithm profile samples.
- Shared Java parsing and verification code under `gate-core/src/main/java/com/vocagate/core/update/`.
- Shared Java update-check client code under `gate-core/src/main/java/com/vocagate/core/update/`.
- Android and desktop settings for update checks, anonymous usage statistics, and update server URL.
- Daily automatic update checks, plus manual "check algorithm update" actions.
- Local profile JSON caching after successful verification.
- Android profile application for gate thresholds and score weights.
- Desktop local-camera MediaPipe profile application for gate thresholds.
- Ed25519 detached signature verification.
- SHA-256 integrity checks.
- Caddy deployment sample for a Tencent Cloud CVM.

It does not yet include:

- A hosted API service with a database.
- Automatic signing tools.

## Trust Model

Treat the server as untrusted.

```text
trusted:   app-bundled public key, built-in default profile
untrusted: update server, CDN, object storage, network, access logs
```

Update flow:

1. Client downloads `/v1/manifest.json` and `/v1/manifest.json.sig`.
2. Client verifies the manifest signature with the built-in Ed25519 public key.
3. Client selects compatible assets by `platform`, `engine`, `channel`, and `min_app_version`.
4. Client downloads profile/model assets.
5. Client verifies SHA-256 and optional per-asset signatures.
6. Client applies the profile/model only after all checks pass.
7. If any check fails, client keeps the built-in default algorithm.

Signing private keys must stay off the server. Sign manifests and assets on a local machine, then upload the signed public files.

## Static File Layout

The sample service lives at:

```text
server/update-service/
├── Caddyfile.example
├── README.md
└── public/
    └── v1/
        ├── manifest.json
        ├── manifest.json.sig.example
        ├── docs/update-api.md
        ├── profiles/
        │   ├── android-mlkit-v1/2026-06-29.json
        │   └── desktop-mediapipe-v1/2026-06-29.json
        └── models/
            └── desktop-mediapipe/
```

Profile hashes in the sample manifest are real. Signature placeholders must be replaced before production use.

## Tencent Cloud Tokyo CVM Deployment

Recommended first deployment:

- Tencent Cloud CVM in Tokyo.
- Ubuntu LTS.
- Caddy or Nginx.
- Domain such as `updates.vocagate.app`.
- Static files copied to `/srv/vocagate-updates/public`.

Suggested directory layout:

```text
/srv/vocagate-updates/
├── Caddyfile
├── logs/
└── public/
    └── v1/
```

Example:

```bash
sudo mkdir -p /srv/vocagate-updates/logs
sudo cp -R server/update-service/public /srv/vocagate-updates/public
sudo cp server/update-service/Caddyfile.example /srv/vocagate-updates/Caddyfile
```

Edit the domain in `Caddyfile`, then run Caddy as a service.

## Security Group

Only open:

```text
80/tcp   HTTP, for certificate issuing or redirect
443/tcp  HTTPS update service
22/tcp   SSH, restricted to your own IP or VPN
```

Do not expose databases, admin panels, or broad port ranges.

## Server Hardening

- Disable SSH password login.
- Disable root SSH login.
- Use an unprivileged deploy user.
- Keep the update public directory separate from private keys.
- Rotate access logs.
- Keep only coarse logs needed for approximate daily update checks.
- Do not upload audio, video, face images, face landmarks, key bindings, game names, or personal identifiers.

## Cache Policy

Use:

```text
Cache-Control: no-store
```

for:

```text
/v1/manifest.json
/v1/manifest.json.sig
```

Use:

```text
Cache-Control: public, max-age=31536000, immutable
```

for versioned profiles and models whose URL changes on every update.

## Logging And Approximate Usage

For the first version, approximate usage can come from HTTPS access logs.

Clients may request:

```text
GET /v1/manifest.json?platform=android&app_version=0.1.0&channel=stable
```

Count by day, platform, app version, and coarse IP. Do not issue a stable user id unless the privacy policy and UI explicitly explain it.

## Developer Verification

Run:

```bash
mkdir -p /tmp/vocagate-update-smoke
javac -d /tmp/vocagate-update-smoke \
  -cp gate-core/src/main/java \
  tools/UpdateProtocolSmokeTest.java
java -cp /tmp/vocagate-update-smoke:gate-core/src/main/java UpdateProtocolSmokeTest
```

The smoke test parses the sample manifest/profile files and verifies the profile hashes in `manifest.json`.

Run the client update-check smoke test:

```bash
mkdir -p /tmp/vocagate-update-client-smoke
javac -d /tmp/vocagate-update-client-smoke \
  -cp gate-core/src/main/java \
  tools/UpdateClientSmokeTest.java
java -cp /tmp/vocagate-update-client-smoke:gate-core/src/main/java UpdateClientSmokeTest
```

The client smoke test verifies daily scheduling, manifest URL generation, profile download, SHA-256 validation, and safe hash-mismatch failure.

Run the profile application smoke test:

```bash
mkdir -p /tmp/vocagate-profile-application-smoke
javac -d /tmp/vocagate-profile-application-smoke \
  -cp gate-core/src/main/java:app/src/main/java:desktop/src/main/java \
  tools/UpdateProfileApplicationSmokeTest.java
java -cp /tmp/vocagate-profile-application-smoke:gate-core/src/main/java:app/src/main/java:desktop/src/main/java UpdateProfileApplicationSmokeTest
```

The profile application smoke test verifies that Android user tuning uses a remote profile as its gate baseline, Android score weights come from the profile, and desktop MediaPipe local-camera gate settings can use the same profile mechanism.

## Next Implementation Steps

1. Add the production public key and require manifest signatures in release builds.
2. Add a local signing tool so release files can be generated safely without private keys touching the server.
3. Add optional COS/CDN publishing scripts once model files move out of the CVM.

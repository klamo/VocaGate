# VocaGate Update API v1

This API is intentionally static. The server is an untrusted file host. Clients must verify signatures and hashes before applying any profile or model.

## Endpoints

```text
GET /v1/manifest.json
GET /v1/manifest.json.sig
GET /v1/profiles/<engine>/<version>.json
GET /v1/models/<engine>/<file>
```

Clients may append query parameters for coarse logging:

```text
platform=android|desktop-macos|desktop-windows
app_version=0.1.0
channel=stable
```

The server must not require user identity for update checks.

## Trust Model

- The manifest is signed offline with Ed25519.
- The client stores the public key inside the app.
- Every asset in the manifest has a `sha256` value.
- Optional per-asset detached signatures may be used for profile/model files.
- If verification fails, the client must keep the built-in default profile.

## Manifest Fields

| Field | Meaning |
|---|---|
| `schema_version` | Protocol version. Current value: `1`. |
| `channel` | Release channel, such as `stable` or `beta`. |
| `generated_at` | UTC timestamp for diagnostics. |
| `expires_at` | Optional UTC timestamp. Clients should refuse stale manifests. |
| `assets` | Profile/model entries. |

## Asset Fields

| Field | Meaning |
|---|---|
| `id` | Stable asset id. |
| `kind` | `profile` or `model`. |
| `engine` | `android-mlkit-v1` or `desktop-mediapipe-v1`. |
| `platform` | `android`, `desktop`, `desktop-macos`, or `desktop-windows`. |
| `version` | Asset version. |
| `min_app_version` | Oldest compatible app version. |
| `url` | Relative or absolute download URL. |
| `sha256` | Lowercase SHA-256 hex of the asset bytes. |
| `signature` | Optional detached Ed25519 signature of the asset bytes. |
| `size_bytes` | Optional expected size. |

## Privacy

The official service should only log coarse update checks. It must not upload audio, video, face images, face landmarks, key bindings, or game/app names.

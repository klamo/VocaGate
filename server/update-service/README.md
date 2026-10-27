# VocaGate Static Update Service

This folder is a deployable static update-service sample.

## What It Hosts

```text
public/v1/manifest.json
public/v1/manifest.json.sig
public/v1/profiles/
public/v1/models/
public/v1/docs/update-api.md
```

The server is not trusted. Clients trust only the built-in public key plus verified hashes/signatures.

## Tencent Cloud CVM Layout

Recommended server layout:

```text
/srv/vocagate-updates/
├── Caddyfile
├── logs/
└── public/
    └── v1/
```

Copy `public/` to `/srv/vocagate-updates/public` and adapt `Caddyfile.example`.

## Security Rules

- Do not store signing private keys on the server.
- Only open ports `80/tcp`, `443/tcp`, and a restricted `22/tcp`.
- Disable SSH password login.
- Keep logs minimal and rotate them.
- If the server is compromised, clients must reject modified files because signatures and hashes will fail.

## Production Checklist

- Replace placeholder SHA-256 values in `manifest.json`.
- Replace placeholder signatures in `manifest.json` and `manifest.json.sig`.
- Upload real model files only after hashing and signing them locally.
- Test with a client that has the expected public key.

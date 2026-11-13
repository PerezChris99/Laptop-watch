# LaptopWatch Python gateway — v0.2 development

This directory contains a **real, tested Python control gateway prototype**, not the complete user-facing desktop product. It has no Tauri UI or Android paired client yet. **Never distribute this as a finished security system.**

## What exists

- Ed25519 pairing registry with **physical approval via the local CLI** (not remotely provisioned).
- Signed requests bound to method, path, body hash, timestamp, unique nonce, phone identity.
- Persistent SQLite replay denial, per-peer rate limiting and revocation.
- Signed, hash-chained audit log; key/cert provisioning; self-signed TLS cert with SHA-256 fingerprint.
- FastAPI HTTPS control gateway: signed status, lock, alarm, arm/disarm, consent-gated still snapshot.
- OS lock adapter for Windows, macOS and Linux; best-effort local alarm; on-demand JPEG capture with immediate camera release.
- No default LAN listener. `serve` binds loopback unless `--listen-lan` is explicitly requested **after one local pairing**.

## Local developer validation

```sh
cd desktop/src-python
python -m pip install -e '.[test,media]'
python -m pytest -q
python -m laptopwatch.cli --state-dir ./local-dev init
```

The test suite uses mocked OS controls and fake camera data and **does not** establish real camera/OS/network success.

To provision a phone key manually, create an Ed25519 keypair on the phone; then on the laptop run:

```sh
python -m laptopwatch.cli --state-dir ./local-dev pair --device-id UUID --public-key-b64url PUBLIC_KEY
```

Approve by typing `PAIR` locally only after verifying the phone. This is a developer-only substitute for the unimplemented QR pairing UI. Then:

```sh
python -m laptopwatch.cli --state-dir ./local-dev serve --port 8765
```

To intentionally expose a paired TLS gateway on LAN, use `--listen-lan`. The client **must** verify/pin the printed TLS certificate fingerprint and sign every request. The legacy Android app does **not** support this yet. Do not try to use the old PIN for the new gateway.

**Secure key storage still pending:** this development implementation writes PEM key files with restricted local permissions. Production needs OS-backed key storage and certificate/key rotation. Browser UI, WebRTC, mDNS/ICE/TURN, person detector, packaging, Android integration, hardware tests and release approvals are **not implemented**.

**Local validation (2026-11-13):** 24 Python tests passed in an isolated working copy, including signed controls, consent gates and certificate generation. This is not a third-party security assessment.

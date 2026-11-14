# LaptopWatch paired control protocol — extracted contract (LW1)

**Authority:** `desktop/src-python/laptopwatch/security.py`, `pairing.py`, `gateway.py`; Kotlin `PairingCrypto.kt` and `SecureLaptopGateway.kt` are the existing mobile reference. This document records source protocol, not evidence of an executed LAN test.

## Signed request

- Exact ASCII transcript: `LW1\\nMETHOD\\nPATH\\nSHA256(raw_body).hex()\\nUTC_EPOCH_SECONDS\\nBASE64URL_NONCE\\nDEVICE_UUID\\n`.
- Method uppercased; allowed `GET POST PUT DELETE` by the verifier; actual gateway only exposes the methods below.
- Path must be `/api/` followed by at most 180 characters in `[A-Za-z0-9/_-]`; never `//`, `..`, or a query string. **Hash raw request bytes; never reserialize JSON after signing.**
- The nonce is **16 cryptographically random bytes**, canonical unpadded base64url; timestamp is decimal UNIX seconds. Maximum ±60 seconds drift. Replays are refused by a persistent, SQLite nonce cache, even across restarts.
- Signature is Ed25519 over the exact transcript, unpadded base64url. Headers: `X-LaptopWatch-Device`, `X-LaptopWatch-Nonce`, `X-LaptopWatch-Timestamp`, `X-LaptopWatch-Signature`.
- Body is at most 64 KiB at gateway middleware (and 4096 bytes for pairing); use empty raw body, **not `{}`**, for empty POST commands. No URL credentials. Signed controls require a locally approved non-revoked phone and fingerprint-pinned HTTPS.
- Reference error status: middleware 401 for invalid/expired/replayed signature, 403 for LAN TLS violation, 413 for large bodies; handlers 400 for validation, 404 unavailable evidence/location, 409 disarmed/unavailable media state, 503 hardware/service unavailable. The current gateway does not expose a machine-readable universal error-code enum. Do not fabricate one.

## Explicit physical pairing

Signed QR URI: `laptopwatch://pair?v=1&id=...&host=...&port=...&pk=...&fp=...&nonce=...&exp=...&sig=...`, numeric private IPv4 address, 1024..65535 port, 64-char lowercase SHA256 DER certificate fingerprint, expires in 120 seconds.

QR transcript: `LWPAIR1\\nDESKTOP_UUID\\nIPV4\\nPORT\\nPUBLIC_KEY_B64URL\\nCERT_SHA256_HEX\\nCHALLENGE_B64URL\\nEXPIRY_SECONDS\\n`. The public key in QR verifies the QR signature; trust comes from **in-person scanning**, not external PKI.

Phone proof: `LWPAIR-PROOF1\\nPHONE_UUID\\nPHONE_PUBLIC_KEY_B64URL\\nQR_CHALLENGE\\n`. `POST /pair/request` uses the exact JSON keys `device_id, pubkey, nonce, proof, label`; response is `{"pending_id": UUID, "status": "AWAITING_LOCAL_APPROVAL"}`. Desktop explicitly approves pending pairing locally before the phone may sign a working `GET /api/status`. Pin the exact certificate and never silently rotate it.

## Observed gateway routes

| Route | Method | Signed | Request body | Response and constraints |
|---|---|---|---|---|
| `/api/status` | GET | yes | none | `online, motion_armed, location: null, subject_identified: null, media_transport, media_active, person_detector_configured, person_monitor_operational, person_monitor_error` |
| `/api/config/armed` | POST | yes | `{"armed": boolean}` | `success, armed` |
| `/api/lock`, `/api/alarm` | POST | yes | none | `success: true` or 503 |
| `/api/warning/tts` | POST | yes | `{"message": text}` 1-400 chars | `success` or 503 |
| `/api/camera/frame` | GET | yes | none | JPEG bytes or 409/503; camera requires armed |
| `/api/camera/photo` | POST | yes | none | `success, id` plus evidence metadata |
| `/api/camera/record/start` | POST | yes | none | `success, recording, id, max_duration_seconds` |
| `/api/camera/record/stop` | POST | yes | none | clip metadata or 409 |
| `/api/webrtc/offer` | POST | yes | `{"sdp": string, "type":"offer", "include_audio": false}` | Actual aiortc SDP answer; mic off by default |
| `/api/webrtc/stop` | POST | yes | none | `success: true` |
| `/api/evidence/{photo|video}/{32-lowercase-hex-id}` | GET | yes | none | JPEG/MP4 attachment, max gateway path 180 chars |
| `/api/location/last-seen` | GET | yes | none | real fix or 404/503; no invented LIVE coordinates |
| `/api/location/refresh` | POST | yes | none | real receiver fix or 503 |
| `/api/incidents/query` | POST | yes | `{"after_id": 0, "limit": 100}` | `events, next_after_id`; configured guard required |
| `/api/incidents/clear` | POST | yes | none | `deleted`; destructive |
| `/api/config/person-monitor` | POST | yes | `auto_snapshot, auto_record, auto_alarm` booleans | configured model required |

**No automatic retries** for lock/alarm/photo/record, and no offline command queue. GET status/last-seen can retry with bounded jitter only through the pinned connection. Desktop owner UI uses Tauri IPC, not the signed network gateway. An added loopback bearer-token owner API needs separate tests before use.

## Executed deterministic fixtures

`shared/protocol-vectors/ed25519-v1.json`: three LW1 signed requests, QR invitation and phone proof, all with one non-secret deterministic test seed. Python `cryptography` produced and checked signatures; Node 22 built-in `crypto` reproduced them byte-identically and rejected appended tampering on 2026-11-14. The committed pytest vectors consume the exact Python verifier but **that repository pytest suite has not yet been executed in a checked-out environment**.

## Still unverified

Real Android TLS certificate pinning, cross-client signing, WebRTC hardware, rate-limit behavior, revocation and 24-hour soak. No claim that the newly proposed React Native client is compatible until signed interop tests run with its actual crypto library and physical transport.

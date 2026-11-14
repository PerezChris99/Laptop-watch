# LaptopWatch Python companion — secure development gateway

> **Development component.** Not a finished desktop application or an Android-compatible released product. Do not expose the PIN-based legacy Flask app.

## Implemented source

- Ed25519 signed controls with per-device pairing approval, replay protection, revocation and rate limiting.
- HTTPS gateway with explicit local identity provisioning and a development-only CLI; binds to loopback by default. LAN requires an enrolled device and explicit `--listen-lan`.
- Signed, locally hash-chained control audit records.
- OS lock/alarm adapter (hardware support unverified).
- **Evidence capture:** armed-only JPEG still, saved JPEG photo, bounded MP4 clip start/stop, signed evidence download. Photos and clips remain in local `state-dir/evidence/`; no hidden recording or audio track.
- **Real GNSS:** opt-in `gpsd` receiver, rejects missing/stale/invalid fixes or unknown accuracy, SQLite last-known history, pending delivery queue and optional periodic GNSS sampling while process runs.
- **Offline state:** last recorded fix persists; stale/fresh determined by timestamp. Remote delivery requires a future paired sender/backend and is not functional.

## Start a local development session

```bash
cd desktop/src-python
python -m venv .venv
# activate virtual environment
python -m pip install -e '.[test,media]'
python -m pytest -q
python -m laptopwatch.cli --state-dir ./local-dev init
```

To pair one developer phone/client, create an Ed25519 key pair outside the repository, then run:

```bash
python -m laptopwatch.cli --state-dir ./local-dev pair --device-id UUID --public-key-b64url PUBLIC_KEY
```

The user must type `PAIR` at the laptop. Pairing is not yet QR-based. Verify the desktop's SHA-256 TLS fingerprint in the client out of band.

Start the loopback-only HTTPS gateway:

```bash
python -m laptopwatch.cli --state-dir ./local-dev serve --port 8765
```

For **owner-approved real GNSS receiver access**, configure a local `gpsd` daemon with an actual GNSS device and opt in:

```bash
python -m laptopwatch.cli --state-dir ./local-dev serve --enable-gpsd
```

`--listen-lan` exposes the signed TLS listener only after local pairing. A signed request client must pin the certificate. The current Android app does **not** have that client and cannot use this gateway yet.

## Signed API

| Method | Route | Result |
|---|---|---|
| GET | `/api/status` | State; no invented hardware location |
| POST | `/api/config/armed` | JSON `{"armed":true}` or `false` |
| POST | `/api/lock`, `/api/alarm` | Explicit owner action |
| GET | `/api/camera/frame` | On-demand JPEG while armed |
| POST | `/api/camera/photo` | Saved local JPEG + random evidence ID |
| POST | `/api/camera/record/start` | Start a bounded MP4, return ID |
| POST | `/api/camera/record/stop` | Finalize MP4 and return stats |
| GET | `/api/evidence/photo/{id}` | Signed private JPEG download |
| GET | `/api/evidence/video/{id}` | Signed private MP4 download |
| GET | `/api/location/last-seen` | Genuine stored fix with source/age/stale flag |
| POST | `/api/location/refresh` | Try real GNSS; unavailable without receiver |

All requests require authenticated canonical request signatures and encrypted transport for remote operation. The camera must be armed explicitly. No authentication tokens/PINs in query parameters.

**Storage:** location `state-dir/last_seen.db` when GNSS enabled; pictures/videos `state-dir/evidence/`; audit `state-dir/audit.jsonl`. The system does **not** automatically upload raw video, photo or coordinates. Protect device with full-disk encryption. The database and key material have not yet been migrated to OS-keychain backed encryption.

## Verification status

- **13 tests executed locally** for newly added tracking/evidence modules (mocked camera and GNSS) using Python 3.13; all passed.
- Existing Python gateway/security tests exist but need a full reproducible, independent suite run after integration.
- Real webcam MP4 decoding, GPS accuracy measurement, OS lock/alarm, Android HTTPS pairing, signing and WAN operations are **not verified**.
- Sleep and shutdown stop user-mode polling. Remote immediate reporting is **not implemented** until a trusted endpoint plus reconnect event wiring exists.

See [root README](../../README.md), [café scenario](../../docs/SCENARIOS.md), [location feasibility](../../docs/LOCATION_AND_RECOVERY.md), [status](../../docs/STATUS.md).

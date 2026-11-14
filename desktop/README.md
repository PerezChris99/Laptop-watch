# LaptopWatch desktop (Tauri 2 + React + Python sidecar)

This is the first integrated desktop UI implementation. It offers a tray-resident
native window, local identity initialization, short-lived pairing QR, explicit
phone approval and revocation, local evidence listing, and opt-in launch-at-login.

**This is not a signed, tested consumer binary.** Tauri compilation, WebKitGTK
runtime and Python sidecar lifecycle still require real Windows/macOS/Linux
builds; sign/notarize outside the repository.

## Project layout

- `src-ui/`: React/JSX UI, QR canvas, pairing approvals, settings, logs/evidence.
- `src-tauri/`: Rust native shell, allowlisted IPC commands and system tray.
- `src-python/`: security/camera/GNSS Python engine.
- `scripts/prepare_sidecar.py`: create host architecture-matched PyInstaller
  executable plus platform icons from the original checked-in image.

## Prerequisites

Node 22+, Rust 1.90+, Tauri 2 desktop prerequisites (WebView2 on Windows,
Xcode command-line tools on macOS, WebKitGTK and native dependencies on Linux),
Python 3.11+, Pillow, PyInstaller. Linux and macOS code signing require
legitimate owner certificates.

## Reproducible developer flow (on each target OS)

```bash
python -m pip install -e 'desktop/src-python[test,media]' pyinstaller pillow
python desktop/scripts/prepare_sidecar.py
cd desktop
npm install
npm run tauri dev
```

For a production release: `npm run tauri build` on **each** target OS after
preparing the sidecar, signing credentials and versioned dependencies. The
script detects the native Rust host triple and builds
`src-tauri/binaries/laptopwatch-sidecar-<TARGET>` (plus .exe on Windows).
It generates the required ICO/ICNS/PNG icons from
`docs/images/app_icon.jpg`. No signing keys or generated binaries enter Git.

## Pairing flow

1. Install desktop, launch LaptopWatch and select **Pair a phone**.
2. UI detects a private IPv4; user can correct it if the machine has multiple
   interfaces. Choose **Create pairing QR and start secure listener**.
3. Scan with Android; the phone validates the desktop's signed QR and exact
   self-signed TLS certificate fingerprint, then submits an Ed25519 proof.
4. Review phone identity and click **Approve** on the laptop within the
   two-minute pairing window. The phone taps **Verify approval**.
5. Only then may the paired phone send signed arm, lock, alarm, photo, video
   and last-seen requests.

No PINs, trust-all TLS, silent phone enrollment or anonymous camera endpoints.
LAN listener is only started after the owner requests pairing.

## Limitations and hard gates

- No mature audio/video WebRTC, mDNS/ICE/TURN, native person detector or remote
  alert service yet. Signed HTTPS still JPEG and bounded MP4, not live streaming.
- Real Windows/macOS/Linux camera/lock APIs, tray/autostart and installer package
  need physical validation. No signed/notarized binaries have been produced.
- Sidecar private keys are in permission-restricted development files, **not**
  OS hardware keystore; owner identity and key-rotation hardening remain.
- `serve` requires an active local QR session or existing paired phone to
  bind to LAN; if the app or sidecar terminates, relaunch and generate a new QR.
- FastAPI/uvicorn is currently a native HTTPS app server; production WAF,
  media quotas, update integrity and forensic audit tests are pending.
- The QR address is LAN/private; a malicious public IP cannot be enrolled
  through the default pairing QR.

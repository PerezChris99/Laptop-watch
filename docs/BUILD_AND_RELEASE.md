# LaptopWatch — reproducible build and acceptance runbook

**Status: development source only.** Last verified focused test run on the
pre-release evidence/GNSS/QR modules: 15 tests passed in a Python 3.13
Linux container with mocked webcam/GPS. The **complete integrated HEAD
has not been built or tested on Android, Windows, macOS or Linux**.
Do not distribute unsigned or unknown-good binaries as commercial releases.

## 1. Development prerequisites

| Target | Required |
|---|---|
| Python sidecar | Python 3.11–3.13, pip, camera permission, optionally GNSS receiver through gpsd |
| Android | JDK 17+, Android SDK 36.1, Gradle 9.3.1, Android Studio + real Android 8+ phone |
| Desktop | Node 22+, Rust toolchain, Tauri 2 prerequisites, PyInstaller, Pillow |
| Linux desktop | WebKitGTK, GTK3, appindicator, OpenSSL, native audio libraries |
| Mac desktop | Xcode command-line tools; valid Apple Developer ID for distribution |
| Windows desktop | WebView2, Microsoft Windows SDK, signing certificate for reputation |
| Self-hosted relay | Docker/Compose, owner-controlled DNS, real HTTPS, secured disk |

The Gradle wrapper binary and launcher scripts are not yet checked into the
repository; this is a release blocker. Generate them from a **known-good
Gradle 9.3.1 installation** (`gradle wrapper --gradle-version 9.3.1`),
check the official distribution hash and commit wrapper artifacts. Do not
pretend `./gradlew` works until that is done.

## 2. Python and relay security tests

```bash
cd desktop/src-python
python -m venv .venv
# activate environment appropriate to your OS
python -m pip install -e '.[test,media,discovery]'
python -m pytest -q
cd ../..
PYTHONPATH="desktop/src-python:services/location-relay" \
  python -m pytest -q services/location-relay/tests
```

On Windows PowerShell, use
`$env:PYTHONPATH="desktop/src-python;services/location-relay"` before
running the relay suite.

Test authenticated QR proof, expired invitation, signed control, nonce replay,
camera capture/disarm, JEPG and MP4 decode, encrypted location storage,
tamper detection, TLS redirect refusal, remote report idempotency, role
separation and owner deletion. A passing simulated test is not a device test.

## 3. Android build and release gates

After bootstrapping the standard Gradle wrapper or using Gradle 9.3.1:

```bash
gradle :app:assembleDebug
gradle :app:testDebugUnitTest
gradle :app:lintDebug
gradle :app:connectedDebugAndroidTest  # physical Android device connected
```

Do not commit signing keys. Release APK/AAB requires owner-provided
`KEYSTORE_PATH`, `STORE_PASSWORD` and `KEY_PASSWORD`, Play Console
approval, privacy policy, data safety declaration and release keystore backup.
The package ID is a legacy ID; changing it breaks update identity and
requires a migration decision before store launch.

Acceptance in a real café-like location:
1. Owner opens the desktop app, opts into visible camera access and starts pairing.
2. Android scans a QR, confirms desktop identity/certificate pin, and sends
   Ed25519 public key proof; desktop owner explicitly approves.
3. Android signed HTTPS arm succeeds; camera does not capture while disarmed.
4. JPEG photo is captured, downloaded privately and rendered without URL PINs.
5. MP4 clip is started, played, stopped, decoded and saved with actual frame
   count; camera release verified; failure never shows a fake success.
6. Live WebRTC video works in both directions of ICE signaling, then optional
   microphone path works **only after** Android user grants mic permission.
7. Repeat with denied camera/mic permissions, unplugged camera, bad QR, revoked
   phone, expired TLS certificate, wrong fingerprint, clock skew, replayed
   requests, disarm race and network loss.
8. Validate thermal/power limits and OS screen-lock behavior in a closed-lid
   state; do not disable safety lid policies.

## 4. Desktop build

```bash
python -m pip install -e 'desktop/src-python[test,media,discovery]' pyinstaller pillow
python desktop/scripts/prepare_sidecar.py
cd desktop
npm ci              # after a verified package-lock.json is generated
npm run build
npm run tauri build # needs platform toolchain and signing
```

**Current blocker:** No generated `package-lock.json` or signed platform
installers have been committed. For first-time dependency resolution use
`npm install` and subsequently commit a reviewed lockfile before reliable
`npm ci` builds. Running Tauri dev without the sidecar will fail.

Verify Windows 10/11, macOS Apple Silicon/Intel and Ubuntu/Debian versions
in scope; signing, notarization, permissions, AV false positives, upgrades,
uninstall cleanup and OS autostart behavior are independent release gates.

## 5. Opt-in GNSS and remote last-seen

Ordinary laptops usually do not have actual GPS hardware. A real GNSS
receiver connected to a locally running gpsd daemon is required for the
current source provider. Invalid, future or missing fixes are rejected.

On laptop, generate physical pairing invitation and copy the desktop public
key from QR fields `id` and `pk`. On Android, use "Copy phone public key
for relay enrollment." Enroll **public keys only** on the self-hosted
private relay (see [its README](../services/location-relay/README.md)).
Set a real HTTPS domain, DNS, TLS and encrypted server storage, then
enable gpsd and optional relay from the desktop Settings screen before
starting the gateway.

Test an Internet outage, persisted queue, reconnection, signed delivery
receipt, phone fetch with laptop offline, revocation and encrypted store
reopen. Only remote-received records can be fetched after theft.
Short Internet blips may be missed. No Python camera/GNSS code runs after
power-off and deep sleep; use OS Find My or independently powered hardware.

**Storage key backup:** securely back up the relay's `location.location-key`
alongside encrypted backups; a lost key makes history unrecoverable. Do not
store backups beside publicly served Caddy content.

## 6. Release stop conditions

Stop release for any HIGH/CRITICAL issue: unsigned or unverifiable binary,
insecure HTTP/PIN fallback, false live GPS, incompatible Android/desktop
pairing, missing user consent, photo/video failure, silent retention,
crashed background process, revoked key still accepted, PII leakage,
unverified push alerts, missing hardware test or illegal store listing.

A production-ready declaration requires signed installers, complete
device/test matrix evidence, independent security review, legal approval,
commercial accounts and live operational acceptance. Source commits,
documentation and a mock test suite do not meet that standard.

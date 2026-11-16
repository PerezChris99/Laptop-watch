# LaptopWatch — separate verified-build and hardware acceptance runbook

**As of 14 November 2026:** owner authorized removing the former Kotlin
controller while still requiring a separate native build, external service,
physical-device, security-review and release acceptance phase. The commands below are **not recorded as passed**.
A GitHub merge or source syntax check is NOT evidence of installed app parity.

## One-command conservative verification report

After a trusted checkout, `python ops/verify_release.py --mode full
--json verification-results.json` executes every locally supported
source/Python/Node/Rust/Android/iOS gate in order and reports missing
locks, SDKs and OS support as **BLOCKED**, not PASS. It intentionally
returns nonzero until all required gates including the manual real-world
release acceptance are completed. `--mode source` runs only the quick
offline source checks; a green source result is **not a release verdict**.

The runner's optional JSON report contains command statuses, not raw
process logs or customer secrets. Carry out physical and legal acceptance
with separately signed, reviewed evidence.

## 0. Clean checkout, source-policy checks

```bash
git clone https://github.com/PerezChris99/Laptop-watch.git
cd Laptop-watch
git fetch origin
git checkout main
python ops/source_preflight.py
python design/generate.py --check
```

Use this full checkout when a trusted host can resolve and reach GitHub.
Source preflight checks exact native contracts, real files, client
JavaScript-only policy, design tokens, Python syntax and installed Node's
non-JSX JavaScript syntax. It does not compile native code.

## 1. Python signed HTTPS gateway and relay

```bash
python -m venv .venv
# macOS/Linux: source .venv/bin/activate
# Windows: .venv\Scripts\Activate.ps1
python -m pip install -e './desktop/src-python[test,media,discovery]'
python -m pytest -q desktop/src-python/tests
python -m pip install -e './services/location-relay[test]'
python -m pytest -q services/location-relay/tests
python -m pip install pip-audit
pip-audit
```

Both Python packages now declare explicit `[test]` extras in their
`pyproject.toml` files, and the relay also declares optional `[fcm]` dependencies.
Review and lock actual resolved versions before accepting any build. At minimum
run the signed gateway, Ed25519 replay, pairing, audit-tamper, owner-health
and new signed GNSS history tests. Capture complete non-secret output.

## 1b. Commercial billing contract (pilot, no live merchant deployment)

```bash
python -m pip install -e './services/billing[test]'
python -m pytest -q services/billing/tests
python ops/lock_dependencies.py  # nonzero until authentic locks exist
```

Paddle webhooks require verified `transaction.completed` alongside
subscription state for paid leases. Refund/chargeback adjustments suspend
leases. Registered devices can request signed checkout authorizations,
but live Paddle merchant credentials, buyer onboarding, payment UI,
client-side entitlement enforcement, accurate renewal reconciliation and
real financial acceptance remain **BLOCKED**. See
[services/billing/README.md](../services/billing/README.md).

## 2. React Native JavaScript mobile

```bash
cd mobile
npm install --package-lock-only
# REVIEW package-lock.json in the PR, pin transitive packages
npm ci
npm run test:protocol
npm run test:ui
npm run lint
npm run check:js
python ../ops/source_preflight.py
cd android
./gradlew --version
./gradlew assembleDebug test lint
cd ..
npm run android
```

React Native Android/iOS project **source and the official Android 0.88.x
Gradle wrapper are now present**. The tracked wrapper targets Gradle 9.4.1
with an official SHA-256 distribution checksum; the Android build uses
NDK 28.2.13676358, consistent with the React Native 0.88 template.
Toolchain alignment in source is not proof of an executed native build. Native modules are registered. These
commands are still unexecuted because this runner lacks a local checkout,
npm registry dependencies, Android SDK/NDK and devices. Review RN package
versions, Kotlin/Swift API signatures, Gradle plugins, dynamic dependencies
and target SDK before interpreting any build as green. Keep Firebase
compile-time OFF until legitimate owner credentials and relay enrollment.

On a macOS Xcode host:

```bash
cd mobile
npm ci
cd ios
pod install --repo-update
xcodebuild -list -project LaptopWatchMobile.xcodeproj
xcodebuild -workspace LaptopWatchMobile.xcworkspace \
  -scheme LaptopWatchMobile -configuration Debug \
  -destination 'generic/platform=iOS Simulator' build
```

Validate iOS camera/local-network permission prompts, Keychain, evidence
exports and native TLS pinning on a real handset; a simulator alone is not
sufficient. iOS does not claim Android NSD/connectedDevice FGS parity.

## 3. Tauri/Rust desktop UI, supervision and sidecar

```bash
cd desktop
npm install --package-lock-only
# REVIEW desktop/package-lock.json
npm ci
npm run check:tokens
npm run test:ui
npm run lint
npm run check:js
npm run build
python scripts/prepare_sidecar.py
cd src-tauri
cargo generate-lockfile
# REVIEW src-tauri/Cargo.lock
cargo build --locked
cargo test --locked
```

Test Tauri tray, single-instance, autostart opt-in, running sidecar without
displayed window, crash backoff (five in 60 seconds), clean stop, restart,
Windows SmartScreen/firewall and OS shutdown. Probe owner-only loopback
health with the random per-launch bearer, verify no unauthenticated owner
control routes, no secret logs, and valid phone signed LAN HTTPS controls.

## 4. Physical feature parity

- Scan a newly signed QR on a real phone and approve on the actual laptop;
  reject expired QR, replay, unapproved phone, wrong TLS fingerprint and
  revoked pairing.
- Execute arm/disarm, lock, alarm, TTS warning, camera still JPEG, MP4,
  evidence list/pinned download/preview/owner export/local deletion.
- Negotiate verified encrypted WebRTC, stop/restart, wrong SDP/ICE and
  interrupted network; test silent default, consented laptop listening
  and optional duplex mic under permission deny/allow.
- Supply a licensed, SHA256-verified person detection model and consented
  scene; measure precision, false alarms and audio/record triggers.
- Test Android NSD rediscovery after laptop Wi-Fi IP changes and forged mDNS.
- Supply actual gpsd receiver; test on-demand LIVE (30s), stale LAST SEEN,
  bounded signed history, offline and owner relay location/incident queries.
- Configure real Firebase, FCM delivery, app foreground/background,
  security of signed relay enrollment/revocation, notifications and Android
  8–current foreground policy; test power off/Doze/reboot/battery.
- Perform actual 24-hour reliability soak and measure installer bytes,
  CPU/RSS, start latency, p95 signed action, audio/video latency and recovery
  using labeled hardware. Do not invent numbers.

Existing reviewable source files `mobile/e2e/` contain starter Maestro
flows, not proven acceptance tests; expand and run them on real hardware.

## 5. Security, supply chain, commercial signoff

- Lock dependencies and reproduce from a clean separate machine; audit
  pip/npm/Cargo/Gradle/Pods and create reviewed SBOM.
- Exercise incorrect TLS cert, signed nonce persistence, forgery/replay,
  user revocation, OS Keychain encryption loss, SQLCipher migration and
  incorrect native private evidence export path.
- Independent privacy/security review; verify signed audit events are
  integrity checked before display. Confirm all personal data retention,
  encrypted backup/deletion and no simulated evidence.
- Complete installer and notarization tasks in
  `docs/SIGNING_AND_NOTARIZATION.md`.
- Owner must supply legal/privacy/EULA/license, domain/relay/Firebase and
  signing accounts, store compliance and support procedures.
- Owner has **explicitly authorized and executed** retirement of the Kotlin
  `app/` before native acceptance. Historical source is available in Git.
  This authorization **does not waive** logged physical feature parity,
  signing, security or commercial release gates.

Current authoring did **not** execute these build, native, security or
hardware commands. Log real successes and failures in `docs/DEFECTS.md`.

## Production commercial issuer pin (new)

Before making a **paid** desktop build, obtain the Ed25519 issuer **public** key through an independently verified operator channel and put its canonical base64url 32-byte representation in a file outside source control. On a trusted release host run:

```bash
python ops/provision_license_issuer.py --public-key-file /safe/issuer-public-key.b64url
LW_COMMERCIAL_BUILD=1 python desktop/scripts/prepare_sidecar.py
```

Review the resulting `desktop/src-python/laptopwatch/license_trust.py` and build provenance; sign and notarize the exact packaged binary. If the key is absent or malformed, the commercial packaging step fails closed. Neither the billing HTTP response nor an end user's environment variable is authoritative for a license signing key. This does **not** mean the merchant API or signed installer has been independently verified.


## Commercial issuer preflight — mandatory for paid builds

The billing service signs device-bound leases with the authorized merchant
issuer key. **Do not** use a key supplied by the billing API response as
the trust anchor: obtain the PUBLIC issuer key through an independent
operator-authenticated channel. Never commit the private signing key.

After provisioning the independently approved public key into both
`desktop/src-python/laptopwatch/license_trust.py` and
`mobile/src/licensing/trust.js`:

```bash
python ops/provision_license_issuer.py --verify-only
LW_COMMERCIAL_BUILD=1 python ops/verify_release.py --mode source
```

The commands must fail for blank, invalid or mismatched pins. They do not
prove a live merchant checkout or secure binary. Run full verification
with `LW_COMMERCIAL_BUILD=1 python ops/verify_release.py --mode full`
before signing any paid desktop/mobile binary; complete the
hardware/legal/manual gates separately. On Windows PowerShell use
`$env:LW_COMMERCIAL_BUILD = "1"` before invoking Python.

The Android/iOS native projects are source-integrated only until their
actual release builds, correct issuer pin, signed entitlements, network
recovery and store policies pass on devices.

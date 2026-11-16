# LaptopWatch — verified-release checklist

**November 14, 2026: source integration only, NOT a signed or sale-ready release.**

## Source implementation

- [x] QR Ed25519 pairing, mobile Keystore, certificate fingerprint verification
- [x] Scoped signed HTTPS control and nonce replay/identity revocation
- [x] Consent-gated JPEG/MP4, WebRTC media and permission-gated microphone
- [x] Tauri/Rust/React native owner dashboard, device list and evidence folder
- [x] Optional person classifier with SHA256-verified externally installed ONNX
- [x] Local owner-armed person incident events with bounded photo/MP4/siren
- [x] Signed incident delivery, AES-GCM encrypted relay, FCM token and retry
- [x] Android FCM opt-in, network-retry registration and foreground signed sync
- [x] Validated GNSS/gpsd, durable offline last-seen, signed HTTPS receipt queue
- [x] Deprecated unsafe legacy PIN/HTTP transport and one-click installer
- [x] README original imagery, technical stack, café/restroom and recovery docs
- [x] Python incident/relay/push regression test source

## Stop-ship gates, not automatically passed

- [ ] Integrated Python, Android, Rust, React and relay build/tests verified
- [ ] Official Gradle wrapper jar/scripts and reviewed Node/Cargo locks
- [ ] Real Windows/Mac/Linux bundles, release signatures and installers
- [ ] Actual Android and laptop pairing/photo/video/audio/lock/siren trials
- [ ] Licensed verified person model, real scenes, false positives, resource soak
- [ ] Real Firebase project and FCM alert/refresh/offline/reboot delivery
- [ ] Real GNSS receiver, cloud TLS, encrypted backup and recovery simulation
- [ ] OS keystore hardening, identity rotation, media deletion, forensic review
- [ ] Secure media WAN/ICE/TURN and user-accessible key revocation verified
- [x] Proprietary LICENSE for original source (not permission to commercially distribute)
- [x] Isolated Paddle payment/subscription/adjustment/backend tests (21 passed locally)
- [x] Source-only desktop license verification and gated paid remote services; hosted Paddle.js checkout launcher source
- [ ] Native desktop checkout acceptance, Android/iOS paid entitlement enforcement and full customer account lifecycle
- [ ] Merchant sandbox integration, subscription renewals, refunds/disputes and customer registration
- [ ] Privacy/EULA, third-party licenses, live commerce, store policies and support approval
- [ ] Long reliability, security and recovery testing; no open critical defects

Merge to main as an engineering checkpoint only. No production semver release
or statement of 100% security unless all gates are independently evidenced.

## Current migration checkpoint — 2026-11-14

All phases remain **BLOCKED**, not DELIVERED, because the new Rust/React
Native changes lack fresh-checkout native builds, full lint/checkJs and
hardware acceptance. Earlier checked source capabilities refer to the
**historical Kotlin/Python reference in Git**, not a verified React Native distribution.
Do not interpret them as a passing feature-parity matrix.

- [ ] Confirm full checkout is available through network or a local mounted ZIP.
- [ ] Review and commit genuine `desktop/package-lock.json`,
      `mobile/package-lock.json`, `desktop/src-tauri/Cargo.lock`,
      reproducible Python resolution and official pinned Gradle wrapper.
- [ ] Execute all component build, lint, `checkJs`, unit and E2E tests;
      attach real logs rather than guessing.
- [ ] Verify Rust supervisor on OS, five restart cap, tray, autostart consent,
      graceful Quit, signed health and independent owner admin channel.
- [ ] Integrate RN Android/iOS scaffolds, pinned module, native Keychain,
      camera QR scanner, foreground service and optional push behind flag.
- [ ] Validate WebRTC/video/mic opt-in, real camera, evidence lifecycle,
      person alerts, GNSS last-seen and offline action denial on hardware.
- [ ] Approve commercial/legal/OS key management, release signing, storage
      encryption, installation, security scans, SBOM and 24-hour soak.
- [x] Owner explicitly authorized and executed deletion of frozen Kotlin
      `app/`; reference source is recoverable from Git history.
- [ ] Document **verified** runtime Android/iOS parity separately using
      actual builds, devices and signed gateway evidence.

## Superseding source checkout inventory — after PR #14

- [x] React Native Android project and official Gradle wrapper source
- [x] React Native iOS Xcode/Podfile source
- [x] Android pinned TLS/FGS/evidence/relay/FCM/discovery registrations
- [x] iOS strict pinned LAN + signed public-CA relay + user evidence exports
- [x] JavaScript signed control/pairing, GNSS history, mDNS verification
- [x] Explicit WebRTC two-way consent/OS permission and track cleanup source
- [x] Encrypted OP-SQLite SQLCipher cache with independent OS Keychain key
- [x] Bounded redacted incident CSV/text and local media export/deletion source
- [x] Owner-only authenticated loopback health and audit-history source
- [x] Feature-by-feature migration parity and security-source preflight
- [ ] **Run** source preflight and all native/installed dependency tests on
      an accessible checkout; these checks are not recorded as passing.
- [ ] Create and review real npm/Cargo locks; run integrated tests.
- [ ] Verify full hardware/OS/native behavior and legal/release gates.
- [x] Owner separately approved Kotlin `app/` removal on 14 November 2026.
      This is independent of device acceptance and store certification.

Earlier lines suggesting native project scaffolds or registered modules are
missing describe an older checkpoint and no longer match the source tree.
Current authoritative record: `docs/PARITY.md`,
`docs/RELEASE_RUNBOOK.md` and new `Warning.txt`.


## 16 November code checkpoint — stop-ship remains

- [x] Source: persisted merchant-cancellation obligations and retries
- [x] Source: mobile signed permanent account closure confirmation
- [ ] Live Paddle sandbox subscription, cancellation, refund and retry tests
- [ ] Genuine reviewed npm/Cargo/Pod locks and full integrated builds
- [ ] Physical Android/iOS/laptop acceptance, pen test and signed installers
- [ ] GNSS/FCM/TURN infrastructure, performance and legal approvals

Do not sell, tag or distribute an uncertified consumer build.

# LaptopWatch — authoritative implementation status

**Updated:** 14 November 2026  
**Repository state:** owner-authorized source migration to React Native completed
as a version-control decision; **release is STOP-SHIP**.

This is a source inventory, not a test certificate. Latest code-level
overhaul changes include PR #19 (authenticated desktop health response)
and PR #20 (legacy Kotlin app/build retirement). See Git history and
[retirement record](KOTLIN_RETIREMENT.md).

| Surface | Tracked implementation | Required independent verification |
|---|---|---|
| Android phone | RN JavaScript UX; Kotlin pinned HTTP, camera QR, evidence, mDNS, consented connectedDevice FGS; optional owner-gated FCM | Gradle build/test/lint, Android 8–current devices, policy and real Wi-Fi |
| iOS phone | RN JavaScript UX; Swift pinned URLSession, private evidence, Keychain, consent and signed public-CA relay | Xcode/CocoaPods, physical iOS permission/network/media tests, signing |
| Desktop UI | React/Vite, Tauri/Rust tray, autostart opt-in, single instance, bounded restarts, challenge-proven health | Rust + JS builds, signed installers, Windows/macOS/Linux lifecycle and soak |
| Desktop engine | Python FastAPI signed pairing/commands, camera, incident model gate, audit, evidence and GNSS | Complete pytest, webcam/MP4/WebRTC/audio, adversarial network and OS |
| Relay/FCM | Owner-provisionable encrypted HTTPS metadata relay; Android FCM registration + rotation | DNS/HTTPS, identity/FCM credentials, delivery, backups, Doze/outage |
| Persistence | SQLite nonce/replay/history; Keychain phone seeds + SQLCipher mobile cache | Key-loss/reinstall, migration, retention, deletion, backup and tamper tests |
| Legal/commercial | Draft website/policy/EULA and technical runbooks | Rights review, approved product licensing, privacy, merchant, stores, support |

## What was removed

The owner expressly authorized deleting the deprecated Kotlin Compose
`app/` with old root Gradle build and the obsolete duplicate adapter.
**The maintained mobile source is now `mobile/android` + `mobile/ios` + `mobile/src`.**
The old project is still in Git history, not an installable companion app.
Its synthetic biometrics, virtual unlock, fake geographic data and old PIN/HTTP
implementation are not present as real security features in the replacement.
This is not an assertion of verified Kotlin feature parity.

## Executable local verification

Use `python ops/verify_release.py --mode full --json verification-results.json`
on a real checkout to obtain an explicit FAIL/BLOCKED/PASS ledger.
The runner cannot sign installers, provision Firebase/GNSS/relay, perform
independent penetration tests, or certify physical-device behavior.

## STOP-SHIP: concrete unexecuted checks

- [ ] Fully materialized clean checkout; exact reviewed mobile/desktop npm,
      Cargo, Pod and transitive dependency locks and supply-chain audits.
- [ ] Executed `python ops/source_preflight.py`, full Python + relay pytest,
      protocol/JS tests, Jest, lint, JS check and desktop Vitest on that commit.
- [ ] Cargo build/test and packaged Tauri sidecar on Windows/macOS/Linux.
- [ ] Gradle build/unit/instrumentation tests and Android OEM/Doze/FGS testing.
- [ ] macOS Xcode/CocoaPods build; real iOS Keychain/local-network/media UX.
- [ ] Correct cert pin, wrong-key, replay, clock skew, key revocation,
      non-owner API attempts, real phone pairing and all signed controls.
- [ ] Physical camera/MP4/WebRTC/optional mic, ICE/TURN, network handoff and
      24-hour thermal, battery and process stability benchmarks.
- [ ] Genuine GNSS receiver, opt-in relay DNS/TLS/backups, real Firebase FCM
      and permitted model weights with measured false-positive accuracy.
- [ ] Desktop OS-native secret protection and independent privacy/security
      assessment, approved signing/updater and recovery strategy.
- [ ] Owner-approved licensing/legal terms, privacy, disclosure, payment,
      stores and customer support with final distribution sign-off.

The current runner cannot clone GitHub (DNS resolution failure) and lacks
Cargo, Android SDK, Xcode, installed native dependencies and physical devices.
GitHub repository operations do not supply a full test environment. No tests
or performance measurements are invented as passing. The stop-ship checklist
remains binding regardless of the source retirement approval.

See [RELEASE_RUNBOOK.md](RELEASE_RUNBOOK.md),
[PARITY.md](PARITY.md), [DEFECTS.md](DEFECTS.md) and
[THREAT_MODEL.md](THREAT_MODEL.md).

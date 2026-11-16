# LaptopWatch — authoritative implementation status

**Updated:** 15 November 2026  
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
| Legal/commercial | Proprietary source LICENSE; Paddle-backed billing, operator-invite enrollment, source checkout UI and desktop signed lease enforcement; 21 passing isolated billing tests plus 3 standalone client tests; draft website/privacy/EULA | Actual merchant approval, mobile entitlement/store billing, customer recovery, full checkout sandbox and refund/dispute operations, stores, support |

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

- [ ] Real merchant-sandbox checkout, mobile paid entitlement enforcement, full self-service signup/recovery, account deletion and dispute/refund reconciliation. Source-only desktop signed gating and operator invitations are present.
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
- [ ] Commercial issuer public key provisioned and verified signed installer; desktop OS-native secret protection and independent privacy/security
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


## 16 November 2026 additional source checkpoint

The commercial backend now queues and retries Paddle cancellation after
authenticated account closure, reports unconfirmed merchant cancellation,
and provides operator-only pending/retry endpoints. The React Native mobile
Settings source now has typed-confirmation signed account closure as well.

**Verification boundary:** Isolated tests of the bounded Paddle cancellation
provider passed (8 of 8). These tests do not constitute integrated builds,
real Paddle sandbox acceptance or device tests. Full Android/iOS, desktop,
media, performance, security and operational sign-off remain STOP-SHIP.
Previously issued one-hour offline signed leases may survive closure briefly.


## Product-hardening checkpoint — 16 November 2026

Additional source defects repaired on the integration branch:
- MP4 sessions that reach their worker limit can no longer be silently
  replaced before their completed evidence is finalized.
- JPEG quota reservation and durable file writing now occur under the same
  recorder lock, and failed writes discard partial JPEG files.
- Camera/encoder opening failures clean up resources before reporting failure.
- The OS alarm waits for the playback result, correctly reports failure,
  cleans up the generated WAV, and supports PipeWire/PulseAudio/ALSA on Linux.
- Commercial account closure now deletes enrolled device public keys and
  their replay nonces while preserving billing tombstones and cancellation jobs.

New regression tests were committed for alarm handling, evidence lifecycle,
and privacy data minimization. An isolated local alarm/Paddle harness passed
14 tests; this is NOT a test of the integrated repository. Native builds and
physical-device/merchant/security acceptance remain BLOCKED and STOP-SHIP.
No final consumer release or paid production launch is authorized.

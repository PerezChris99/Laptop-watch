# LaptopWatch — implementation and release status

**Checkpoint: November 14, 2026.** Code milestone only; NOT production ready.
Latest source is developed on perez then integrated into main. A merge is
not the same as a verified consumer release.

| Workstream | Present in source | Outstanding acceptance / non-code gate |
|---|---|---|
| Android | Compose/Room, QR-pinned TLS, phone-specific Ed25519/AES-GCM, signed photos/MP4, WebRTC media, optional FCM/WorkManager | Android build, real phone pairing/recording/push tests, permissions, OEM battery profiles |
| Desktop | React/Tauri 2 Rust owner UI, controlled Python sidecar, tray, device/incident/evidence screens | Rust/npm builds, OS installers, signing/notarization and reboots |
| Detection | Owner-armed verified person detector adapter with SHA256-pinned external ONNX weights, bounded auto-photo/MP4 | Licensed weights, seven+ scene false-positive evaluation, camera resource/thermal tests |
| Recovery | Opt-in real GNSS/gpsd, durable last-seen and signed retry queue, owner relay encryption AES-256-GCM | True GNSS hardware, public DNS/TLS deployment, backups, outage/resume field tests |
| Alerting | Signed incident metadata uploader, owner-only authenticated relay, encrypted FCM tokens, phone opt-in and generic push hints | Live Firebase project/ADC, actual FCM/device delivery and recovery |
| Security | Paired signed controls, replay denial, TLS fingerprint, audit chain, no runnable legacy PIN/HTTP | Desktop OS-native secret protection, independent penetration review, retention/deletion |
| Commercial | Source-level packaging recipes and draft policies/pricing | Signed installers, buyer-approved license, merchant, support and store acceptance |

### Technical boundaries

Current location adapter uses **real gpsd/GNSS hardware**, not universal
Windows/macOS geolocation. Stored fixes are historical even when recently
observed. User-space cameras/GNSS cannot run through deep sleep or power off.
A disconnected laptop cannot guarantee future ping or capture. Universal
offline power-independent recovery needs independently powered hardware.

Person detection is disabled without externally sourced hash-pinned model
weights. FCM is optional and best-effort; notifications are non-forensic
hints. Signed history must confirm events. Internet WebRTC needs signaling,
TURN/ICE and actual network handoff tests. Desktop long-term signing keys
are stored in restricted files, not fully OS-backed hardware keystores.

### Unverified stop-ship conditions

- [ ] Complete Python/relay test suites verified on the exact integrated commit
- [ ] Android Gradle 9.3.1 wrapper binary/scripts checked in, Android build/lint/tests pass
- [ ] Node/Cargo lockfiles and Tauri Windows/Mac/Linux builds verified
- [ ] Android-phone ↔ laptop QR, camera, recorder, WebRTC, audio and revocation tests
- [ ] Verified person model, false-positive benchmarks, 24-hour heat/power soak
- [ ] Real Firebase/FCM Android notification, reconnect and key lifecycle tests
- [ ] Real GNSS device, encrypted relay, signed receipts, offline/wake restore tests
- [ ] OS desktop keychain, storage quotas, encryption and deletion security review
- [ ] Signed/verified/notarized installers and documented update/rollback process
- [ ] Legal/privacy approval, commercial license/merchant, stores/customer support

These are engineering or external release gates, not checkmarks from a
source commit. No versioned production release has been certified. See
BUILD_AND_RELEASE.md, RELEASE_CHECKLIST.md and SCENARIOS.md.

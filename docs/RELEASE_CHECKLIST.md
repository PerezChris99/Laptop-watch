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
- [ ] Privacy/EULA/license owner approval, commerce, store policies and support
- [ ] Long reliability, security and recovery testing; no open critical defects

Merge to main as an engineering checkpoint only. No production semver release
or statement of 100% security unless all gates are independently evidenced.

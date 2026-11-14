# LaptopWatch Android migration parity — 2026-11-14

**Do not delete `app/`.** Status here means source presence, not a compiled
Android app. Kotlin remains the frozen reference. New React Native `mobile/`
is not installable until official native project generation and device testing.

| Capability | Existing Kotlin reference | New JS/React Native | Proof / blocker |
|---|---|---|---|
| Lawful first-run consent | Source present | UI source present; acceptance not persisted | Physical consent UX test pending |
| QR signature and pair proof | Kotlin reference implementation | JS verifier/proof source, Python/Node golden vectors | JS noble execution and phone camera QR scan not executed |
| Signed controls LW1 | Kotlin client source | SignedClient source, allowlisted exact routes | JS runtime/native transport and replay test on phone pending |
| Pinned HTTPS leaf fingerprint | Kotlin OkHttp source | Native OkHttp bridge source, **not registered** | Native Android template, JNI registration and MITM test |
| Hardware-protected key | Android Keystore wrapper source | react-native-keychain adapter source | Determine actual secure storage security level on device |
| Arm/disarm, lock, alarm | Kotlin source | JS UI + SignedClient source, no offline queue | LAN device action checks and consent |
| JPEG and MP4 capture | Kotlin source | JS signed request source | Camera permission, device and codec tests |
| Evidence metadata | Server route now source | Signed inventory UI source | Python gateway tests and device list, preview, export, delete pending |
| WebRTC and optional mic | Kotlin SDK source | Receiver-only JS flow / RTCPeerConnection; no native acceptance | Install native react-native-webrtc; run signed offer/answer, end-session and mic-off tests |
| Person detection/incident alerts | Kotlin source | Signed query and display source | Installed verified ONNX model, scene evaluation and device UI tests |
| FCM alerts | Kotlin opt-in source | **Not implemented** | Authorized Firebase credentials and retry/deep links |
| GNSS last seen | Kotlin source | Signed read and LAST SEEN display source | Real gpsd receiver, offline replay, device accuracy tests |
| Background connection | Kotlin foreground service source | **Not implemented** | Native foreground service, Android 15/16 Doze/battery acceptance |
| Retention/app-private SQLite | Room source | Versioned op-sqlite schema/cache untested and unencrypted | Generate native project, run migration/recovery tests, add SQLCipher and app-private export/deletion |
| Signed release installer | Reference unsigned/dev | **Not implemented** | Native templates, lockfile, Android signing |
| Commercial/legal | Draft only | Draft only | Owner-approved license/privacy/EULA and independent assessment |

## Phase 0–7 acceptance inventory

| Phase | Acceptance status | Evidence and exact remaining gate |
|---|---|---|
| 0 Baseline | **BLOCKED** | No materialized checkout/registries/Android SDK/Rust; Python pytest, Kotlin Gradle, npm and cargo not executed |
| 1 Protocol | **PARTIAL** | Python + Node cryptography independently reproduced 3 LW1 signatures, QR and proof; committed pytest and RN noble tests not run in checkout |
| 2 Desktop | **SOURCE ONLY** | Rust supervisor backoff/epoch fence/single instance source and tests; cargo, startup, logging, signed loopback health, sleep behavior not verified |
| 3 Desktop UI | **SOURCE ONLY** | Paper/gold/blue tokens and React CSS; Vite, WCAG, screen walkthrough and screenshot tests not executed |
| 4 RN foundation | **SOURCE ONLY / NOT BUILDABLE** | JS crypto, pairing, native pinned bridge source, Keychain adapter; no native project, approved lockfile or device |
| 5 Camera/media | **PARTIAL SOURCE ONLY** | Python signed evidence list + tests, JS inventory/action screens; native WebRTC, preview/export and hardware not verified |
| 6 Background/location | **PARTIAL SOURCE ONLY** | JS LAST SEEN and signed alert query; foreground service, push and device GNSS not verified |
| 7 Release | **BLOCKED** | Parity, physical acceptance, audit/SBOM/security scan, signing, license, commercial approval; do not delete `app/` |

**No phase meets DELIVERED acceptance. All phases remain BLOCKED.** Every blocked acceptance item
must include exact commands and logs in `docs/RELEASE_RUNBOOK.md` before release.

# LaptopWatch migration parity — source integration baseline

**Status: SOURCE INTEGRATION PREPARED, NATIVE ACCEPTANCE DEFERRED.** This
matrix compares only **real, consent-based functionality** against the Kotlin
reference `app/` and Python gateway. A checked source integration means the
implementation and entrypoint/wiring are present, **not** that it compiled,
ran on a phone, passed a penetration test, or is production-ready.

The owner deferred builds, infra, independent verification and hardware
tests to a separate phase. The Kotlin reference must be retained until that
phase passes and the owner separately authorizes its removal.

| Feature | Kotlin reference | React Native JavaScript / native integration | Acceptance still required |
|---|---|---|---|
| First-run consent | Compose onboarding | Persisted owner opt-in in SQLCipher private cache; blocks action screen until saved | Android/iOS reinstall/lock-state UX |
| Signed physical pairing | QR, proof and local approval | QR camera scanner, exact LWPAIR1 QR verification, phone Ed25519 proof, signed approval test | Live owner-display scan, approval and negative cases |
| Device-private phone seed | Android KeyStore wrapper | RN Keychain THIS_DEVICE_ONLY; never SQL/local files | Hardware security level and seed-rotation test |
| Strict paired LAN HTTPS | OkHttp QR pin | Android OkHttp and iOS URLSession strict leaf-DER SHA256; no unpinned fetch | Wrong pin, replay, trust and real device TLS tests |
| Signed LW1 controls | Kotlin proof/canonical request | JS SignedClient, frozen transcript/header set, single-use nonces | Execute Node noble library vectors and real phone tests |
| Arm/disarm, lock and alarm | Signed Android actions | Foreground UI, explicit confirmation, no offline queue | Laptop lock/siren with real clock/skew and connection |
| Spoken warning | Kotlin warning broadcast | Owner-confirmed signed /api/warning/tts with bounded message | Speaker hardware and audio policy |
| Camera snapshot and recording | Kotlin photo/MP4 | Signed photo/start/stop controls and guarded UI | Camera, codecs, exclusive BUSY semantics |
| WebRTC video | Kotlin receiver | RN WebRTC offer/answer and DTLS-SRTP check, signed stop, foreground-only | Actual ICE, 24h network/battery and video latency |
| Microphone/intercom | Kotlin optional intercom | Receive-only or two-way audio; explicit consent and OS mic runtime permission; tracks stopped on exit | Duplex audio, denial, background termination, consent |
| Evidence vault | Kotlin private photos and recordings | Signed bounded index; native pinned media stream direct to app-private storage; preview | JPEG/MP4 playback on both OSes, storage caps |
| Evidence export | Kotlin share/export | Android system document picker, iOS share sheet; user approval required | URI/filesize/permission tests |
| Evidence deletion | Kotlin local copy deletion | Native phone-only delete; desktop original deliberately retained | Recover/rollback/secure deletion policy |
| Incident list / alerts | Kotlin person incident history | Signed incremental incident query, deduplication, bounded SQLCipher cache, explicit clear action | Live person model and alert correctness |
| Incident metadata export | Kotlin text/CSV | CSV spreadsheet-formula escaping and text reports; OS picker/share | Negative export/consent tests |
| Person-response policy | Kotlin auto photo/record/alarm switches | Signed model-backed three-bool config, explicit auto-record consent | SHA256-verified ONNX, real scenes and false positives |
| Optional FCM | Kotlin opt-in worker | Android Firebase service + opt-in signed owner relay registration/revocation; **build flag OFF until owner configuration** | Firebase credentials, token refresh and delivery, Doze |
| Background signed status | Kotlin foreground service | Android connectedDevice FGS with owner opt-in, persistent notice, signed periodic status, zeroized in-memory seed | Android 8–16 FGS policy, screen-off, battery/reboot |
| Private LAN rediscovery | Kotlin NSD lookup | Android NSD candidate finder + matched QR metadata + real TLS pinned signed confirmation | Changing Wi-Fi IP, forged mDNS, Android permissions |
| GNSS latest | Kotlin last seen / relay | Real gpsd signed latest, direct on-demand refresh, 30-second LIVE threshold, cached LAST SEEN | Actual GNSS receiver and staleness simulation |
| Real GNSS history | Kotlin historical breadcrumbs (some sample data) | New signed, bounded GET /api/location/history from actual stored gpsd data | Signed auth, rate limits, 100 item ordering tests |
| HTTPS owner relay | Kotlin remoteSigned | Android normal-CA HTTPS / iOS URLSession relay; distinct from pinned LAN; signed latest/events/push | Relay deployment, TLS/cert handling and clock skew |
| Encrypted structured cache | Room with optional protected preferences | OP-SQLite SQLCipher, random per-device Keychain key, fail-closed, single connection | Native cipher compile, key-loss/reinstall, migration |
| Desktop tray / background | Tauri reference | Rust single instance, consented autostart, restart budget, signed Python listener and authenticated owner health | Windows/macOS/Linux process and idle tests |
| Owner audit and evidence | Desktop reference | Signed audit-chain verification before display, truthful GNSS history, native evidence file surface | Tamper and Tauri build tests |

## Legacy presentation features deliberately NOT treated as genuine parity

Some Kotlin/Compose paths show **simulated** geographic coordinates, subject
biometric dossiers, demo "intruder" actions, virtual unlock, geofencing and
tamper simulations. They are not evidence from authenticated laptop sensors or
consented face identification. They are intentionally **not** represented as
live, verified alerts or biometric profiles in the new client. Adding them
as real data would violate the owner's no-fake-telemetry/no-covert-capture
requirements. Kotlin `app/` is kept as the review reference, not proof
that its simulated features are production capabilities.

A remote OpenStreetMap SDK is not installed by default, as requested. The
location screen shows verified coordinates, source, accuracy, timestamps,
real history, LIVE or LAST SEEN instead of a fabricated live map. A richer
map/UI remains a separately reviewable product enhancement, **not** a claim
of full visual one-for-one legacy reproduction.

## Known integration and commercial gates — separately deferred by owner

1. Native clean checkout, reviewed npm/Cargo lockfiles, Android/Gradle,
   Xcode, Python, Rust, lint, type-aided checkJs, Jest/Vitest and E2E.
2. Android 8 through current support, phone/laptop real hardware, QR, media,
   high-latency and wrong-certificate tests; 24-hour Doze/resource soak.
3. iOS Keychain, pinning, media, OS local-network and app-store acceptance.
   Android NSD/foreground features have **no iOS equivalent claimed**.
4. Real Firebase project + signed owner relay, managed HTTPS DNS, optional
   TURN, real gpsd hardware and legitimately licensed person ONNX model.
5. Complete OS keystore protection for desktop private keys, updater
   credential/config and signed installers, audit review and approved
   privacy/EULA/license/support and commercialization.
6. Explicit owner authorization before removing Kotlin `app/`.

## Verification record

- Previously executed Python and Node cryptographic LW1/QR/proof fixture
  checks, and shared design-token equality checks passed in an isolated runner.
- Current-turn isolated V8 checks passed for CSV sanitization, honest
  location freshness, background eligibility and migration-schema rules.
- **Not run on an integrated checkout:** full pytest, JS/RN noble/Jest,
  ESLint/checkJs/Vitest, Rust Cargo, Android Gradle, iOS Xcode, physical media,
  foreground FCM/GNSS and penetration tests.
- No performance or signed-release measurements were made or invented.

The source migration is substantially integrated. **Full verified runtime
parity and commercial release remain blocked until those real tests pass.**

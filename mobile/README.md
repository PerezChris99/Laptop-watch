# LaptopWatch Mobile — React Native JavaScript overhaul

**Engineering status:** Android and iOS native project source, JavaScript UI,
cryptography and platform adapters are integrated into the repository.
The owner deferred native builds, on-device tests and external provisioning.
Source integration is **NOT** verified native parity or a published release.
The old Kotlin `app/` is intentionally preserved as the independent reference.

## Working source architecture

- **Android:** React Native 0.88.1 Kotlin native entrypoint, official
  0.88.x Gradle wrapper, explicit native packages for pinned signed LAN
  networking, guarded evidence storage/management, owner-configured relay,
  optional FCM, opt-in foreground signed status and Android NSD rediscovery.
- **iOS:** RN 0.88.x Xcode/Podfile scaffolds; native Swift pinned TLS,
  approved signing and download, private export/deletion, public-CA
  signed relay and privacy-conscious report export.
- **JS only:** Live, Pair, Home, Evidence, Alerts, Location, Settings,
  first-run consent, real QR camera scanning and local owner approval.
  The paired desktop, not fabricated phone metrics, decides device status.
- **Protocol:** Ed25519 LW1 exact-byte requests, randomized nonce and
  short-lived desktop QR/public-key proof. Android/iOS must reject incorrect
  TLS certificate DER SHA256 fingerprints; no plain `fetch` fallback.
- **Media:** Real aiortc offers answered via signed HTTPS; encrypted WebRTC
  video by default, optional laptop audio receive and handset duplex
  intercom only after user action plus OS mic permission. App exit/background
  stops local tracks and requests desktop session cleanup.
- **Evidence:** Signed 100-item local laptop inventory; native pinned
  download directly to app-private media, photo/video preview, explicit
  Android system file picker or iOS share sheet, phone-local deletion
  (does not delete the laptop original).
- **Person response:** Signed, model-backed `auto_snapshot`,
  `auto_record`, `auto_alarm` switches, spoken warning, incremental
  signed incident display, clear-metadata and formula-safe local CSV/text.
  An installed SHA256-verified lawful ONNX model is required; absent
  model disables person detection.
- **Location:** Signed real gpsd on-demand GNSS refresh; bounded signed
  GPS history; historical fixes and remote relay replies always LAST SEEN,
  never a fabricated LIVE location.
- **Background and alerts:** Owner-opt-in Android connectedDevice
  foreground service with persistent notification and signed status; it
  cannot control a sleeping/powered-off laptop. Optional Firebase incident
  hints are compile-time OFF until a real owner project/relay is approved.
  Push NEVER triggers commands or asserts a person was verified without a
  new signed API query.
- **Storage:** OS Keychain device-only phone signing seed, independent
  256-bit OS Keychain key for OP-SQLite **SQLCipher** private cache, single
  connection, migration schema, no offline action outbox, fail-closed DB
  open. Old experimental plaintext cache is not auto-migrated or trusted.

## Source completeness versus unexecuted verification

The major feature wiring is now represented by code and the
[feature-by-feature parity matrix](../docs/PARITY.md). Some legacy Kotlin
screens display simulated geofence events, virtual unlocks, subject
biometric dossiers and sample NYC GPS readings. Those are NOT genuine
device telemetry and are deliberately excluded from the new security
client. This avoids manufacturing "verified" threats or biometric
identifications.

Known external and native acceptance requirements remain separate:
the owner must generate reviewed `mobile/package-lock.json`, provision
Android/iOS toolchains, run `npm ci`, lint, `checkJs`, Jest and actual
native builds, inspect permissions/app-store policies and physically verify
QR, wrong certificate, replay, lock/alarm, JPEG/MP4, WebRTC, opt-in audio,
vault, GNSS, battery/Doze, security and 24-hour reliability.

iOS does not claim feature parity with Android's NSD or connectedDevice
foreground service. Cross-platform behavior needs device acceptance, and
real Firebase, model and relay services require separate owner provisioning.

```bash
# From repository root:
python ops/source_preflight.py
python design/generate.py --check
cd mobile
npm ci
npm run test:protocol
npm run test:ui
npm run lint
npm run check:js
npm run android  # requires installed compatible Android SDK and emulator/device
npm run ios      # requires macOS, Xcode and CocoaPods
```

**The commands above are required, not recorded as passing.**
Keep `app/` until complete verified parity and specific owner authorization.
No root software LICENSE or CI workflow was created on the owner's behalf.

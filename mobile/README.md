# LaptopWatch React Native (JavaScript) — release blocked

The legacy Kotlin `app/` stays intact as the **authoritative parity
reference** until the JavaScript replacement is compiled, integrated on
Android/iOS hardware, audited, and approved for removal by the owner.

## Implemented in this repository (not acceptance-tested)

- React Native 0.88.1 JavaScript entrypoint and white-paper UI screens
  for consent, pairing, home, evidence, live, alerts, location and settings.
- Deterministic LW1 Ed25519 signed client, public QR verification, phone
  private-key possession proof, replay-resistant nonces, approved-pairing gate.
  No offline queue for lock/alarm/photo/record.
- Strict native pinned HTTPS bridge in `android-native/` source. **Not yet
  registered in a generated native Android project.** Without it, every
  network operation is disabled. No plain fetch fallback.
- React Native Keychain storage adapter for the Ed25519 phone seed. Real
  Android hardware-backed security level **not established**.
- Signed gateway status, arm/disarm, lock/alarm, photo/start/stop record,
  person incidents, bounded evidence inventory and GNSS last-seen reads.
- WebRTC receiver-only JavaScript flow negotiated via signed HTTPS and
  DTLS-SRTP; turns off when leaving foreground. No microphone capture
  without a separately integrated consent+permission flow.
- Versioned op-sqlite **cache-only** schema for pairing metadata, evidence,
  alerts, GNSS and settings. Private keys are not stored in SQL. No
  command outbox. Database is NOT SQLCipher-encrypted yet.

## Blocked native dependencies

There is no full `mobile/android/` or `mobile/ios/` project at this
checkpoint; the Android pinned certificate module source is not registered.
There is no `mobile/package-lock.json`, reviewed Gradle wrapper or
Android debug build. `npm ci`, JavaScript lint, `checkJs`, full Jest/RNTL,
native camera QR scanning, WebRTC hardware tests and on-device storage
checks **have not passed**.

A trusted development workstation must generate the official
**JavaScript-only** React Native 0.88.1 native project scaffolds using
compatible Android Gradle and iOS templates; register
`LaptopWatchPinnedPackage`, configure permissions, review native
dependencies and generate the committed npm lockfile. Never import the
old insecure PIN/HTTP adapter.

The new client cannot be distributed or called parity-complete until the
owner has hardware tests for pairing, wrong-cert/revocation, screenshot
or evidence safeguards, recording, encrypted media, notifications and
GNSS; a signed Android build; feature-parity validation; and security
approval. See `docs/DEFECTS.md`, `docs/PARITY.md` and
`docs/RELEASE_RUNBOOK.md`.

```bash
python design/generate.py --check
cd mobile
npm ci
npm run test:protocol
npm run lint
npm run check:js
npm run android
```

These are **required, not recorded as passed**. No TypeScript source or
`tsconfig.json` is allowed in the `desktop/` or `mobile/` clients.

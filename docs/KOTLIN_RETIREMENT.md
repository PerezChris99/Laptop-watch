# LaptopWatch — owner-authorized Kotlin controller retirement

Date: 14 November 2026

## Decision

The repository owner explicitly authorized removal of the former Android
Kotlin/Jetpack Compose/Room controller and asked for the React Native overhaul
to become the single mobile application. This decision supersedes earlier
instructions to keep the Kotlin source until a hardware-verified parity gate.

The **retirement was authorized**, but native build success, Android/iOS
functional parity, 24-hour operation and distribution were **not established**
by the authorization. Do not treat this approval as production certification.

## Removed from the working tree

| Former path | Why it was removed |
|---|---|
| `app/` | Replaced Compose/Room app, tests and legacy Android resources |
| `mobile/android-native/` | Obsolete, unregistered duplicate of integrated RN native adapters |
| `settings.gradle.kts`, `build.gradle.kts` | Root Gradle build that targeted the retired app |
| `gradle/`, `gradle.properties` | Root wrapper/configuration no longer used by RN Android |
| `metadata.json` | Obsolete project-generation metadata unrelated to product build |

## Canonical maintained applications

- `mobile/src` — React Native JavaScript owner experience and signed API.
- `mobile/android` — React Native Gradle/Android project, with **necessary**
  Kotlin native modules for OS-integrated pinning, evidence, discovery, push,
  and foreground behavior. These Kotlin modules are *not* the retired app.
- `mobile/ios` — React Native Xcode/Swift native app and pinned transport.
- `desktop/src-ui`, `desktop/src-tauri`, `desktop/src-python` — owner UI,
  supervised shell and authenticated Python companion.
- `services/location-relay` — optional owner-provisioned HTTPS metadata relay.

The historic app is recoverable from Git without restoring it as a maintained
production path. For example, inspect individual historical source using:
`git show e52f19dbf15654940d118a6f38f2520e39af7cc0:app/build.gradle.kts`.
No unsupported simulated biometric dossiers, fabricated location fixes or
legacy PIN/HTTP command gateway are brought into the new application.

## Parity and acceptance boundaries

**Decision-level migration:** approved. **Source-level routing:** RN is the
sole maintained mobile app. **Native runtime parity:** unverified. The
separate physical/device, dependencies, build, security, OS-signing, commercial
and legal gates remain stop-ship requirements in
[PARITY.md](PARITY.md) and [RELEASE_RUNBOOK.md](RELEASE_RUNBOOK.md).

Removing deprecated code neither validates the replacement nor makes it safe
to distribute. Do not create versioned production tags or mark the project
ready for stores/sale until the actual verification gates pass.

> **Current record (14 November 2026):** The owner authorized retiring the old Kotlin Compose `app/` and root Gradle build. SEC/QA/REL entries tied exclusively to that removed implementation describe **historical baseline defects**, not active React Native source paths. They do **not** prove replacement runtime safety. Open security/release gates remain active. See [STATUS.md](STATUS.md), [PARITY.md](PARITY.md) and [KOTLIN_RETIREMENT.md](KOTLIN_RETIREMENT.md).

> **Commercial/source checkpoint (15 November 2026):** A proprietary root
> `LICENSE` now exists and is owner-authorized. Earlier REL-002 and BASE-014
> rows asserting no LICENSE describe historical baselines, not current files.
> `services/billing/` contains a locally tested Paddle-signed subscription,
> payment, adjustment and device checkout/entitlement backend (14 billing
> regression tests passed in isolation). Genuine dependency locks, live merchant,
> self-service signup, client enforcement, native builds and legal release
> acceptance are still STOP-SHIP.
>
# LaptopWatch — defect register and acceptance evidence

**Inspected revision:** `7a0c23d169d74b06241db95689c267eae8a13378`. Severity is potential user impact; status reflects code inspection, NOT a successful exploit or runtime reproduction.

| ID | Severity | Source location | Evidence at baseline | Required remedy / acceptance test | Status |
|---|---|---|---|---|---|
| SEC-001 | CRITICAL | `app/src/main/java/com/example/network/LaptopApiClient.kt:59-68` | Empty X509 trust callbacks and `hostnameVerifier { _, _ -> true }` | Remove trust-all, use validated pinned TLS; MITM certificate rejected in integration test | PARTIAL: source change, verification pending |
| SEC-002 | CRITICAL | `app/src/main/java/com/example/util/LaptopCompanionScript.kt:121-122,155-157`; `data/LaptopConfigEntity.kt:9` | Hardcoded default PIN and optional query-token auth | Pair with per-phone public keys and signed requests; unauthorized/old/replayed requests rejected | OPEN |
| SEC-003 | HIGH | `network/LaptopApiClient.kt:100-105` | Camera URLs contain `?pin=`; exposed to logs, history and proxies | Remove all URL credentials; auth headers + per-request signatures; test URLs contain no token | OPEN |
| SEC-004 | HIGH | `util/LaptopCompanionScript.kt:551` | Flask development server bound to `0.0.0.0` | Local sidecar loopback; opt-in authenticated LAN gateway; reject unpaired clients | PARTIAL: source change, verification pending |
| SEC-005 | HIGH | `util/LaptopCompanionScript.kt:155-157` | No replay protection, bounded token window or global rate limiting | Per-key nonce/timestamp window; lockout/rate limiting; replay and flood tests | OPEN |
| SEC-006 | HIGH | `data/AppDatabase.kt:29-36` | `fallbackToDestructiveMigration()` may delete forensic records | Versioned Room migrations and v6->new upgrade tests preserving evidence | PARTIAL: source change, verification pending |
| SEC-007 | HIGH | `app/src/main/AndroidManifest.xml:16-27` | `allowBackup=true` while database contains security evidence, PIN/config | Encrypt credentials/keys in Keystore and exclude sensitive files from backups; verify restore | PARTIAL: source change, verification pending |
| PRIV-001 | HIGH | `util/LaptopCompanionScript.kt:287-316,319-365` | GPS 1m San Francisco coordinates and subject identities are synthetic in live server | Return null/unavailable unless real sensor/person classifier; test no mock data in live mode | PARTIAL: source change, verification pending |
| API-001 | HIGH | `network/LaptopApiClient.kt:443,465`; `util/LaptopCompanionScript.kt:319,337` | Android GET routes `/api/location/gps`, `/api/security/profiles` do not match Python `/api/location`, `/api/biometrics/profiles` | Contract test all endpoints and payload keys, return unsupported for unimplemented features | PARTIAL: source change, verification pending |
| API-002 | MEDIUM | `README.md` REST spec; `util/LaptopCompanionScript.kt` route list | README describes `/api/camera/stream`, `/api/intercom/audio` but Flask script has no matching routes | WebRTC media, explicit REST fallback, generated OpenAPI contract | OPEN |
| MEDIA-001 | HIGH | `util/LaptopCompanionScript.kt:188-266` | Frame differencing only; lighting, pets and screens can trigger motion alarms | Person-class gated alert with seven-scene false-positive test | OPEN |
| MEDIA-002 | HIGH | `network/LaptopApiClient.kt`, `util/LaptopCompanionScript.kt` | MJPEG/JPEG instead of low-latency WebRTC; no ICE restart | WebRTC DTLS-SRTP/Opus, STUN/TURN and handoff measurements | OPEN |
| MEDIA-003 | MEDIUM | `util/LaptopCompanionScript.kt:411-431` | VideoWriter is opened/released but recorder needs verified frame-write path | Video file has decodable frames; test resolution/fps match; confirm file lifecycle | OPEN |
| FGS-001 | HIGH | `app/src/main/AndroidManifest.xml:41-45`; `service/LaptopMonitorService.kt` | Sticky 24h dataSync foreground polling conflicts with Android 15+ 6h/24h limit | Redesign background model and timeout behavior; test Android 15/16; see platform docs | PARTIAL: source change, verification pending |
| REL-001 | HIGH | Root tree | No checked-in `gradlew`, `gradlew.bat` or wrapper JAR | Reproducible clean Android build from wrapper on Windows/Linux | OPEN |
| REL-002 | HIGH | Root tree; `README.md` | MIT badge and LICENSE link but LICENSE file absent | Approve actual licensing strategy; commit owner-authorized LICENSE or commercial license; remove false badge | OPEN |
| REL-003 | HIGH | `app/build.gradle.kts:26-43` | Release signing depends on external keystore; no supplied signing infrastructure | Signed AAB/APK and verification with authorized signing identity | BLOCKED: credentials |
| REL-004 | HIGH | Root tree | No desktop app, installer, notarization, update chain, signaling/TURN or storefront | Implement and test platform pipeline; legal/store accounts required for actual submission | OPEN |
| QA-001 | HIGH | `app/src/test/`, `app/src/androidTest/` | Only sample tests; no security, contract, motion, soak or cross-device tests | Add automated and physical test matrix and attach logs | OPEN |
| DOC-001 | MEDIUM | `README.md`; `app/build.gradle.kts:20-28` | README Android 8+ badge (API 26), Gradle minSdk 24 (Android 7) | Reconcile supported version and test min SDK on a device | OPEN |
| DOC-002 | MEDIUM | `README.md` | Claims encrypted channel and complete two-way intercom, but code does not establish both | Replace marketing claims with implementation status until validated | OPEN |

## Additional findings from 2026-11-14 (source-reviewed, not closed by feature commits)

| ID | Severity | Path / scope | Finding | Acceptance gate | Status |
|---|---|---|---|---|---|
| MEDIA-004 | HIGH | `desktop/src-python/laptopwatch/evidence.py`, `gateway.py` | Photo, MP4 recording and download endpoints now exist and use signed requests, but camera/codec hardware behavior and Android integration unverified | Real JPEG/MP4 decode, disk quota, retention, permissions, crash recovery and signed client tests | PARTIAL: 5 mocked media tests passed |
| LOC-001 | HIGH | `desktop/src-python/laptopwatch/tracking.py` | GNSS adapter implemented using a configured real `gpsd` receiver; no native Windows/macOS location API or device location UX | Reference GPS comparison and OS permissions on actual supported devices | PARTIAL: 8 mocked tracking tests passed |
| LOC-002 | CRITICAL | Reconnect/backhaul, Android client, infrastructure | Offline queue is LOCAL to laptop; after theft, previous unsynced positions cannot be viewed remotely | End-to-end encrypted signed remote delivery, authenticated durable receipt, owner phone retrieval, reconnect hooks and outage tests | OPEN |
| LOC-003 | HARDWARE | Sleep/power-off | Python user-mode code cannot guarantee sampling while machine sleeps/powers down | Document OS Find My options and independent battery-backed GNSS tracker for true independence | INHERENT LIMITATION |
| MEDIA-005 | HIGH | Android ↔ Python security | Android still uses legacy network client; phone cannot control the signed photo/video gateway | Paired phone signing and TLS pinning, UI actions, live device tests | OPEN |
| QA-002 | HIGH | Build and release | No complete Android SDK/Gradle wrapper verification or all-platform hardware acceptance | Reproducible full test suite, signing and 24h soak evidence | OPEN |

## Acceptance evidence format

For every fix, record: commit SHA, test ID, exact invocation, date, OS/device, result, and log/screenshot/hardware artifact. A code review or mock response is not runtime proof.

**Automated gates:** `./gradlew build test lint`; `pytest -q`, `ruff check`; `npm run build`; `cargo build`; `docker build`; protocol fuzz/integration checks. **Manual gates:** Windows/macOS/Linux installation, real person scenes, WebRTC latency, adversarial LAN, Android 15+/16+, store signing.

**Platform reference:** https://developer.android.com/develop/background-work/services/fgs/timeout

Do not promote to `main` if any newly modified subsystem has a red required gate. An audit document is not a claim that the listed issues are fixed.


## Phase 0 forensic baseline gate — 2026-11-14 (commit 30f685e)

This is an **observed environment and repository inventory**, not a passed
build or security certification. The GitHub tree was inspected using the
connected repository API. Direct clone was attempted; there is no checkout
and the runtime cannot resolve `github.com`. Commands below reflect actual
executions and inspection; required build commands could not start.

| Gate / ID | Severity | Exact evidence | Status | Action to unblock |
|---|---|---|---|---|
| BASE-001 Warning.txt | P0 | `GET contents/Warning.txt?ref=perez` returned HTTP 404; recursive tree has no `Warning.txt` | **BLOCKED: required reference missing** | Owner supply original `Warning.txt` exact file/content; no invented substitute |
| BASE-002 full checkout | P0 | `git ls-remote https://github.com/PerezChris99/Laptop-watch.git HEAD` → `Could not resolve host: github.com`; `/mnt/data/Laptop-watch/.git` absent | **BLOCKED: source not materialized** | Restore outbound DNS/HTTPS or provide a locally available full checkout |
| BASE-003 Python full test | P0 | Python 3.13.5, pytest 9.0.2, cryptography 46.0.4, fastapi 0.128.2 available; `aiortc`, `av`, `sounddevice` missing; no checkout | **NOT EXECUTED** | Checkout + dependency index: `python -m pip install -e 'desktop/src-python[test,media,discovery]'`, `cd desktop/src-python && python -m pytest -q` |
| BASE-004 Rust/Tauri | P0 | `cargo` and `rustc` commands missing | **NOT EXECUTED** | Install Rust toolchain + platform Tauri prerequisites; run `cargo build --manifest-path desktop/src-tauri/Cargo.toml` and `cargo test --manifest-path desktop/src-tauri/Cargo.toml` |
| BASE-005 React/Vite | P0 | Node v22.16.0 and npm 10.9.2 present, **no desktop/package-lock.json** in Git tree; no source checkout/network | **NOT EXECUTED** | Generate and review lockfile with trusted registry; `cd desktop && npm ci && npm run build`; add ESLint, Prettier and `checkJs` gates |
| BASE-006 Android/Gradle | P0 | Tree has only `gradle/wrapper/gradle-wrapper.properties`, no `gradlew`, `gradlew.bat`, or `gradle-wrapper.jar`; `gradle`, `adb` absent | **NOT EXECUTED** | Official verified Gradle wrapper; JDK/Android SDK; `./gradlew assembleDebug test lint` |
| BASE-007 Cargo locks | P1 | Tree has `desktop/src-tauri/Cargo.toml` but no `Cargo.lock` | **OPEN** | Generate official resolver `Cargo.lock`, review crate versions, commit |
| BASE-008 Android material | P1 | No physical Android/desktop, cameras or GNSS exposed to the execution environment | **NOT EXECUTED** | Actual Android/Windows/Mac/Linux device matrix, cameras, network and permission checks |
| BASE-009 desktop supervision | P0 | `desktop/src-tauri/src/main.rs` only starts a sidecar via `start_companion`; termination clears process state; no automatic restart and no max-five-in-60-second supervisor | **OPEN** | Rust single-instance process manager, crash/restart tests, graceful Quit, tray error, autostart owner consent |
| BASE-010 installer performance | P1 | `desktop/scripts/prepare_sidecar.py` passes `--onefile`; target specified `--onedir`, cold start and installed size not measured | **OPEN** | Change packaging layout and validate Tauri external binaries on each host, record executable size and cold start |
| BASE-011 owner loopback | P0 | Local UI uses Tauri IPC and direct Python subprocess; dedicated ephemeral-token owner loopback API not present or verified | **BLOCKED: security design/test** | Define and test separate owner auth boundary, fail closed and reject anonymous localhost control |
| BASE-012 React Native | P0 | No `mobile/` directory or committed mobile JS lockfile, no RN signing/pairing/camera/native modules | **NOT IMPLEMENTED** | Only after Phase 0 gate: JS-only RN app, exact signed interop, secure Keystore + OS pinning, parity evidence |
| BASE-013 mobile migration | OWNER AUTHORITY | Existing Kotlin `app/` is authoritative parity reference, master requires deletion only after proven parity | **NOT AUTHORIZED** | Owner explicitly authorize deletion after documented verified parity, no premature removal |
| BASE-014 legal/commercial | OWNER AUTHORITY | No owner-approved `LICENSE` and no actual signing or payment/store credentials | **BLOCKED** | Owner legal decisions, account approvals, signing identities, service deployment and device acceptance |

### Test/build attempt log

```text
date: 2026-11-14
environment: isolated Linux runner /mnt/data
python --version                    -> Python 3.13.5
node --version                      -> v22.16.0
npm --version                       -> 10.9.2
pytest --version                    -> pytest 9.0.2
command -v cargo                    -> missing
command -v rustc                    -> missing
command -v gradle                   -> missing
command -v adb                      -> missing
command -v docker                   -> missing
git ls-remote https://github.com/PerezChris99/Laptop-watch.git HEAD
                                    -> fatal: Could not resolve host: github.com
test -d /mnt/data/Laptop-watch/.git -> missing
```

**Gate verdict: Phase 0 FAIL/BLOCKED.** Not correct to assert “all
source code complete except third-party/infra,” not correct to label the
Python test suite green at the integrated commit. Phase 1+ remain
unverified/not started at this gate. No benchmark values fabricated.

All code modifications requiring inspection of `Warning.txt` await the
missing authoritative file. See `docs/DECISIONS.md` for conservative
security defaults and the reason production claims remain prohibited.

## Autopilot completion directive — source recovery checkpoint (2026-11-14)

The owner explicitly waived waiting for the **previously missing** original
`Warning.txt`. A NEW engineering warning ledger was written; it is not
represented as that original's contents. No release acceptance is implied.

### Actually executed (outside a full checkout)

```text
git ls-remote https://github.com/PerezChris99/Laptop-watch.git HEAD
  -> fatal: Could not resolve host: github.com
python --version -> Python 3.13.5
node --version   -> v22.16.0
npm --version    -> 10.9.2
java -version    -> OpenJDK 21.0.12.1
command -v cargo -> missing
command -v rustc -> missing
command -v gradle -> missing
command -v adb -> missing

node /mnt/data/lwwork/verify.mjs
  -> Node crypto: 3 LW1 signatures + QR + proof verified byte-identically;
     tampering rejected.
python /mnt/data/lwwork/design/generate.py --check
  -> Shared tokens are synchronized.
Python cryptography signature check of the same vectors
  -> Python cryptography: 3 requests, QR, proof PASS
```

These are **fixture checks**, not repository pytest, React Native library
interop, Android signed-network tests or native app build results.

### Phase-by-phase stop-ship decisions

| Phase | Status | Exact unmet gate | Required human action |
|---|---|---|---|
| 0 Base toolchains | **BLOCKED** | No clean materialized checkout, Python media extras, Cargo/Rust, pinned Gradle wrapper, SDK, desktop npm lock, Cargo lock or mobile npm lock; `npm ci`, `cargo build --locked`, `./gradlew assembleDebug`, full `pytest` **NOT EXECUTED** | Supply a full locally mountable repo checkout or repair GitHub DNS/HTTPS; install Rust stable, Gradle official pinned wrapper/JAR, JDK matching RN 0.88, Android SDK including API 36/37, npm registry; generate reviewed lockfiles and execute exact commands in `docs/RELEASE_RUNBOOK.md` |
| 1 Signed client | **BLOCKED** | Node `@noble/ed25519`/RN native test dependencies not installed; committed vector tests against exact repository library **NOT EXECUTED** | Run `cd mobile && npm ci && npm run test:protocol` and `cd desktop/src-python && pytest -q tests/test_protocol_vectors.py` from clean checkout; attach outputs |
| 2 Rust supervisor | **BLOCKED** | Source supervision/epoch fence/backoff is not compiled; no child crash integration test or signed health probe; no rotating/redacted log service | Install Rust/Tauri prerequisites, then `cd desktop/src-tauri && cargo build --locked && cargo test --locked`; test 5-crash cap, autostart, sleep, Quit and log redaction on Windows/macOS/Linux |
| 3 White-paper owner UI | **BLOCKED** | CSS token checks only; no desktop npm lock, lint, unit/component tests, WCAG contrast run or installable screenshot | Provide reachable npm registry, regenerate reviewed `desktop/package-lock.json`, execute `cd desktop && npm ci && npm run build && npm run lint`; perform keyboard/screen-reader/contrast review |
| 4 RN JavaScript Android | **BLOCKED** | No official RN `mobile/android/` or `mobile/ios/` scaffolds, npm lock, native pinned module registered, Android build, on-device Keychain check, QR camera scanner or SQLite migration test | On supported Android/Xcode host generate RN 0.88.1 JavaScript native projects, register pinned OkHttp module, add verified Android permissions, commit official generated files and reviewed `package-lock.json`; run `npm ci && npm run check:js && npm run lint && npm run android` |
| 5 Camera and vault | **BLOCKED** | Media receiver and signed evidence list untested; no native WebRTC, mic opt-in, preview/export/delete and real camera/MP4/person-model acceptance | Install `react-native-webrtc` with native project, test signed offer/answer, DTLS-SRTP, app-private evidence and consent on Android device and actual laptop; supply licensed, SHA256-verified ONNX |
| 6 Background, FCM, GNSS | **BLOCKED** | No RN foreground service, genuine FCM, GNSS hardware or 24h battery/Doze soak; current RN foreground-only polling is not an always-on listener | Configure authorized Firebase, approved FGS type/permissions and Android notifications, real GNSS receiver, paired devices/relay, then test notification latency/reboot/Doze/last-seen accuracy |
| 7 Parity/release | **BLOCKED** | Kotlin feature parity incomplete; no full dependency audits or CycloneDX SBOM, no signed installers, no legal/commerce credentials, no 24h soak | Owner provides build fleet, release identity, third-party accounts, license/privacy/EULA decision, independent security reviewer and final approval; **explicit separate approval required before deleting `app/`** |

### Cross-subsystem quality checklist (not green)

- No tests were claimed as run for newly integrated Rust/Tauri, Kotlin,
  mobile native module, op-sqlite, camera/WebRTC, FCM or GNSS.
- The JS anti-theft commands MUST remain no-queue; TLS pinning MUST fail
  closed without the Android native module; no background camera/microphone.
- Direct app ownership and signing are NOT substitutes for device approval;
  the Android RN migration is not a releasable replacement for Kotlin yet.
- Phase 7 is incomplete; do not publish, market as verified or delete Kotlin.

## Follow-on verified-history and desktop UI changes — 2026-11-14

The next engineering checkpoint adds Tauri IPC for **last-seen-only**
SQLite reading and locally verified signed audit history, a Python tamper
regression test, desktop navigation screens and source-only Vitest +
Maestro flows. The security-sensitive code and tests were reviewed as
GitHub text; no full checkout build, pytest run or native UI/hardware
test was executed. **All phases remain BLOCKED.**

| Requirement | Status | Exact human action to unblock |
|---|---|---|
| Signed audit UI | BLOCKED | On a trusted checkout execute `cd desktop/src-python && pytest -q tests/test_cli_audit.py`, `cd desktop/src-tauri && cargo test --locked`; manually tamper an audit copy and verify Tauri never shows unverified entries |
| Last-seen view | BLOCKED | Run desktop Tauri build and cross-process SQLite fixture tests; confirm coordinates are only real gpsd history and always LAST SEEN |
| Desktop UI tests | BLOCKED | `cd desktop && npm ci && npm run test:ui && npm run lint && npm run check:js && npm run build`; provide reviewed lockfile, supported npm registry, component screenshots and WCAG AA/keyboard check |
| Mobile Maestro | BLOCKED | Generate/build RN native Android, install on emulator plus physical device and execute `maestro test mobile/e2e/01-pair-and-controls.yaml` with a real fresh signed QR and actual owner approval; then media flow with webcam/permission consent |
| Signing/notarization | BLOCKED | Owner obtain certificates and accounts and execute commands in `docs/SIGNING_AND_NOTARIZATION.md`; inspect exact signed binaries on devices |
| Performance / soak | BLOCKED | Owner run 24-hour device soak, CPU/RSS/installer-size/cold-start and action/stream latency measurements on named hardware; attach raw logs and actual numbers |

A merged PR does **not** turn any of these BLOCKED rows into DELIVERED.

## Source-integration completion checkpoint — 2026-11-14

After merged PRs #13 and #14, the source tree includes Android RN 0.88.x
Gradle wrapper and native modules, iOS Xcode/Swift pinned bridge, optional
FCM+HTTPS owner relay, signed incident/GNSS history, Android NSD trusted
rediscovery, explicit two-way audio, native evidence lifecycle, formula-safe
incident exports, and Keychain-backed SQLCipher storage. The next source
checkpoint updates preflight and documentation to match actual files.

The owner explicitly deferred ALL clean builds/native tests, real credentials,
infrastructure, signing, legal approval and hardware checks to a separate
verification phase. The **code cannot be certified 100% complete or runtime
parity-verified before those checks**. No fake evidence or biometric persona
is substituted for absent real sensor input.

| Gate | Current status | Exact owner verification required later |
|---|---|---|
| Android native/Kotlin/RN | BLOCKED | Review mobile npm lock and official Gradle 0.88.x plugins; run npm ci, lint, checkJs, unit and ./gradlew assembleDebug/test/lint; test real handset |
| iOS Swift/Xcode | BLOCKED | Run pods/Xcode compile/sign and iPhone Keychain/pinning/evidence/media tests |
| Desktop Rust/Tauri | BLOCKED | Generate reviewed Cargo lock; run cargo test/build and verify sidecar child start/crash/stop/health/log security on Windows/macOS/Linux |
| Signed Python and relay | BLOCKED | Execute full pytest, live TLS replay/revocation and external owner relay tests |
| Encrypted cache | BLOCKED | Verify OP-SQLite SQLCipher native integration, random Keychain key, database open failure, reboot/upgrade/reinstall and old plaintext cache cleanup |
| Media/biometric | BLOCKED | Real camera/MPEG/WebRTC, permission denial, microphone background teardown, genuinely licensed person ONNX and scene evaluation |
| Location / FGS / FCM | BLOCKED | Real gpsd, 100-item history, Android NSD spoofing, Doze/FGS/reboot, authorized Firebase signed enrollment/delivery |
| Distribution | BLOCKED | Signing/notarization, dependency SBOM/audits, privacy/EULA/license/store owner acceptance and 24h soak |

**Known deliberate source feature exclusions:** legacy Kotlin demo
subject/biometric classifications, virtual unlock, fabricated GNSS readings
and simulated geofence/tamper controls; these are not real authenticated
security features and are NOT parity requirements. Existing Kotlin app/
remains; remove it only after green acceptance and explicit owner approval.

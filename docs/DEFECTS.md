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

# LaptopWatch — reproducible build and device acceptance runbook

**Execution evidence:** all commands below are **prescribed**, not passed.
The isolated engineering runner cannot DNS-resolve GitHub/npm and lacks Rust,
Gradle, Android SDK and physical devices. If a command fails, attach full logs
and hardware/OS details to `docs/DEFECTS.md`, without secrets.

## Repository source-policy preflight

```bash
python ops/source_preflight.py
python design/generate.py --check
```

Checks JavaScript-only desktop/mobile, generated tokens, Python syntax and
critical JS syntax; this is **not** a full build or security certification.

## Python + HTTPS relay

```bash
python -m venv .venv
source .venv/bin/activate
python -m pip install -e './desktop/src-python[test,media,discovery]'
cd desktop/src-python && python -m pytest -q && cd ../..
python -m pip install pip-audit
pip-audit
```

On Windows PowerShell use `.venv\\Scripts\\Activate.ps1`.
Also run `python -m pytest -q services/location-relay/tests` from root with
matching relay dependencies installed. Capture Python, OS and tool versions.

## Desktop JavaScript, Rust and Tauri

```bash
cd desktop
npm ci
npm run check:tokens
npm run build
npm audit --audit-level=high
cd src-tauri
cargo build --locked
cargo test --locked
```

**Currently blocked by missing `desktop/package-lock.json`, `Cargo.lock`,
cargo/toolchains and optional platform SDKs.** Generate the locks on a trusted
machine after reviewing dependencies; do not fabricate placeholder lockfiles.
Check Tauri singleton, repeated crashes (five / 60 seconds), autostart with
explicit owner consent, hide-to-tray, Quit termination, manual stop/rapid
restart and reboot. Verify no Windows console window in a signed package.

## Kotlin reference — keep until parity

```bash
./gradlew --version
./gradlew assembleDebug test lint
```

Requires genuine `gradlew`, `gradlew.bat`, official wrapper JAR, JDK 17,
Android SDK and reviewed dependency lockfiles. At baseline these are missing.
Do not claim FCM configured without a real Firebase project.

## React Native 0.88.1 (JavaScript only)

```bash
cd mobile
npm ci
npm run test:protocol
npm run lint
npm run check:js
npm run android
```

**Blocked:** official reviewed `android/` and `ios/` projects, generated
`package-lock.json`, RN native package registration, real device and toolchains
are absent. Android native OkHttp pinned adapter is source-only. Do not swap
in unpinned `fetch`. On iOS an equivalent fingerprint-pinning module is
required before signed LAN controls can be enabled.

## Security and distribution

After dependencies and a full checkout: execute `pip-audit`, `npm audit`,
`cargo audit` (install `cargo-audit`), gitleaks, semgrep and a CycloneDX
SBOM for each artifact. Attach machine-readable findings, explicit exceptions
and actual test runs. No GitHub Actions workflow is added.

Device matrix: Android supported API floor through 16, Windows 10/11,
macOS latest supported, Linux distributions; test QR, certificate mismatch,
replay/skew/revocation, network interruption, 24-hour soak, WebRTC/Mic off,
Camera/MP4, GNSS real fix and stale-label truth, push/Doze, backup recovery.
Collect cold-start, idle RSS/CPU, installer bytes, p95 LAN action/stream
latency with measurement tool/OS/device, not guessed values.

Only after every acceptance criterion is proven, and the owner explicitly
authorizes deletion of `app/` and legal/identity actions, can commercial
release be evaluated. Store submissions, signing and payments need real accounts.

### Mandatory targeted post-build functional tests

```bash
cd mobile
npm run test:protocol           # includes location and schema tests
npm run check:js
npm run lint
cd ../desktop/src-python
python -m pytest -q tests/test_protocol_vectors.py tests/test_evidence.py tests/test_gateway.py
cd ../src-tauri
cargo test --locked supervisor_tests
```

These commands have **not been executed on an integrated checkout**.
Devtools screenshots and mock tests alone cannot prove native WebRTC,
pinned TLS, Keystore integrity, FCM, GNSS or foreground operation.

# LaptopWatch — engineering decisions (autopilot)

**Checkpoint:** 2026-11-14. **Repository baseline:** `30f685e145548c87f3e12e84e0b2f493490d3552`.

## Decision 001 — Phase 0 is a hard gate

- **Status:** ACCEPTED.
- Do not claim Phase 0 passed unless Python (full pytest), desktop React/Vite, Rust/Tauri and Kotlin/Gradle have been executed against the baseline and results recorded.
- In the present execution environment, GitHub repository source can be inspected through the repository connector, but the checkout cannot be materialized: `git ls-remote https://github.com/PerezChris99/Laptop-watch.git HEAD` fails with `Could not resolve host: github.com`.
- Local `/mnt/data/Laptop-watch/.git` is absent; `cargo`, `rustc`, `gradle`, `adb` and `docker` are unavailable. Python 3.13.5, Node 22.16.0, npm 10.9.2 and pytest 9.0.2 exist; `aiortc`, `av` and `sounddevice` do not.
- **Conservative resolution:** classify full baseline build and full-suite tests as **BLOCKED / NOT EXECUTED**. Do not substitute old partial mocked tests for the current checkout.
- **Required human action:** provide a tool-accessible full repository checkout with the required SDK/Rust tooling and reachable dependency registries (or execute the exact commands in a controlled build machine and provide complete logs). This is a test-execution/environment gate, not a source-code pass.

## Decision 002 — Missing required authoritative Warning.txt

- **Status:** BLOCKING REFERENCE FILE MISSING.
- GitHub tree at the baseline contains **no `Warning.txt`**. Repository API retrieval of `Warning.txt` returned HTTP 404.
- The master instruction explicitly requires this file to be read **before writing application code**.
- **Conservative resolution:** do not invent its contents or silently create a replacement. Only documentation of discovered gaps may be updated pending the owner's actual `Warning.txt`.
- **Required human action:** provide the exact intended `Warning.txt` contents or a repository path where it is accessible.

## Decision 003 — Authentication contract is not to be guessed

- **Status:** ACCEPTED.
- Python signed transcript is `LW1\\nMETHOD\\nPATH\\nSHA256(raw body).hex\\nUTC_SECONDS\\nBASE64URL_NONCE\\nDEVICE_UUID\\n`; headers are `X-LaptopWatch-Device`, `X-LaptopWatch-Nonce`, `X-LaptopWatch-Timestamp`, `X-LaptopWatch-Signature`. Nonce = 16 random bytes (unpadded base64url); identity = UUID; Ed25519 signature covers the exact ASCII transcript.
- Pairing QR is `laptopwatch://pair?` with keys `v,id,host,port,pk,fp,nonce,exp,sig`; the invitation transcript begins `LWPAIR1\\n`, proof begins `LWPAIR-PROOF1\\n`. The phone must pin the exact TLS certificate fingerprint and require local desktop approval.
- A separate owner UI ↔ gateway bearer-token loopback endpoint is **not presently implemented as a proven contract**. Any new channel must be independently authenticated, restrict origin, require a per-launch secret and be covered by tests. No unauthenticated localhost camera or action endpoints may be added.
- **Conservative resolution:** no speculative mobile signing API or local owner command endpoint until executable cross-language vectors and authorization tests are available.

## Decision 004 — Controlled migration and irreversible changes

- **Status:** ACCEPTED.
- Freeze existing Kotlin/Compose `app/` as the parity reference. Build a new JS-only React Native implementation under `mobile/` with full evidence of parity, then request **explicit owner authorization** before deleting `app/`.
- Do not add or remove a LICENSE absent explicit owner approval. Do not add feature branches, GitHub Actions, release tags or claims of verified production availability.
- Preserve current fail-closed legacy PIN/HTTP stubs, lawful recording/consent, manual microphone opt-in, no offline command queue and honest last-seen labeling.

## Decision 005 — Source presence is not acceptance evidence

- **Status:** ACCEPTED.
- File existence, a README or a GitHub merge cannot prove native compilation, device reachability, signed installer integrity or legal authority to sell.
- The existing desktop uses PyInstaller `--onefile`, not the target `--onedir`; the Rust shell starts a sidecar on demand and only clears state on termination, without an evidenced supervisor/restart budget. These are P0 engineering gaps.
- Keep the same baseline until all executable Phase 0 gates are satisfied or explicitly blocked with precise required actions in `docs/DEFECTS.md`.

## Decision 006 — Communication and release

- **Status:** ACCEPTED.
- Continue all unblocked investigation without phase-progress prose, then provide a single evidence-based §14 report. An explicit request for the missing authoritative file is allowed under the owner's autopilot exception.
- Do not merge an unverified native source overhaul as if it were a signed production release. A documentation-only baseline assessment may be committed to `perez`, reviewed against the original SHA and merged through an ordinary PR with an explicit **NOT VERIFIED** disclaimer.

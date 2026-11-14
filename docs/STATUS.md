# LaptopWatch — current implementation vs production target

> **November 14, 2026 repository checkpoint:** Signed QR approval, Android signed
> controls, Tauri shell, development WebRTC interfaces, opt-in mDNS, signed TTS,
> offline GPS queue and self-hosted location receiver are now written to `perez`.
> The legacy insecure Android PIN client has been disabled. The recipient
> encrypts GPS coordinates at rest with AES-256-GCM. This is a **code-level
> integration milestone only**: Android/desktop builds, real connected phones,
> TURN connectivity, push notifications, person weights, DNS, signed packages,
> legal/purchase accounts and actual sleep-state tests remain unverified.


**Status date:** 2026-11-14. **Working branch:** `perez`. **No v1.0.0 release candidate.** The existence of code does not constitute a physical test, commercial permission or app-store acceptance.

| Workstream | Where we are | Where we must be | Release gap |
|---|---|---|---|
| Mobile | Legacy Kotlin/Compose prototype, demo UI, Room data and foreground service | Stable signed Android app with native paired-key control, user consent, privacy and tested background delivery | **Critical** |
| Desktop | Python CLI and secure gateway prototype | Tauri 2 + React UI, sidecar/tray, signed Windows/Mac/Linux installers | **Critical** |
| Network/auth | Python Ed25519 signature verification and pinned TLS identity provisioning | QR peer pairing, Android keystore keys, mobile TLS pinning, WAN ICE/WebRTC, relay, safe revocation | **Critical** |
| Photo/recording | Signed local JPEG and bounded MP4 endpoints, tested with mocks | Phone controls, real camera/codec validation, evidence UX, retention/export, visible consent | **High** |
| Live feed | Still snapshot fallback | Low-latency WebRTC video/Opus audio and seamless handoff | **Critical** |
| Motion/person | Legacy motion-difference daemon | Real person detector false-positive matrix and reliable alerting | **High** |
| GPS | Opt-in `gpsd` receiver adapter, validated non-fake position and accuracy | Native OS providers, live device tests, explicit consent, no fake 1m precision | **High** |
| Stolen-device recovery | Durable local last-seen + unacknowledged sync queue | Deployed trusted remote recipient, signed encrypted transfer, phone retrieval, network-resume hooks, push alerts | **Critical** |
| Sleep/power-loss | Ordinary processes suspend; no general guarantee | OS-supported wake where possible; enable platform Find My; independent powered tracker for true offline/power-off | **Hardware constraint** |
| Data security | Audit chain, local DB, no default LAN exposure | OS keystore, encrypted location/evidence, external audit checkpoint, adversarial testing | **High** |
| Quality | New evidence/GNSS unit tests: 13/13 locally passed | Full Android/Python/Tauri CI-equivalent gates, real hardware, 24h soak, performance report | **Critical** |
| Release/commercial | Draft legal documents and site copy | Owner-approved license and policies, live website, checkout/MoR, signed binaries, Play/MS Store acceptance | **Critical** |

## Actually completed in the latest implementation pass

- Existing `main` PR merge incorporated into `perez` by fast-forward before changes.
- New evidence subsystem and authenticated API: save JPG, start/stop bounded MP4, signed download.
- New GNSS adapter with true fix checks and no claim of accuracy without receiver; local persistent last-seen and offline queue, enabled only by explicit flag.
- Mocked evidence/GPS tests executed in local Python 3.13 environment: 13 passing; exact tested source blobs match GitHub blob SHAs.
- Source/documentation and café/restroom scenario updated with original existing repository imagery.
- There are no real signed installers, QR pairing UI or remote sync provider yet.

## Definition of done before public production claim

1. Android and desktop complete the owner flow, reliably paired, without PIN/HTTP bypass.
2. Real camera photos/video and live WebRTC work on tested devices (not simulator only).
3. Person detection demonstrably controls alerts, with measured false positives.
4. Accurate location source identifies its precision and returns unavailable honestly; remote last-seen survives laptop loss because it was delivered and acknowledged before disconnection.
5. No unsupported promise of tracking through deep sleep or power-off; native Find My instructions or independent tracker option documented.
6. Every permission, storage, key and network channel reviewed for security and privacy.
7. Hardware/OS/network matrix, performance budgets, long soak and release build green.
8. Signatures, notarization, EULA/privacy, merchant, website and store submissions completed with owner-controlled accounts.
9. Only verified milestones merged to `main`; tagged checkpoints represent actual known-good releases.

## Release interpretation

Security and media work remains on `perez` until required validation succeeds. Branch synchronization is not evidence of production readiness. No version tag should be created until its test/build gate passes.

# LaptopWatch — gated release checklist

**Status 2026-11-13: NOT RELEASE READY.** This checklist is an executable policy, not a marketing declaration.

## v0.1.0 audit & architecture
- [x] Source inventory and current architecture documented.
- [x] Severity-classified source defects recorded.
- [x] Target ADRs and threat model written.
- [ ] Mermaid diagrams extracted and rendered by Mermaid CLI.
- [ ] Docs reviewed and milestone merge/tag verified.

## v0.2.0 secure media and discovery
- [ ] All auth/tunnel traffic protected by validated pinned TLS and per-peer signed commands.
- [ ] Key enrollment, QR, unpair, expiration, revocation and replay/rate-limit tests green.
- [ ] Video + bidirectional audio WebRTC, fallback snapshots, mDNS/ICE, optional TURN.
- [ ] Android+Python+signaling builds and physical LAN/WAN latency recorded.

## v0.3.0 desktop client
- [ ] React/Tauri desktop app, integrated sidecar and safe lifecycle.
- [ ] Tray, opt-in auto-start, settings, QR, camera privacy affordances.
- [ ] Windows/macOS/Linux build checks and WebKitGTK risk test.

## v0.4.0 person detection
- [ ] Person detector after frame difference, no false alarm for non-people.
- [ ] Seven-scene metrics written with real test apparatus.

## v0.5.0 packaging and licensing
- [ ] Signed Windows executable/MSIX, Apple notarized dmg, Linux deb.
- [ ] Signed update and offline-verifiable license; entitlement checks.
- [ ] Clean-device installations, uninstall and updater verified.

## v0.6.0 polish and consent
- [ ] Three-step pairing onboarding + first-run lawful-use consent.
- [ ] Error/empty states, 48dp targets, TalkBack, dark/light, localization.
- [ ] Battery impact disclosure and explicit demo labeling.

## v0.7.0 performance
- [ ] Full performance table actual figures, sample counts and evidence.
- [ ] 24h soak, network handoffs, battery report, resource budgets met.

## v1.0.0 commercial release
- [ ] Owner-approved EULA, privacy policy, data safety and retention rules.
- [ ] Store accounts, identity verification, signing and legal rights verified.
- [ ] Public website, live privacy URL, customer support and licensed checkout.
- [ ] Real app screenshots, signed AAB, release notes and download checksums.
- [ ] Store submissions accepted and links/confirmation recorded.
- [ ] Microsoft Store MSIX accepted, winget manifest merged.
- [ ] Real legal/trademark check (not assumed from a search).
- [ ] No red gates; eight known-good no-ff merge milestones and annotated tags.

## Do not auto-green external gates

App store submission and acceptance, signing with owner keys, macOS notarization, billing agreements, trademark clearance, legal counsel review and physical device tests require authorized access, identity and real devices. If unavailable, record **BLOCKED**, not **PASS**. Never ship an unverified camera or security app as "100% secure".

# LaptopWatch security core — v0.2 development

This is a **tested but not yet integrated** component of the replacement Python sidecar. It is not a launcher or an Internet-exposable service.

Implemented: Ed25519 request signing transcript; SQLite paired-device registry, revocation and persistent replay denial; IP-window rate limiting; signed hash-chained append-only audit records and tamper verification.

Run from `desktop/src-python`:

```bash
python -m pip install -e . pytest
python -m pytest -q
```

**Local validation (2026-11-13):** Python 3.11+ isolated module, 11 passing pytest cases. This does not validate Android integration, Flask gateway, camera privacy, WAN threat resistance or an end-to-end system.

**Strict security boundary:** Do not activate or expose the existing Kotlin-embedded Flask server as part of this package. It still has hardcoded PIN and permissive network controls. Pairing is explicitly local-user-approved; no pairing endpoint, TLS listener or Android-side key exchange has been implemented yet.

**Remaining to reach v0.2.0:** keystore-backed private identity storage; QR handshake and desktop approval UI; authenticated TLS gateway with certificate binding; Android public-key signing and storage; signed control integration + audit on every action; strict rate limiting at gateway, WebRTC/Opus media, mDNS, ICE, TURN and verified physical tests.

Audit hashes are not externally anchored and cannot resist a fully compromised local administrator. Persist signed checkpoints to an independently protected location before making forensic-grade claims.

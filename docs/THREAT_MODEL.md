# LaptopWatch — security threat model (v0.1 baseline)

**This is a model, not a penetration-test report.** Current PIN and TLS implementation are insecure and must not be publicly exposed.

## Assets

Webcam/video frames; microphone/audio; laptop OS lock authority; paired public keys and private signing keys; consent choices; forensic event log and images; location/subject data; license entitlement and billing metadata; signaling credential and TURN usage.

## Attack surfaces and adversaries

| Boundary | Attacker | Primary abuse | Enforced target controls |
|---|---|---|---|
| Android ↔ LAN | Untrusted Wi-Fi guest / rogue AP | MITM or replay, control hijack, private feed exposure | TLS pin, request signatures, nonces, strict schema |
| Android ↔ Internet | WAN/relay attacker | Signaling injection, media interception, relay misuse | Authenticated signaling, DTLS-SRTP, short TURN credentials, quotas |
| Desktop ↔ LAN | Hostile LAN endpoint | Scan exposed Flask, guess default PIN, lock/siren abuse | Default loopback; opt-in secure gateway; rate limiting, scope |
| QR pairing | Nearby attacker / malicious QR | Bind rogue phone/laptop | Short pairing window, signed identity, visible desktop approval |
| Android handset | Theft or malware | Key extraction and covert access | Keystore hardware support, local lock/biometric option, remote revoke |
| Desktop host | Local user / malware | Read evidence, alter audit log or capture | Least privilege, OS key store, hardened file perms, no admin daemon |
| Updates/installers | Supply-chain attacker | Trojan sidecar, update substitution | Signed release channel, verified manifests, reproducible artifacts |
| Camera and microphone | Non-consensual operator | Covert surveillance or voice recording | Visible active indicator, opt-in, retention and deletion controls |
| Storage and exports | Phone backup, share target | Sensitive evidence leakage | Encrypted secrets; backup exclusion; scoped export with warnings |
| License/account | Entitlement or payment fraud | License cloning, account takeover | Signed offline leases, entitlement validation, no card data on device |

## Defend against

- **Credential misuse:** no default PIN, no URL credentials, per-device keys, short-lived pairing.
- **MITM/TLS downgrade:** validated TLS chain or pinned self-signed peer cert; no permissive hostname verifier.
- **Replay:** canonical Ed25519-signed requests, unique nonce, bounded timestamp, per-peer state.
- **Authorization bypass:** allowlisted actions, paired device status, unpair/revoke and limited capabilities.
- **Brute force and denial of service:** per-client and global throttling, bounded request body, request deadlines, concurrency limits.
- **Audit tampering:** append-only records with chained hash and MAC/signature, fsync, external export checkpoint. Hash chain alone is not proof against rewriting by privileged local users.
- **Unsafe capture:** camera access only while armed/explicit session active; release on disarm and OS privacy indicators never bypassed.

## Explicit non-goals / residual risks

Not protected against physical theft, a compromised operating system/administrator, a modified kernel, malicious person with user login, malicious camera firmware, a compromised TURN/signaling server causing denial of service, or a user who deliberately records unlawfully. Full-disk encryption, OS passwords, updates and physical security remain user responsibilities. No cryptographic scheme makes stolen data recoverable if the only copy was on the stolen laptop.

## Data retention and privacy

Default no third-party cloud evidence storage. Every recording/snapshot requires explicit user consent and a visible purpose. Document exact storage paths, retention/deletion, user access, and lawful audio recording before publication. Demo subject data must be labeled as fake and prohibited from real evidence export. Remote TURN may carry **encrypted** media packets; claiming "no third-party processing" needs precise provider disclosure.

## Security verification requirements

| Test | Expected |
|---|---|
| Wrong certificate / wrong pubkey | Disconnect, fail closed, no fallback to trust-all |
| Replay signed command | HTTP 401/409, no repeated action, log rejection |
| Tampered signed body | Rejected before side effects |
| Stale request / reused nonce | Rejected and rate-limited |
| Anonymous discovery | Can see only non-sensitive mDNS service metadata |
| Pair while pairing window closed | Denied |
| Unpaired remote lock or siren | Denied; no OS control |
| Oversized media upload | 413; bounded memory and disk |
| Camera disarmed | Device released; visible state shows off |
| Service shutdown and uninstall | Camera, audio and locks released; autostart disabled |
| Audit log modified / reordered | Verification fails and compromised log is flagged |
| Backup/restore | No private key or saved PIN exported |
| WAN unreachable | No anonymous LAN downgrade or silent insecure fallback |

## Prioritized remediation order

1. Disable permissive TLS, remove public default PIN and URL token auth **as an integrated authenticated protocol migration**.
2. Introduce paired-key authorization, constrained network gateway and auditable control operations.
3. Deploy media encryption, certified release process and keys stored outside source control.
4. Verify person-only alerts and full consent flow.
5. Complete adversarial tests with an untrusted LAN client and external security assessment.

The presence of this file does not attest that any threat has been mitigated.


## Additional evidence/location controls required (2026-11-14)

The Python gateway now exposes signed JPEG, MP4 and last-seen APIs and stores files in user-specific local state directories. **No release approval is implied.** A signed request is necessary but not sufficient to mitigate abuse, local malware, compromised pairing or involuntary recordings. Before shipping, add hardware-backed key handling, explicit consent UI, local data encryption, configurable retention/deletion, audit events on evidence access, disk usage quotas, media upload limits, and Android/desktop account revocation.

Location history is sensitive: never send it to an arbitrary URL or default telemetry endpoint, and never claim a fix without a real source and timestamp. The offline queue has no deployed remote sink; user-provided relay endpoints must enforce TLS pinning, signed device messages, encrypted storage, authorization and receipt replay protection. The design does not defend against an attacker who wipes the disk or turns the machine fully off.

# LaptopWatch — Product release and recovery operations

This runbook is for a controlled release host and designated independent reviewer.
**Source code does not constitute a distributable product or security approval.**
The source-level release tools are committed; genuine dependency locks, a clean
native checkout, qualified approver keys, real hardware and service accounts
must still be supplied by operators.

## 1. Reproducible dependency resolution

On a network-connected checkout with Node, Rust/Cargo, CocoaPods, **uv** and
native platform SDKs installed:

```bash
python ops/lock_dependencies.py --generate --platform all
python ops/lock_dependencies.py
python -m pytest -q ops/tests
```

This resolves/reviews the checked-in Node, Cargo, CocoaPods and **three Python**
requirement locks. Python requirements are generated with uv's universal,
all-extras, SHA-256-hashed compile. Lock verification checks source manifest
agreement, npm SHA-512 integrities, Cargo registry checksums, Pod spec checksums,
and Python pinned hashes/direct requirements. Generated resolution is **not**
proof of installation, native compilation, supplier trust or vulnerability
acceptance. Review the diff, investigate every new dependency, then commit real
locks through the normal `perez` → `main` flow; do not fabricate or
hand-edit package resolution hashes.

## 2. Build and exercise deliverable artifacts

```bash
python ops/verify_release.py --mode full --json /secure/release-results.json
```

Without the independently approved release acceptance manifest this command
**must return nonzero**, even when source/Python/Node/Cargo/native gates pass.
The runner now includes operations tests, the desktop Tauri/PyInstaller
installer build, Android debug+release APK/AAB build and iOS debug build plus
release archive on a macOS host. Separate trusted Windows, macOS and Linux
build hosts are needed for their own desktop packages. Check release package
signatures/notarization and install them on **actual supported devices**;
a compiled unsigned bundle alone is not a release.

## 3. Independent release-acceptance reports

Provide these eight human-reviewed Markdown reports in a secure directory
**outside the checkout**, with actual dated outputs and platform/OS/hardware
IDs, not placeholder results:

- `physical_devices.md` — installed laptop and phone acceptance, pairing, evidence, location, lock, alarm, mic/WebRTC and revocation
- `security_review.md` — independent threat/penetration review and unresolved findings
- `merchant_sandbox.md` — Paddle checkout, renewals, refunds, cancellation and account closure
- `signed_installers.md` — artifact hashes, issuer/OS code signing, notarization, Play/App Store acceptance
- `privacy_legal.md` — consent, retention/deletion, licenses, consumer legal and distribution approval
- `recovery.md` — actual encrypted restore tests, key rotation and incident-response exercise
- `reliability_soak.md` — 24-hour battery, CPU/RAM, reboot, offline/online, Doze, crash and latency evidence
- `remote_services.md` — real FCM, TURN, owner relay, GNSS, TLS and service monitoring

The proprietor must separately approve an Ed25519 release-approver public key
and pin it using `ops/provision_release_approver.py`.
**Do not put the private key in Git, a release log, or the evidence directory.**

```bash
python ops/provision_release_approver.py \
  --public-key-file /secure/release-approver-public.txt \
  --i-verified-independent-approval

# After the key-pinning commit is merged and the clean checkout matches
# the reviewed source revision, the designated independent approver runs:
python ops/sign_release_attestation.py \
  --evidence-dir /secure/laptopwatch-acceptance \
  --private-key-file /secure/release-approver-private.pem \
  --i-reviewed-and-approve-every-gate

python ops/verify_release.py --mode full \
  --acceptance-manifest /secure/laptopwatch-acceptance/manifest.json \
  --json /secure/release-results.json
```

Acceptance signatures are tied to a **single Git commit**, have a seven-day
issue lifetime, must be signed by the separately pinned approver key, and
cover exact SHA-256 hashes for every report. Any missing/altered report,
wrong commit, expired signature, unsafe path or dirty checkout fails.
They authenticate **who approved which reports**; they cannot prove the
claims in a report without external human/device verification. Signed
license-issuer keys are separate from release-approver keys.

## 4. Encrypted SQLite recovery

This provides an authenticated, consistent backup of **one local SQLite
database at a time**. It is **not** a full system/media backup or an export
of Paddle/Firebase state. It uses an operator-controlled 32-byte random
AES-256-GCM key. Protect it independently, outside the repository and
outside the backup volume. On POSIX it requires owner-only permissions.

```bash
python ops/sqlite_recovery.py backup \
  --database /srv/laptopwatch/billing.sqlite \
  --sealed-file /secure-backups/billing-snapshot.lwb \
  --key-file /secure/backup-key.raw

# Exercise a DR restore to a NEW destination, never over an active DB:
python ops/sqlite_recovery.py restore \
  --database /scratch/recovery/billing.sqlite \
  --sealed-file /secure-backups/billing-snapshot.lwb \
  --key-file /secure/backup-key.raw
```

Backup snapshots use a consistent SQLite backup transaction rather than
copying a live WAL file. Decryption requires the same key and an authentic,
untampered sealed file. A restore refuses to overwrite existing data.
This bounded in-memory implementation supports source SQLite databases
up to 128 MiB. For larger stores implement and certify a streaming encrypted
backup or managed database recovery process before launch. **Do not discard
the encryption key; lost keys mean unrecoverable backups.**

## 5. Remaining external acceptance

Provision reviewed release signing identities, real merchant approval,
trusted public issuer pins, remote infrastructure and physical test hardware.
Run the full matrix on the actual shipped binaries. Investigate/fix any
failures and reissue approval only for the resulting reviewed commit.

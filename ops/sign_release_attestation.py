"""Explicit offline approval of reports from tested product-release artifacts.

A release approver must review reports manually and possess an offline Ed25519
key that matches the separately approved public key in ops/release_trust.py.
No private key generation, distribution, or automatic approval occurs here.
"""
from __future__ import annotations

import argparse
import base64
from hashlib import sha256
import json
import os
from pathlib import Path
import stat
import subprocess
import sys
import time

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

try:
    from .release_attestation import REQUIRED, AcceptanceError, ROOT, verify
    from .release_trust import TRUSTED_RELEASE_APPROVER_PUBLIC_KEY_B64URL
except ImportError:
    from release_attestation import REQUIRED, AcceptanceError, ROOT, verify
    from release_trust import TRUSTED_RELEASE_APPROVER_PUBLIC_KEY_B64URL


def sign_reports(directory: Path, private_key_file: Path, head_sha: str,
                 trusted_pub: str, *, now: int | None = None) -> Path:
    now = int(time.time()) if now is None else now
    base = directory.resolve(strict=True)
    if base.is_relative_to(ROOT.resolve()):
        raise AcceptanceError('Acceptance reports must live outside the source checkout')
    key_file = private_key_file.resolve(strict=True)
    if key_file.is_relative_to(ROOT.resolve()) or key_file.is_relative_to(base):
        raise AcceptanceError('Approver private key cannot be in source or evidence directory')
    if os.name == 'posix' and stat.S_IMODE(key_file.stat().st_mode) & 0o077:
        raise AcceptanceError('Approver private key has unsafe filesystem permissions')
    private = serialization.load_pem_private_key(key_file.read_bytes(), password=None)
    if not isinstance(private, Ed25519PrivateKey):
        raise AcceptanceError('Release approver must use a protected Ed25519 key')
    actual_pub = base64.urlsafe_b64encode(private.public_key().public_bytes(
        serialization.Encoding.Raw, serialization.PublicFormat.Raw)).decode().rstrip('=')
    if actual_pub != trusted_pub or not trusted_pub:
        raise AcceptanceError('Offline approver key is not independently trusted')
    reports = {}
    for gate in sorted(REQUIRED):
        path = base / (gate + '.md')
        if path.is_symlink() or not path.is_file() or not 1 <= path.stat().st_size <= 10*1024*1024:
            raise AcceptanceError(f'Missing real acceptance report: {gate}')
        reports[gate] = {'path': path.name, 'sha256': sha256(path.read_bytes()).hexdigest()}
    if (base / 'manifest.json').exists():
        raise AcceptanceError('Do not overwrite an existing signed acceptance manifest')
    document = {'v': 1, 'commit_sha': head_sha, 'issued_at': now,
                'expires_at': now + 7*86400, 'evidence': reports}
    body = json.dumps(document, sort_keys=True, separators=(',', ':'),
                      ensure_ascii=True, allow_nan=False).encode('ascii')
    signature = base64.urlsafe_b64encode(private.sign(body)).decode().rstrip('=')
    manifest = base / 'manifest.json'
    with manifest.open('x', encoding='utf-8') as f:
        json.dump({**document, 'signature': signature}, f, sort_keys=True, indent=2)
        f.write('\n')
        f.flush()
        os.fsync(f.fileno())
    verify(manifest, trusted_pub, head_sha, now=now)
    return manifest


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--evidence-dir', type=Path, required=True)
    parser.add_argument('--private-key-file', type=Path, required=True)
    parser.add_argument('--i-reviewed-and-approve-every-gate', action='store_true')
    args = parser.parse_args(argv)
    if not args.i_reviewed_and_approve_every_gate:
        parser.error('A separate designated approver must explicitly approve all eight evidence reports')
    try:
        head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT,
                                       text=True, stderr=subprocess.DEVNULL).strip()
        dirty = subprocess.check_output(['git', 'status', '--porcelain'], cwd=ROOT,
                                        text=True, stderr=subprocess.DEVNULL).strip()
        if dirty:
            raise AcceptanceError('Signing a dirty checkout is prohibited')
        file = sign_reports(args.evidence_dir, args.private_key_file, head,
                            TRUSTED_RELEASE_APPROVER_PUBLIC_KEY_B64URL)
    except Exception:
        print('Release approval denied: reports, checkout or trusted signer invalid', file=sys.stderr)
        return 1
    print(f'Approval signed for {head}; manifest: {file}')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())

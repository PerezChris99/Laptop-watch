"""Fail-closed, independently signed real-world release evidence gate.

This checks signatures and file hashes, not the truth of an external safety
assessment. A designated approver must inspect every report before signing.
"""
from __future__ import annotations

import argparse
import base64
from hashlib import sha256
import json
from pathlib import Path, PurePosixPath
import re
import subprocess
import sys
import time

from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PublicKey

try:
    from .release_trust import TRUSTED_RELEASE_APPROVER_PUBLIC_KEY_B64URL
except ImportError:
    from release_trust import TRUSTED_RELEASE_APPROVER_PUBLIC_KEY_B64URL

ROOT = Path(__file__).resolve().parents[1]
REQUIRED = frozenset({
    'physical_devices', 'security_review', 'merchant_sandbox', 'signed_installers',
    'privacy_legal', 'recovery', 'reliability_soak', 'remote_services',
})
HEX = re.compile(r'^[0-9a-f]{64}$')
COMMIT = re.compile(r'^[0-9a-f]{40}$')
B64 = re.compile(r'^[A-Za-z0-9_-]+$')


class AcceptanceError(ValueError):
    """Release evidence is missing, stale, unsafe or unauthenticated."""


def _decode(value: str, expected: int) -> bytes:
    if not isinstance(value, str) or not B64.fullmatch(value):
        raise AcceptanceError('Release signature/key encoding invalid')
    try:
        raw = base64.urlsafe_b64decode(value + '=' * (-len(value) % 4))
    except (ValueError, base64.binascii.Error) as exc:
        raise AcceptanceError('Invalid base64url') from exc
    if len(raw) != expected or base64.urlsafe_b64encode(raw).decode().rstrip('=') != value:
        raise AcceptanceError('Release signature/key must be canonical')
    return raw


def verify(manifest_file: Path, trusted_approver: str, head_sha: str,
           *, now: int | None = None) -> dict:
    """Check owner-signed evidence for the exact source commit and gate set."""
    now = int(time.time()) if now is None else now
    if COMMIT.fullmatch(head_sha) is None:
        raise AcceptanceError('Invalid checkout commit')
    try:
        if not manifest_file.is_file() or manifest_file.stat().st_size > 131072:
            raise AcceptanceError('Release manifest missing or too large')
        payload = json.loads(manifest_file.read_text(encoding='utf-8'))
        if not isinstance(payload, dict) or set(payload) != {
                'v', 'commit_sha', 'issued_at', 'expires_at', 'evidence', 'signature'}:
            raise AcceptanceError('Invalid release manifest schema')
        signature = _decode(payload['signature'], 64)
        public = Ed25519PublicKey.from_public_bytes(_decode(trusted_approver, 32))
        unsigned = {key: value for key, value in payload.items() if key != 'signature'}
        message = json.dumps(unsigned, sort_keys=True, separators=(',', ':'),
                             ensure_ascii=True, allow_nan=False).encode('ascii')
        public.verify(signature, message)
        if payload['v'] != 1 or payload['commit_sha'] != head_sha:
            raise AcceptanceError('Approval belongs to another source revision')
        if type(payload['issued_at']) is not int or type(payload['expires_at']) is not int:
            raise AcceptanceError('Approval timestamps must be integer UTC epochs')
        if (payload['issued_at'] > now + 300 or payload['expires_at'] <= now
                or payload['expires_at'] - payload['issued_at'] > 30*86400):
            raise AcceptanceError('Approval expired or outside permitted validity')
        evidence = payload['evidence']
        if not isinstance(evidence, dict) or set(evidence) != REQUIRED:
            raise AcceptanceError('Release acceptance does not cover every required gate')
        root = manifest_file.resolve().parent
        for gate, details in evidence.items():
            if not isinstance(details, dict) or set(details) != {'path', 'sha256'}:
                raise AcceptanceError('Missing evidence path or digest')
            name, expected = details['path'], details['sha256']
            if not isinstance(name, str) or not name or len(name) > 256:
                raise AcceptanceError('Invalid evidence path')
            relative = PurePosixPath(name)
            if (relative.is_absolute() or '..' in relative.parts or
                    any(part.startswith('.') for part in relative.parts)):
                raise AcceptanceError('Evidence must be a safe relative path')
            if not isinstance(expected, str) or not HEX.fullmatch(expected):
                raise AcceptanceError('Invalid evidence digest')
            file = root.joinpath(*relative.parts)
            if file.is_symlink() or not file.is_file() or not file.resolve().is_relative_to(root):
                raise AcceptanceError('Evidence is absent, linked or outside approval directory')
            if not 1 <= file.stat().st_size <= 10*1024*1024:
                raise AcceptanceError('Evidence size outside allowed range')
            if sha256(file.read_bytes()).hexdigest() != expected:
                raise AcceptanceError('Evidence content differs from approved digest')
        return {'commit_sha': head_sha, 'verified_gates': sorted(REQUIRED)}
    except AcceptanceError:
        raise
    except Exception as exc:
        raise AcceptanceError('Signed release acceptance could not be verified') from exc


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--manifest', type=Path, required=True)
    options = parser.parse_args(argv)
    try:
        head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT,
                                       text=True, stderr=subprocess.DEVNULL).strip()
        dirty = subprocess.check_output(['git', 'status', '--porcelain'], cwd=ROOT,
                                        text=True, stderr=subprocess.DEVNULL).strip()
        if dirty:
            raise AcceptanceError('Release checkout must be clean')
        result = verify(options.manifest, TRUSTED_RELEASE_APPROVER_PUBLIC_KEY_B64URL, head)
    except (AcceptanceError, OSError, subprocess.CalledProcessError):
        print('BLOCKED: independently approved release evidence not verified', file=sys.stderr)
        return 1
    print(f'Verified signed release acceptance for {result["commit_sha"]}; '
          f'{len(result["verified_gates"])} evidence categories authenticated')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())

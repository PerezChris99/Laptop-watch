"""Install an independently reviewed release-approver PUBLIC key into source.

This does not create, read or export private keys. The proprietor must validate
fingerprint and signing authority before committing and signing a release.
"""
from __future__ import annotations

import argparse
import base64
from hashlib import sha256
from pathlib import Path
import re

TARGET = Path(__file__).resolve().with_name('release_trust.py')


def provision(public_key_file: Path, target: Path = TARGET) -> str:
    value = public_key_file.read_text(encoding='ascii').strip()
    if not re.fullmatch('[A-Za-z0-9_-]{43}', value):
        raise ValueError('Approver key must be a canonical raw 32-byte Ed25519 public key')
    raw = base64.urlsafe_b64decode(value + '=')
    if len(raw) != 32 or base64.urlsafe_b64encode(raw).decode().rstrip('=') != value:
        raise ValueError('Approver key is not canonical base64url')
    new_content = (
        '"""Independently approved Ed25519 public release-signing authority.\n'
        'Never substitute a key from remote build logs or release manifests.\n'
        '"""\n'
        + f"TRUSTED_RELEASE_APPROVER_PUBLIC_KEY_B64URL = '{value}'\n"
    )
    target.write_text(new_content, encoding='utf-8')
    return sha256(raw).hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--public-key-file', required=True, type=Path)
    parser.add_argument('--i-verified-independent-approval', action='store_true')
    args = parser.parse_args()
    if not args.i_verified_independent_approval:
        parser.error('Independent proprietor approval is required before pinning a release authority')
    print('Pinned reviewed release APPROVER public key; SHA256:',
          provision(args.public_key_file))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())

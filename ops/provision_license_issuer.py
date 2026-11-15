"""Pin an independently reviewed issuer PUBLIC key in the desktop release.

Never obtain this key from the billing service whose leases it authenticates.
Provision once per signed release. No secret key is required or accepted.
"""
from __future__ import annotations

import argparse
import base64
import hashlib
from pathlib import Path
import re
import tempfile

ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / 'desktop/src-python/laptopwatch/license_trust.py'
MOBILE_TARGET = ROOT / 'mobile/src/licensing/trust.js'
_B64 = re.compile(r'^[A-Za-z0-9_-]+$')


def parse_issuer_key(value: str) -> bytes:
    value = value.strip()
    if not _B64.fullmatch(value) or len(value) not in {43, 44}:
        raise ValueError('Issuer public key must be canonical base64url for 32 raw bytes')
    try:
        raw = base64.urlsafe_b64decode(value + '=' * (-len(value) % 4))
    except (ValueError, base64.binascii.Error) as exc:
        raise ValueError('Not base64url') from exc
    if len(raw) != 32 or base64.urlsafe_b64encode(raw).decode().rstrip('=') != value:
        raise ValueError('Invalid issuer public key length or encoding')
    return raw


def provision(public_key_file: Path, target: Path = TARGET, *, mobile_target: Path | None = None) -> str:
    key_text = public_key_file.read_text(encoding='ascii').strip()
    raw = parse_issuer_key(key_text)
    desired = ('''"""Pinned license issuer for this reviewed LaptopWatch distribution.

This PUBLIC key identifies the proprietor's Ed25519 license signer.
Changing it requires an independently approved rotation and new signed build.
"""\n'''
               + f"TRUSTED_ISSUER_PUBLIC_KEY_B64URL = '{key_text}'\n")
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.exists() and target.read_text(encoding='utf-8') == desired and mobile_target is None:
        return hashlib.sha256(raw).hexdigest()
    with tempfile.NamedTemporaryFile(mode='w', encoding='utf-8', dir=target.parent,
                                     prefix='.issuer-', suffix='.tmp', delete=False) as f:
        f.write(desired)
        temporary = Path(f.name)
    temporary.replace(target)
    if mobile_target is not None:
        mobile_target.parent.mkdir(parents=True, exist_ok=True)
        generated = ("// Independently pinned license issuer, public key only.\n"
                     + f"export const TRUSTED_ISSUER_PUBLIC_KEY_B64URL = '{key_text}';\n")
        mobile_target.write_text(generated, encoding='utf-8')
    return hashlib.sha256(raw).hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--public-key-file', type=Path, required=True)
    args = parser.parse_args()
    fingerprint = provision(args.public_key_file, mobile_target=MOBILE_TARGET)
    print(f'Issuer PUBLIC key pinned. SHA256 fingerprint: {fingerprint}')
    print('Independently review this key and sign the exact desktop release artifact.')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())

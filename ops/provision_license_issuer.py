"""Pin an independently reviewed issuer PUBLIC key in the desktop release.

Never obtain this key from the billing service whose leases it authenticates.
Provision once per signed release. No secret key is required or accepted.
"""
from __future__ import annotations

import argparse
import ast
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


def verify_issuer_pins(desktop_path: Path = TARGET, mobile_path: Path = MOBILE_TARGET) -> str:
    """Fail a commercial build if either artifact has a missing or different pin.

    Independently verify the approved public key through an operator channel.
    Never trust a key returned by the API that the key is meant to authenticate.
    """
    desktop = ast.parse(desktop_path.read_text(encoding='utf-8'))
    assignments = [
        node.value.value for node in desktop.body
        if isinstance(node, ast.Assign)
        and any(isinstance(target, ast.Name)
                and target.id == 'TRUSTED_ISSUER_PUBLIC_KEY_B64URL'
                for target in node.targets)
        and isinstance(node.value, ast.Constant)
        and isinstance(node.value.value, str)
    ]
    text = mobile_path.read_text(encoding='utf-8')
    mobile = re.findall(
        r"^export const TRUSTED_ISSUER_PUBLIC_KEY_B64URL = ['\\\"]([A-Za-z0-9_-]*)['\\\"];?$",
        text, flags=re.MULTILINE,
    )
    if len(assignments) != 1 or len(mobile) != 1 or assignments[0] != mobile[0]:
        raise ValueError('Desktop/mobile commercial issuer pins missing or mismatched')
    return hashlib.sha256(parse_issuer_key(assignments[0])).hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument('--public-key-file', type=Path)
    mode.add_argument('--verify-only', action='store_true')
    args = parser.parse_args()
    if args.verify_only:
        fingerprint = verify_issuer_pins()
        print(f'Commercial issuer pins agree. Public SHA256 fingerprint: {fingerprint}')
        return 0
    fingerprint = provision(args.public_key_file, mobile_target=MOBILE_TARGET)
    print(f'Issuer PUBLIC key pinned. SHA256 fingerprint: {fingerprint}')
    print('Independently review this key and sign the exact desktop and mobile release artifacts.')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())

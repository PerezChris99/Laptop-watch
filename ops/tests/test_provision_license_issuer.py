import base64
import secrets
import pytest
from ops.provision_license_issuer import parse_issuer_key, provision, verify_issuer_pins


def test_public_key_provisioning_is_canonical_and_idempotent(tmp_path):
    value = base64.urlsafe_b64encode(secrets.token_bytes(32)).decode().rstrip('=')
    source = tmp_path/'issuer.txt'
    dest = tmp_path/'license_trust.py'
    source.write_text(value, encoding='ascii')
    digest = provision(source, dest)
    assert len(digest) == 64
    assert value in dest.read_text(encoding='utf-8')
    assert provision(source, dest) == digest
    assert parse_issuer_key(value) == base64.urlsafe_b64decode(value+'=')


def test_reject_private_or_noncanonical_key_material():
    for value in ('', 'abc', 'abc=', '-----BEGIN PRIVATE KEY-----',
                  'a'*42, 'a'*45, 'a'*43+';'):
        with pytest.raises(ValueError):
            parse_issuer_key(value)


def test_commercial_build_requires_identical_trusted_desktop_and_mobile_pins(tmp_path):
    value = base64.urlsafe_b64encode(secrets.token_bytes(32)).decode().rstrip('=')
    approved = tmp_path / 'independently-approved-key.txt'
    desktop = tmp_path / 'license_trust.py'
    mobile = tmp_path / 'trust.js'
    approved.write_text(value, encoding='ascii')
    digest = provision(approved, desktop, mobile_target=mobile)
    assert verify_issuer_pins(desktop, mobile) == digest
    mobile.write_text("export const TRUSTED_ISSUER_PUBLIC_KEY_B64URL = '';\n")
    with pytest.raises(ValueError, match='issuer'):
        verify_issuer_pins(desktop, mobile)


def test_commercial_build_rejects_invalid_empty_pins(tmp_path):
    desktop = tmp_path / 'license_trust.py'
    mobile = tmp_path / 'trust.js'
    desktop.write_text("TRUSTED_ISSUER_PUBLIC_KEY_B64URL = ''\n")
    mobile.write_text("export const TRUSTED_ISSUER_PUBLIC_KEY_B64URL = '';\n")
    with pytest.raises(ValueError):
        verify_issuer_pins(desktop, mobile)

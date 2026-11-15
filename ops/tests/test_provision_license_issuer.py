import base64
import secrets
import pytest
from ops.provision_license_issuer import parse_issuer_key, provision


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

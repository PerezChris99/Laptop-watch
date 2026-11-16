"""Release approver public key provisioning must be canonical and explicit."""
import base64
import pytest
from ops.provision_release_approver import provision


def test_public_key_installed_without_private_material(tmp_path):
    key=tmp_path/'source.txt'
    key.write_text(base64.urlsafe_b64encode(bytes(range(32))).decode().rstrip('='))
    dest=tmp_path/'trust.py'
    digest=provision(key,dest)
    assert len(digest)==64
    assert key.read_text() in dest.read_text()
    assert provision(key,dest)==digest


def test_rejects_private_pem_and_invalid_length(tmp_path):
    key=tmp_path/'source.txt'
    for s in ('', 'bad', '-----BEGIN PRIVATE KEY-----', 'x'*45):
        key.write_text(s)
        with pytest.raises(ValueError):provision(key,tmp_path/'trust.py')

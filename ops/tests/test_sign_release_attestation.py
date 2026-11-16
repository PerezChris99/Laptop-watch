"""Explicit report signing rejects absent reports, unsafe keys and untrusted approvers."""
import base64
import os
import pytest
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from cryptography.hazmat.primitives import serialization
from ops.release_attestation import REQUIRED, verify, AcceptanceError
from ops.sign_release_attestation import sign_reports

HEAD='b'*40


def setup(tmp_path):
    evidence=tmp_path/'reports';evidence.mkdir()
    for gate in REQUIRED:(evidence/(gate+'.md')).write_text('reviewed real test results for '+gate)
    key=Ed25519PrivateKey.generate()
    secret=tmp_path/'private-key.pem'
    secret.write_bytes(key.private_bytes(serialization.Encoding.PEM,
        serialization.PrivateFormat.PKCS8,serialization.NoEncryption()))
    os.chmod(secret,0o600)
    pub=base64.urlsafe_b64encode(key.public_key().public_bytes_raw()).decode().rstrip('=')
    return evidence,secret,pub


def test_signed_reports_are_verifiable_without_private_key(tmp_path):
    reports, secret, pub=setup(tmp_path)
    manifest=sign_reports(reports,secret,HEAD,pub,now=1784210000)
    assert len(verify(manifest,pub,HEAD,now=1784210000)['verified_gates'])==8
    with pytest.raises(AcceptanceError,match='overwrite'):
        sign_reports(reports,secret,HEAD,pub,now=1784210000)


def test_missing_reports_and_wrong_signer_are_rejected(tmp_path):
    reports,secret,pub=setup(tmp_path)
    with pytest.raises(AcceptanceError,match='trusted'):
        sign_reports(reports,secret,HEAD,'',now=1784210000)
    (reports/'physical_devices.md').unlink()
    with pytest.raises(AcceptanceError,match='physical_devices'):
        sign_reports(reports,secret,HEAD,pub,now=1784210000)

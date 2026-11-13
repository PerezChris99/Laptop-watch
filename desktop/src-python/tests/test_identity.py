from datetime import datetime, timezone
from pathlib import Path
import uuid

from cryptography import x509
import pytest

from laptopwatch.cli import main
from laptopwatch.identity import initialize_identity, get_audit_key
from laptopwatch.security import b64url
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from cryptography.hazmat.primitives.serialization import Encoding, PublicFormat


def test_tls_identity_pinned_and_stable(tmp_path):
    first = initialize_identity(tmp_path)
    second = initialize_identity(tmp_path)
    assert first == second
    assert len(first['tls_fingerprint_sha256']) == 64
    cert = x509.load_pem_x509_certificate((tmp_path / 'tls_cert.pem').read_bytes())
    assert cert.not_valid_after_utc > datetime.now(timezone.utc)
    assert len(first['audit_pubkey_hex']) == 64


def test_missing_key_in_bundle_fails_closed(tmp_path):
    initialize_identity(tmp_path)
    (tmp_path / 'audit_key.pem').unlink()
    with pytest.raises(RuntimeError, match='partially provisioned'):
        initialize_identity(tmp_path)


def test_local_manual_pairing_accepts_explicit_approval(tmp_path, monkeypatch):
    initialize_identity(tmp_path)
    key = Ed25519PrivateKey.generate()
    device = str(uuid.uuid4())
    pub = b64url(key.public_key().public_bytes(Encoding.Raw, PublicFormat.Raw))
    monkeypatch.setattr('builtins.input', lambda prompt: 'PAIR')
    assert main(['--state-dir', str(tmp_path), 'pair', '--device-id', device,
                 '--public-key-b64url', pub]) == 0
    assert (tmp_path / 'audit.jsonl').exists()
    assert main(['--state-dir', str(tmp_path), 'revoke', '--device-id', device]) == 0
    assert len((tmp_path / 'audit.jsonl').read_text().splitlines()) == 2


def test_local_pairing_denied_without_approval(tmp_path, monkeypatch):
    initialize_identity(tmp_path)
    key = Ed25519PrivateKey.generate()
    device = str(uuid.uuid4())
    pub = b64url(key.public_key().public_bytes(Encoding.Raw, PublicFormat.Raw))
    monkeypatch.setattr('builtins.input', lambda prompt: 'NO')
    assert main(['--state-dir', str(tmp_path), 'pair', '--device-id', device,
                 '--public-key-b64url', pub]) == 1

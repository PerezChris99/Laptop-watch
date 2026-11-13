"""Development-only local TLS and audit key provisioning.

Production builds must protect long-lived private keys via OS Keychain, CNG or
Secret Service. Avoid claiming file mode 0600 alone defeats local malware.
"""
from __future__ import annotations

from datetime import datetime, timedelta, timezone
from hashlib import sha256
import ipaddress
import os
from pathlib import Path
import uuid

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec, ed25519
from cryptography.x509.oid import NameOID


def _private_file(path: Path, data: bytes) -> None:
    fd = os.open(path, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    with os.fdopen(fd, 'wb') as out:
        out.write(data)
        os.fsync(out.fileno())
    if os.name == 'posix':
        os.chmod(path, 0o600)


def initialize_identity(state_dir: Path) -> dict[str, str]:
    state_dir = Path(state_dir)
    state_dir.mkdir(parents=True, exist_ok=True, mode=0o700)
    if os.name == 'posix':
        os.chmod(state_dir, 0o700)
    audit_pem, tls_pem, cert_pem = (state_dir / 'audit_key.pem', state_dir / 'tls_key.pem', state_dir / 'tls_cert.pem')
    exists = [p.exists() for p in (audit_pem, tls_pem, cert_pem)]
    if any(exists) and not all(exists):
        raise RuntimeError('partially provisioned identity; inspect keys manually, never overwrite silently')
    if not any(exists):
        audit_key = ed25519.Ed25519PrivateKey.generate()
        _private_file(audit_pem, audit_key.private_bytes(serialization.Encoding.PEM,
                      serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))
        tls_key = ec.generate_private_key(ec.SECP256R1())
        _private_file(tls_pem, tls_key.private_bytes(serialization.Encoding.PEM,
                      serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))
        now = datetime.now(timezone.utc)
        subject = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, 'LaptopWatch Desktop')])
        certificate = (x509.CertificateBuilder()
            .subject_name(subject).issuer_name(subject).public_key(tls_key.public_key())
            .serial_number(x509.random_serial_number())
            .not_valid_before(now - timedelta(minutes=5))
            .not_valid_after(now + timedelta(days=90))
            .add_extension(x509.BasicConstraints(ca=False, path_length=None), critical=True)
            .add_extension(x509.SubjectAlternativeName([x509.DNSName('localhost'),
                           x509.IPAddress(ipaddress.ip_address('127.0.0.1'))]), critical=False)
            .sign(tls_key, hashes.SHA256()))
        _private_file(cert_pem, certificate.public_bytes(serialization.Encoding.PEM))
    cert = x509.load_pem_x509_certificate(cert_pem.read_bytes())
    now = datetime.now(timezone.utc)
    if cert.not_valid_after_utc <= now:
        raise RuntimeError('TLS certificate expired; rotate by an explicit re-pairing procedure')
    audit = serialization.load_pem_private_key(audit_pem.read_bytes(), password=None)
    if not isinstance(audit, ed25519.Ed25519PrivateKey):
        raise RuntimeError('audit key type mismatch')
    return {'tls_fingerprint_sha256': sha256(cert.public_bytes(serialization.Encoding.DER)).hexdigest(),
            'audit_pubkey_hex': audit.public_key().public_bytes(serialization.Encoding.Raw,
                                 serialization.PublicFormat.Raw).hex()}


def get_audit_key(state_dir: Path) -> ed25519.Ed25519PrivateKey:
    path = Path(state_dir) / 'audit_key.pem'
    key = serialization.load_pem_private_key(path.read_bytes(), password=None)
    if not isinstance(key, ed25519.Ed25519PrivateKey):
        raise RuntimeError('unexpected audit key format')
    return key

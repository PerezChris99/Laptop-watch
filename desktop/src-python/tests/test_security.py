import base64
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import sqlite3
import time
import uuid

import pytest
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from cryptography.hazmat.primitives.serialization import Encoding, PublicFormat

from laptopwatch.security import (
    AuthenticationError, PairingRegistry, SignedRequestVerifier,
    SlidingWindowLimiter, b64url, canonical_request, sign_request,
)
from laptopwatch.audit import AuditIntegrityError, SignedAuditLog


@pytest.fixture
def pair(tmp_path):
    private = Ed25519PrivateKey.generate()
    identity = str(uuid.uuid4())
    public = private.public_key().public_bytes(Encoding.Raw, PublicFormat.Raw)
    registry = PairingRegistry(tmp_path / "paired.db")
    registry.pair(identity, public, locally_approved=True)
    yield private, identity, registry
    registry.close()


def request(private, identity, *, ts=None, body=b'{}', method="POST", path="/api/lock", nonce=None):
    ts = int(time.time()) if ts is None else ts
    nonce = b64url(bytes(range(16))) if nonce is None else nonce
    return sign_request(private, method=method, path=path, body=body, device_id=identity, timestamp=ts, nonce=nonce)


def test_local_approval_required(tmp_path):
    registry = PairingRegistry(tmp_path / "db")
    k = Ed25519PrivateKey.generate()
    with pytest.raises(PermissionError):
        registry.pair(str(uuid.uuid4()), k.public_key().public_bytes(Encoding.Raw, PublicFormat.Raw))
    registry.close()


def test_valid_request_and_replay_denied(pair):
    private, identity, registry = pair
    headers = request(private, identity)
    verifier = SignedRequestVerifier(registry)
    assert verifier.verify("POST", "/api/lock", b'{}', headers, client_ip="127.0.0.1") == identity
    with pytest.raises(AuthenticationError, match="replayed"):
        verifier.verify("POST", "/api/lock", b'{}', headers, client_ip="127.0.0.1")


def test_nonce_survives_restart(tmp_path):
    identity = str(uuid.uuid4())
    key = Ed25519PrivateKey.generate()
    pub = key.public_key().public_bytes(Encoding.Raw, PublicFormat.Raw)
    path = tmp_path / 'db'
    a = PairingRegistry(path)
    a.pair(identity, pub, locally_approved=True)
    heads = request(key, identity)
    SignedRequestVerifier(a).verify('POST', '/api/lock', b'{}', heads, client_ip='127.0.0.1')
    a.close()
    b = PairingRegistry(path)
    with pytest.raises(AuthenticationError, match='replayed'):
        SignedRequestVerifier(b).verify('POST', '/api/lock', b'{}', heads, client_ip='127.0.0.1')
    b.close()


def test_body_tamper_and_wrong_method(pair):
    k, device, registry = pair
    headers = request(k, device)
    verifier = SignedRequestVerifier(registry)
    with pytest.raises(AuthenticationError, match="signature"):
        verifier.verify("POST", "/api/lock", b'{"tampered":true}', headers, client_ip="host")
    with pytest.raises(AuthenticationError, match="signature"):
        verifier.verify("GET", "/api/lock", b'{}', headers, client_ip="host")


def test_expiration_and_revocation(pair):
    k, device, reg = pair
    now = int(time.time())
    verifier = SignedRequestVerifier(reg)
    with pytest.raises(AuthenticationError, match="expired"):
        verifier.verify("POST", "/api/lock", b'{}', request(k, device, ts=now-70), client_ip="host", now=now)
    reg.revoke(device)
    with pytest.raises(AuthenticationError, match="revoked"):
        verifier.verify("POST", "/api/lock", b'{}', request(k, device, ts=now), client_ip="host", now=now)


def test_wrong_key_rejected(pair):
    _, identity, registry = pair
    other = Ed25519PrivateKey.generate()
    with pytest.raises(AuthenticationError, match="signature"):
        SignedRequestVerifier(registry).verify("POST", "/api/lock", b'{}', request(other, identity), client_ip="host")


def test_no_url_query_or_double_slash(pair):
    k, identity, registry = pair
    for path in ['/api/lock?pin=1234', '/api//lock', '/api/../lock', '/not-api/lock']:
        with pytest.raises(AuthenticationError):
            request(k, identity, path=path)


def test_ip_rate_limit(pair):
    k, identity, reg = pair
    rate = SlidingWindowLimiter(max_attempts=2, period=60)
    verifier = SignedRequestVerifier(reg, rate)
    with pytest.raises(AuthenticationError):
        verifier.verify('POST', '/api/lock', b'', {}, client_ip='host', now=1)
    with pytest.raises(AuthenticationError):
        verifier.verify('POST', '/api/lock', b'', {}, client_ip='host', now=2)
    with pytest.raises(AuthenticationError, match="rate limited"):
        verifier.verify('POST', '/api/lock', b'', {}, client_ip='host', now=3)


def test_threaded_double_claim(pair):
    k, device, reg = pair
    verifier = SignedRequestVerifier(reg, SlidingWindowLimiter(max_attempts=100))
    heads = request(k, device)
    def attempt(i):
        try:
            verifier.verify('POST', '/api/lock', b'{}', heads, client_ip=str(i))
            return True
        except AuthenticationError:
            return False
    with ThreadPoolExecutor(max_workers=8) as pool:
        assert sum(pool.map(attempt, range(30))) == 1


def test_audit_integrity_and_tamper(tmp_path):
    key = Ed25519PrivateKey.generate()
    path = tmp_path / 'audit.jsonl'
    log = SignedAuditLog(path, key)
    first = log.append('lock', 'device', result='success')
    second = log.append('alarm', 'device', result='success', metadata={'reason': 'owner-action'})
    assert first != second
    assert SignedAuditLog.verify(path, key.public_key()) == second
    raw = path.read_bytes().replace(b'owner-action', b'other-action')
    path.write_bytes(raw)
    with pytest.raises(AuditIntegrityError):
        SignedAuditLog(path, key)


def test_audit_secret_metadata_rejected(tmp_path):
    log = SignedAuditLog(tmp_path / 'log', Ed25519PrivateKey.generate())
    with pytest.raises(ValueError):
        log.append('lock', 'device', result='success', metadata={'security_pin': '1234'})

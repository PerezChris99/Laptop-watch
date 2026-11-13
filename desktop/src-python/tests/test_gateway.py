from pathlib import Path
import time
import uuid

from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from cryptography.hazmat.primitives.serialization import Encoding, PublicFormat
from fastapi.testclient import TestClient
import pytest

from laptopwatch.audit import SignedAuditLog
from laptopwatch.gateway import create_app, GatewayState
from laptopwatch.security import PairingRegistry, SignedRequestVerifier, SlidingWindowLimiter, b64url, sign_request


class FakeController:
    def __init__(self):
        self.locks = 0
        self.alarms = 0
        self.snapshots = 0
    def lock(self):
        self.locks += 1
        return True
    def alarm(self):
        self.alarms += 1
        return True
    def snapshot(self):
        self.snapshots += 1
        return b'\xff\xd8\xff\xd9'


@pytest.fixture
def gateway(tmp_path):
    identity = str(uuid.uuid4())
    key = Ed25519PrivateKey.generate()
    register = PairingRegistry(tmp_path / 'paired.db')
    register.pair(identity, key.public_key().public_bytes(Encoding.Raw, PublicFormat.Raw), locally_approved=True)
    ctrl = FakeController()
    audit = SignedAuditLog(tmp_path / 'audit.log', Ed25519PrivateKey.generate())
    state = GatewayState(SignedRequestVerifier(register, SlidingWindowLimiter(max_attempts=100)), audit, ctrl)
    client = TestClient(create_app(state), base_url='https://localhost')
    yield client, identity, key, ctrl, register, state
    client.close()
    register.close()


def signed(client, identity, key, method, path, body=b'', nonce=None, url=None):
    nonce = b64url(uuid.uuid4().bytes) if nonce is None else nonce
    headers = sign_request(key, device_id=identity, method=method, path=path, body=body,
                           timestamp=int(time.time()), nonce=nonce)
    return client.request(method, url or path, content=body, headers=headers)


def test_requires_authenticated_signature(gateway):
    client, identity, key, ctrl, reg, state = gateway
    r = client.post('/api/lock')
    assert r.status_code == 401
    assert ctrl.locks == 0


def test_signed_lock_and_alarm_with_audit(gateway):
    client, identity, key, ctrl, reg, state = gateway
    assert signed(client, identity, key, 'POST', '/api/lock').json() == {'success': True}
    assert signed(client, identity, key, 'POST', '/api/alarm').status_code == 200
    assert ctrl.locks == ctrl.alarms == 1
    records = (Path(state.audit.path)).read_text().splitlines()
    assert len(records) == 2


def test_camera_fails_closed_when_disarmed(gateway):
    client, identity, key, ctrl, reg, state = gateway
    r = signed(client, identity, key, 'GET', '/api/camera/frame')
    assert r.status_code == 409
    assert ctrl.snapshots == 0
    assert signed(client, identity, key, 'POST', '/api/config/armed', b'{"armed":true}').status_code == 200
    r = signed(client, identity, key, 'GET', '/api/camera/frame')
    assert r.status_code == 200
    assert r.headers['content-type'] == 'image/jpeg'
    assert ctrl.snapshots == 1


def test_gateway_blocks_query_token_even_with_valid_signature(gateway):
    client, identity, key, ctrl, reg, state = gateway
    r = signed(client, identity, key, 'POST', '/api/lock', url='/api/lock?pin=7890')
    assert r.status_code == 400
    assert ctrl.locks == 0


def test_gateway_replay_does_not_act_twice(gateway):
    client, identity, key, ctrl, reg, state = gateway
    nonce = b64url(uuid.uuid4().bytes)
    assert signed(client, identity, key, 'POST', '/api/lock', nonce=nonce).status_code == 200
    assert signed(client, identity, key, 'POST', '/api/lock', nonce=nonce).status_code == 401
    assert ctrl.locks == 1


def test_gateway_rejects_revoked_device(gateway):
    client, identity, key, ctrl, reg, state = gateway
    reg.revoke(identity)
    assert signed(client, identity, key, 'POST', '/api/lock').status_code == 401
    assert ctrl.locks == 0


def test_oversize_body_denied(gateway):
    client, identity, key, ctrl, reg, state = gateway
    assert client.post('/api/lock', content=b'x' * (65536 + 1)).status_code == 413
    assert ctrl.locks == 0


def test_unsigned_remote_http_denied(tmp_path):
    from fastapi.testclient import TestClient
    from laptopwatch.gateway import create_app
    from laptopwatch.security import PairingRegistry, SignedRequestVerifier
    class Dummy: pass
    registry = PairingRegistry(tmp_path / 'auth.db')
    app = create_app(GatewayState(SignedRequestVerifier(registry),
                                  SignedAuditLog(tmp_path / 'log', Ed25519PrivateKey.generate()), Dummy()))
    client = TestClient(app, base_url='http://remote-host')
    assert client.get('/api/status').status_code == 403
    client.close()
    registry.close()


def test_invalid_arm_payload_rejected(gateway):
    client, identity, key, ctrl, reg, state = gateway
    assert signed(client, identity, key, 'POST', '/api/config/armed', b'{"armed":"true"}').status_code == 400
    assert not state.get_armed()

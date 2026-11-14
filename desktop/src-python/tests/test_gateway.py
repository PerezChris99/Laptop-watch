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


def test_signed_photo_mp4_download_and_disarm(gateway, tmp_path):
    """Gateway is safe by default; a paired identity may capture only after arming."""
    from laptopwatch.evidence import VideoResult
    client, identity, key, ctrl, reg, state = gateway

    class FakeEvidence:
        max_duration_seconds = 180.0
        def __init__(self):
            self.directory = tmp_path
            self.recording = False

        def snapshot(self):
            return b'\\xff\\xd8\\xff\\xd9'

        def take_photo(self):
            evidence_id = 'a' * 32
            filename = f'photo-{evidence_id}.jpg'
            (self.directory / filename).write_bytes(self.snapshot())
            return {'id': evidence_id, 'kind': 'photo', 'filename': filename, 'size_bytes': 4}

        def start_recording(self):
            self.recording = True
            return 'b' * 32

        def stop_recording(self):
            assert self.recording
            self.recording = False
            filename = 'video-' + 'b' * 32 + '.mp4'
            (self.directory / filename).write_bytes(b'test-video')
            return VideoResult('b' * 32, 3, 10, filename)

        def stop_if_recording(self):
            self.recording = False

    fake = FakeEvidence()
    state.evidence = fake
    assert signed(client, identity, key, 'POST', '/api/camera/photo').status_code == 409
    assert signed(client, identity, key, 'POST', '/api/camera/record/start').status_code == 409
    assert signed(client, identity, key, 'POST', '/api/config/armed', b'{"armed":true}').status_code == 200
    photo = signed(client, identity, key, 'POST', '/api/camera/photo')
    assert photo.status_code == 200
    assert photo.json()['kind'] == 'photo'
    img = signed(client, identity, key, 'GET', '/api/evidence/photo/' + 'a' * 32)
    assert img.status_code == 200
    assert img.content == b'\\xff\\xd8\\xff\\xd9'
    assert signed(client, identity, key, 'POST', '/api/camera/record/start').status_code == 200
    clip = signed(client, identity, key, 'POST', '/api/camera/record/stop')
    assert clip.status_code == 200
    assert clip.json()['frames'] == 3
    mp4 = signed(client, identity, key, 'GET', '/api/evidence/video/' + 'b' * 32)
    assert mp4.status_code == 200 and mp4.content == b'test-video'
    assert signed(client, identity, key, 'POST', '/api/config/armed', b'{"armed":false}').status_code == 200
    assert signed(client, identity, key, 'POST', '/api/camera/photo').status_code == 409


def test_location_returns_only_a_stored_real_fix(gateway, tmp_path):
    from laptopwatch.tracking import LocationFix, LastSeenStore, TrackingService
    client, identity, key, ctrl, reg, state = gateway
    assert signed(client, identity, key, 'GET', '/api/location/last-seen').status_code == 503

    now_ms = int(time.time() * 1000)
    store = LastSeenStore(tmp_path / 'lastseen.db')
    try:
        state.tracking = TrackingService(store, receiver=None)
        assert signed(client, identity, key, 'GET', '/api/location/last-seen').status_code == 404
        store.add(LocationFix(0.3476, 32.5825, 17.5, now_ms), now_ms=now_ms)
        response = signed(client, identity, key, 'GET', '/api/location/last-seen')
        assert response.status_code == 200
        assert response.json()['accuracy_meters'] == 17.5
        assert response.json()['provider'] == 'gpsd-gnss'
    finally:
        store.close()

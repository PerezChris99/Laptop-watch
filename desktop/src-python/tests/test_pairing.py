"""QR pairing: in-person desktop certificate pin and explicit phone approval."""
from datetime import datetime, timezone
import time
import uuid
from urllib.parse import urlparse, parse_qs
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from cryptography.hazmat.primitives.serialization import Encoding, PublicFormat
import pytest
from fastapi.testclient import TestClient

from laptopwatch.audit import SignedAuditLog
from laptopwatch.gateway import GatewayState, create_app
from laptopwatch.pairing import (
    PairingCoordinator, qr_transcript, phone_transcript, validate_host
)
from laptopwatch.security import (
    PairingRegistry, SignedRequestVerifier, b64url, unb64url
)


def setup_pairing(tmp_path, now=None):
    registry = PairingRegistry(tmp_path / "paired.sqlite")
    coordinator = PairingCoordinator(tmp_path / "pairing.sqlite", registry)
    owner = Ed25519PrivateKey.generate()
    public = owner.public_key().public_bytes(Encoding.Raw, PublicFormat.Raw)
    timestamp = int(time.time()) if now is None else now
    invite = coordinator.create_qr(
        host="192.168.1.25", port=8765, laptop_id=str(uuid.uuid4()),
        public_key=public, fingerprint_hex="a" * 64,
        signing_key=owner, now=timestamp,
    )
    params = {k: v[0] for k, v in parse_qs(urlparse(invite).query).items()}
    return registry, coordinator, owner, public, timestamp, params


def phone_payload(params):
    key = Ed25519PrivateKey.generate()
    identity = str(uuid.uuid4())
    public = b64url(key.public_key().public_bytes(Encoding.Raw, PublicFormat.Raw))
    proof = b64url(key.sign(phone_transcript(identity, public, params["nonce"])))
    return {"device_id": identity, "pubkey": public, "proof": proof,
            "nonce": params["nonce"], "label": "Owner phone"}


def test_signed_qr_mutual_phone_proof_and_approval(tmp_path):
    registry, coordinator, desktop_key, public, now, params = setup_pairing(tmp_path)
    desktop_key.public_key().verify(
        unb64url(params["sig"], 64),
        qr_transcript(params["id"], params["host"], int(params["port"]),
                      params["pk"], params["fp"], params["nonce"], int(params["exp"]))
    )
    payload = phone_payload(params)
    pending_id = coordinator.request(
        device_id=payload["device_id"], pubkey=payload["pubkey"],
        challenge=payload["nonce"], proof=payload["proof"],
        label=payload["label"], now=now + 2
    )
    assert registry.get_public_key(payload["device_id"]) is None
    assert coordinator.pending(now=now + 2)[0]["request_id"] == pending_id
    assert coordinator.approve(pending_id, now=now + 3) == payload["device_id"]
    assert registry.get_public_key(payload["device_id"]) is not None
    assert not coordinator.active(now=now + 3)
    coordinator.close(); registry.close()


def test_pair_expiration_and_fake_proof_rejected(tmp_path):
    registry, coordinator, key, pub, now, params = setup_pairing(tmp_path, now=1_700_000_000)
    payload = phone_payload(params)
    payload["proof"] = b64url(b"x" * 64)
    with pytest.raises(Exception):
        coordinator.request(device_id=payload["device_id"], pubkey=payload["pubkey"],
                            challenge=payload["nonce"], proof=payload["proof"],
                            label=payload["label"], now=now + 1)
    payload = phone_payload(params)
    with pytest.raises(Exception):
        coordinator.request(device_id=payload["device_id"], pubkey=payload["pubkey"],
                            challenge=payload["nonce"], proof=payload["proof"],
                            label=payload["label"], now=now + 130)
    coordinator.close(); registry.close()


def test_untrusted_public_ip_rejected():
    for host in ("8.8.8.8", "0.0.0.0", "224.0.0.1", "example.com"):
        with pytest.raises(ValueError):
            validate_host(host)
    assert validate_host("10.0.0.42") == "10.0.0.42"


def test_unpaired_request_only_during_live_qr_window(tmp_path):
    registry, pairing, key, pub, now, params = setup_pairing(tmp_path)
    class EmptyController:
        def lock(self): return False
        def alarm(self): return False
        def snapshot(self): return None
    app = create_app(GatewayState(
        SignedRequestVerifier(registry),
        SignedAuditLog(tmp_path / "audit.jsonl", Ed25519PrivateKey.generate()),
        EmptyController(), pairing=pairing,
    ))
    client = TestClient(app, base_url="https://localhost")
    payload = phone_payload(params)
    response = client.post("/pair/request", json=payload)
    assert response.status_code == 200
    assert response.json()["status"] == "AWAITING_LOCAL_APPROVAL"
    assert client.get("/api/status").status_code == 401
    assert client.post("/pair/request", json=payload).status_code == 403
    client.close(); pairing.close(); registry.close()

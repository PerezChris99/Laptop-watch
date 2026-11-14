"""Isolated local integration tests for owner-provisioned relay auth and receipt."""
import secrets
import time
import uuid
from pathlib import Path

import pytest
from fastapi.testclient import TestClient
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from cryptography.hazmat.primitives.serialization import Encoding, PublicFormat

from laptopwatch.security import PairingRegistry, SignedRequestVerifier, sign_request, b64url
from laptopwatch_relay.app import create_relay, RelayStore


@pytest.fixture
def relay(tmp_path):
    registry=PairingRegistry(tmp_path/"keys.db")
    storage=RelayStore(tmp_path/"locations.db")
    desktop_key=Ed25519PrivateKey.generate()
    phone_key=Ed25519PrivateKey.generate()
    desktop_id=str(uuid.uuid4())
    phone_id=str(uuid.uuid4())
    for identity,key,role in (
        (desktop_id,desktop_key,"desktop"),
        (phone_id,phone_key,"owner_phone"),
    ):
        storage.enroll(registry,identity,
            key.public_key().public_bytes(Encoding.Raw,PublicFormat.Raw),
            role)
    client=TestClient(create_relay(registry,storage),base_url="http://localhost")
    yield client, (desktop_id,desktop_key), (phone_id,phone_key), storage, registry
    client.close()
    storage.close()
    registry.close()


def signed(client, device, method, path, payload=b""):
    identity,key=device
    headers=sign_request(key,method=method,path=path,body=payload,
        timestamp=int(time.time()),nonce=b64url(secrets.token_bytes(16)),
        device_id=identity)
    return client.request(method,path,content=payload,headers=headers)


def fix(event_id=1,lat=0.3476,when=None):
    import json
    return json.dumps({
        "event_id":event_id, "latitude":float(lat), "longitude":32.5825,
        "accuracy_meters":15.5, "observed_at_ms":int(time.time()*1000) if when is None else when,
        "provider":"gpsd-gnss"
    },separators=(",",":"),sort_keys=True).encode()


def test_only_owner_enrolled_desktop_can_report(relay):
    client,desktop,phone,storage,registry=relay
    payload=fix()
    assert client.post("/api/location/report",content=payload).status_code==401
    assert signed(client,phone,"POST","/api/location/report",payload).status_code==403
    assert signed(client,desktop,"POST","/api/location/report",payload).status_code==200


def test_authenticated_receipt_and_private_last_seen(relay):
    client,desktop,phone,storage,registry=relay
    from hashlib import sha256
    payload=fix(event_id=2)
    receipt=signed(client,desktop,"POST","/api/location/report",payload)
    assert receipt.status_code==200
    assert receipt.json()["event_id"]==2
    assert receipt.json()["sha256"]==sha256(payload).hexdigest()
    assert signed(client,desktop,"GET","/api/location/latest").status_code==403
    last=signed(client,phone,"GET","/api/location/latest")
    assert last.status_code==200
    data=last.json()
    assert data["latitude"]==0.3476
    assert data["accuracy_meters"]==15.5
    assert data["is_live_fix"] is False
    assert data["received_at_ms"]>=data["observed_at_ms"]-1000


def test_offline_redelivery_is_idempotent_but_changed_event_fails(relay):
    client,desktop,phone,store,registry=relay
    payload=fix(event_id=3)
    first=signed(client,desktop,"POST","/api/location/report",payload)
    assert first.status_code==200
    second=signed(client,desktop,"POST","/api/location/report",payload)
    assert second.status_code==200
    assert first.json()["sha256"]==second.json()["sha256"]
    conflicting=fix(event_id=3,lat=0.2)
    assert signed(client,desktop,"POST","/api/location/report",conflicting).status_code==422


def test_replay_request_rejected_and_key_revocation(relay):
    client,desktop,phone,store,registry=relay
    identity,key=desktop
    payload=fix(5)
    headers=sign_request(key,method="POST",path="/api/location/report",
        body=payload,timestamp=int(time.time()),nonce=b64url(secrets.token_bytes(16)),
        device_id=identity)
    assert client.post("/api/location/report",data=payload,headers=headers).status_code==200
    assert client.post("/api/location/report",data=payload,headers=headers).status_code==401
    registry.revoke(identity)
    assert signed(client,desktop,"POST","/api/location/report",fix(6)).status_code==401


def test_owner_delete_only(relay):
    client,desktop,phone,store,registry=relay
    assert signed(client,desktop,"POST","/api/location/report",fix()).status_code==200
    assert signed(client,desktop,"POST","/api/location/clear").status_code==403
    assert signed(client,phone,"POST","/api/location/clear").status_code==200
    assert signed(client,phone,"GET","/api/location/latest").status_code==404


def test_fabricated_or_future_data_rejected(relay):
    client,desktop,phone,store,registry=relay
    assert signed(client,desktop,"POST","/api/location/report",
        fix(lat=92)).status_code==422
    assert signed(client,desktop,"POST","/api/location/report",
        fix(7,when=int(time.time()*1000)+3600_000)).status_code==422

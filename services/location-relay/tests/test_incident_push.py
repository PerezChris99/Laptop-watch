"""Relay role, replay, encrypted token and verified incident delivery contracts."""
from __future__ import annotations

from hashlib import sha256
import json
import sqlite3
import secrets
import time
import uuid

import pytest
from fastapi.testclient import TestClient
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from cryptography.hazmat.primitives.serialization import Encoding, PublicFormat

from laptopwatch.security import PairingRegistry, b64url, sign_request
from laptopwatch_relay.app import RelayStore, create_relay
from laptopwatch_relay.push import IncidentRelayStore


@pytest.fixture
def client_stack(tmp_path):
    registry=PairingRegistry(tmp_path/"devices.sqlite")
    storage=RelayStore(tmp_path/"location.sqlite")
    owner=str(uuid.uuid4()); phone=str(uuid.uuid4())
    owner_key=Ed25519PrivateKey.generate()
    phone_key=Ed25519PrivateKey.generate()
    for id_,key,role in ((owner,owner_key,"desktop"),(phone,phone_key,"owner_phone")):
        storage.enroll(registry,id_,key.public_key().public_bytes(
            Encoding.Raw,PublicFormat.Raw),role)
    app=create_relay(registry,storage)
    with TestClient(app) as client:
        yield client,storage,registry,(owner,owner_key),(phone,phone_key)
    storage.close()
    registry.close()


def sign(client, device, method, path, data=None):
    id_,key=device
    body=b"" if data is None else json.dumps(
        data,separators=(",",":"),sort_keys=True).encode("utf8")
    h=sign_request(
        key,method=method,path=path,body=body,device_id=id_,
        nonce=b64url(secrets.token_bytes(16)),timestamp=int(time.time()))
    return client.request(method,path,content=body,headers=h),body


def event(event_id=1):
    return {
        "event_id":event_id,
        "observed_at_ms":int(time.time()*1000),
        "confidence":0.91,
        "evidence_id":uuid.uuid4().hex,
    }


def test_only_desktop_may_report_and_phone_may_register(client_stack):
    client,storage,registry,desktop,phone=client_stack
    assert sign(client,phone,"POST","/api/incidents/report",event())[0].status_code==403
    assert sign(client,desktop,"POST","/api/push/register",{"token":"A"*90})[0].status_code==403
    reply,body=sign(client,desktop,"POST","/api/incidents/report",event(2))
    assert reply.status_code==200
    assert reply.json()["event_id"]==2
    assert reply.json()["sha256"]==sha256(body).hexdigest()


def test_encrypted_push_registration_retry_and_authorized_read(client_stack):
    client,storage,registry,desktop,phone=client_stack
    token="A"*130+":"+uuid.uuid4().hex
    registered,_=sign(client,phone,"POST","/api/push/register",{"token":token})
    assert registered.status_code==200
    assert registered.json()["registered"] is True
    assert registered.json()["configured"] is False
    incident,_=sign(client,desktop,"POST","/api/incidents/report",event(7))
    assert incident.status_code==200
    pending=IncidentRelayStore(storage).pending_push()
    assert len(pending)==1
    assert pending[0]["token"]==token
    rows=storage.db.execute("SELECT token_enc FROM push_targets").fetchall()
    assert len(rows)==1
    assert token.encode() not in rows[0][0]
    denied,_=sign(client,desktop,"POST","/api/incidents/query",
                  {"after_id":0,"limit":50})
    assert denied.status_code==403
    ok,_=sign(client,phone,"POST","/api/incidents/query",
              {"after_id":0,"limit":50})
    assert ok.status_code==200
    item=ok.json()["events"][0]
    assert item["kind"]=="person_verified"
    assert item["id"]==7
    assert item["confidence"]==0.91
    assert item["desktop_id"]==desktop[0]
    store=IncidentRelayStore(storage)
    store.mark_push(desktop[0],7,phone[0],False)
    assert len(store.pending_push())==0
    store.mark_push(desktop[0],7,phone[0],True)
    assert len(store.pending_push())==0
    removed,_=sign(client,phone,"POST","/api/push/unregister")
    assert removed.status_code==200
    assert storage.db.execute("SELECT COUNT(*) FROM push_targets").fetchone()[0]==0


def test_idempotent_receipts_and_forged_event_denied(client_stack):
    client,storage,registry,desktop,phone=client_stack
    data=event(8)
    first,_=sign(client,desktop,"POST","/api/incidents/report",data)
    again,_=sign(client,desktop,"POST","/api/incidents/report",data)
    assert first.status_code==200
    assert again.status_code==200
    altered=dict(data,confidence=0.77)
    denied,_=sign(client,desktop,"POST","/api/incidents/report",altered)
    assert denied.status_code==422
    forged=dict(data,observed_at_ms=int(time.time()*1000)+3_600_000)
    assert sign(client,desktop,"POST","/api/incidents/report",forged)[0].status_code==422
    invalid=dict(data,confidence=-1.0)
    assert sign(client,desktop,"POST","/api/incidents/report",invalid)[0].status_code==422
    assert sign(client,phone,"POST","/api/incidents/clear")[0].status_code==200
    assert sign(client,phone,"POST","/api/incidents/query",
                {"after_id":0,"limit":10})[0].json()["events"]==[]


def test_invalid_push_token_is_rejected_without_storage(client_stack):
    client,storage,registry,desktop,phone=client_stack
    for token in ("A","\n"*60,"\u0000"*90):
        result,_=sign(client,phone,"POST","/api/push/register",{"token":token})
        assert result.status_code==400
    assert storage.db.execute("SELECT COUNT(*) FROM push_targets").fetchone()[0]==0

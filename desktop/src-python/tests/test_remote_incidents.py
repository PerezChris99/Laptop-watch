"""Offline idempotent signed incident receipt and durable cursor tests."""
import json
import secrets
import time
import uuid
from hashlib import sha256

from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

from laptopwatch.remote_incidents import IncidentDeliveryCursor, SignedIncidentSender
from laptopwatch.security import canonical_request, unb64url


def test_cursor_survives_restart_and_never_decreases(tmp_path):
    file=tmp_path/"incident-delivery.sqlite"
    first=IncidentDeliveryCursor(file)
    assert first.last()==0
    first.ack(8)
    first.ack(2)
    assert first.last()==8
    first.close()
    reopened=IncidentDeliveryCursor(file)
    assert reopened.last()==8
    reopened.close()


def test_signed_incident_sender_validates_receipt_and_has_no_media():
    key=Ed25519PrivateKey.generate()
    identity=str(uuid.uuid4())
    sender=SignedIncidentSender("https://relay.example.org",identity,key)
    event={"id":9,"kind":"person_verified",
        "observed_at_ms":int(time.time()*1000),
        "confidence":.91,"evidence_id":uuid.uuid4().hex,
        "video_id":"private-video-not-for-cloud","state":"unread"}
    class Response:
        status=200
        def __init__(self,payload):self.payload=payload
        def read(self,n):return self.payload
        def __enter__(self):return self
        def __exit__(self,*a):return False
    class Opener:
        def __init__(self,valid):self.valid=valid;self.calls=0
        def open(self,request,timeout):
            self.calls+=1
            assert request.get_full_url()=="https://relay.example.org/api/incidents/report"
            body=json.loads(request.data)
            assert "video_id" not in body and "state" not in body
            assert body["event_id"]==9 and body["confidence"]==.91
            headers=dict((k.lower(),v) for k,v in request.header_items())
            msg=canonical_request(
                "POST","/api/incidents/report",request.data,
                int(headers["x-laptopwatch-timestamp"]),
                headers["x-laptopwatch-nonce"],identity)
            key.public_key().verify(
                unb64url(headers["x-laptopwatch-signature"],64),msg)
            receipt={"accepted":True,"event_id":9,
                "sha256":sha256(request.data).hexdigest() if self.valid else "0"*64}
            return Response(json.dumps(receipt).encode())
    sender.opener=Opener(True)
    assert sender.send(event) is True
    assert sender.opener.calls==1
    sender.opener=Opener(False)
    assert sender.send(event) is False
    assert sender.send({"id":10,"kind":"unknown"}) is False

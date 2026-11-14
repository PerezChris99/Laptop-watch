import json
import time
import uuid
from hashlib import sha256

import pytest
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

from laptopwatch.remote_location import SignedLocationSender, validated_relay_url
from laptopwatch.tracking import LocationFix
from laptopwatch.security import canonical_request, unb64url


def test_relay_only_trusted_https_urls():
    assert validated_relay_url("https://relay.example.com/")=="https://relay.example.com"
    for raw in (
        "http://relay.example.com", "https://user:pass@relay.example.com",
        "https://relay.example.com/redirect", "https://relay.example.com?q=x",
        "https://relay.example.invalid", "file:///etc/passwd", "https://relay.example.com:8080",
    ):
        with pytest.raises(ValueError):
            validated_relay_url(raw)


def test_sender_checks_signed_delivery_receipt_and_never_sends_pin():
    key=Ed25519PrivateKey.generate()
    id_=str(uuid.uuid4())
    sender=SignedLocationSender("https://relay.example.com",id_,key)

    class GoodResponse:
        def __init__(self,data):
            self.data=data
            self.status=200
        def read(self,n):return self.data
        def __enter__(self):return self
        def __exit__(self,*args):return False

    class FakeOpener:
        def __init__(self,valid=True):self.calls=0;self.valid=valid
        def open(self,request,timeout):
            self.calls+=1
            assert request.get_full_url()=="https://relay.example.com/api/location/report"
            assert b"7890" not in request.data
            payload=json.loads(request.data)
            assert payload["accuracy_meters"]==18.0
            assert payload["provider"]=="gpsd-gnss"
            # Verify actual Ed25519 signature with canonical Python transcript.
            headers={k.lower():v for k,v in request.header_items()}
            nonce=headers["x-laptopwatch-nonce"]
            stamp=int(headers["x-laptopwatch-timestamp"])
            transcript=canonical_request("POST","/api/location/report",request.data,stamp,nonce,id_)
            key.public_key().verify(unb64url(headers["x-laptopwatch-signature"],64),transcript)
            receipt={
                "accepted":True,
                "event_id":payload["event_id"],
                "sha256":sha256(request.data).hexdigest() if self.valid else "0"*64,
            }
            return GoodResponse(json.dumps(receipt).encode())

    sender.opener=FakeOpener(valid=True)
    fix=LocationFix(0.3476,32.5825,18.0,int(time.time()*1000))
    assert sender(12,fix) is True
    assert sender.opener.calls==1
    sender.opener=FakeOpener(valid=False)
    assert sender(12,fix) is False

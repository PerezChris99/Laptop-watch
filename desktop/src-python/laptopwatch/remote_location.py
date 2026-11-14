"""Optional signed, CA-validated remote last-seen delivery over HTTPS.

The laptop runs this only with the device owner's explicit consent and relay
configuration. Never transmit video, audio, credentials or demo coordinates.
"""
from __future__ import annotations

from hashlib import sha256
import json
from pathlib import Path
import threading
import time
from urllib.error import HTTPError, URLError
from urllib.parse import urlparse
from urllib.request import (
    HTTPSHandler, HTTPRedirectHandler, Request, build_opener
)

from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

from .security import b64url, sign_request
from .tracking import LocationFix, TrackingService


class RedirectDenied(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def validated_relay_url(value: str) -> str:
    url=urlparse(value)
    if (url.scheme!="https" or not url.hostname or url.username or url.password
        or url.query or url.fragment or url.path not in ("","/")
        or url.port not in (None, 443) or len(value)>256):
        raise ValueError("Relay must be an HTTPS domain at port 443 with no path or credentials")
    if url.hostname.endswith(".invalid"):
        raise ValueError("No placeholder relay domains")
    return "https://" + url.hostname.lower()


class SignedLocationSender:
    """Sends signed fixes; only a matching authenticated receipt is considered ack."""
    def __init__(self, url: str, desktop_id: str, private_key: Ed25519PrivateKey,
                 *, timeout_s: float = 5.0):
        import uuid
        uuid.UUID(desktop_id)
        self.base = validated_relay_url(url)
        self.desktop_id = desktop_id
        self.private_key = private_key
        self.timeout_s = min(max(timeout_s, 1.0), 15.0)
        # urllib's default HTTPS handler validates hostname and system trust.
        # A cross-domain redirect is forbidden; it could leak a signed fix.
        self.opener = build_opener(HTTPSHandler(), RedirectDenied())

    def __call__(self, event_id: int, fix: LocationFix) -> bool:
        body=json.dumps({
            "event_id": event_id,
            "latitude": fix.latitude,
            "longitude": fix.longitude,
            "accuracy_meters": float(fix.accuracy_m),
            "observed_at_ms": fix.observed_at_ms,
            "provider": fix.provider,
        }, separators=(",", ":"), sort_keys=True, allow_nan=False).encode("utf8")
        path="/api/location/report"
        nonce=b64url(__import__("secrets").token_bytes(16))
        stamp=int(time.time())
        headers=sign_request(
            self.private_key,method="POST",path=path,body=body,
            timestamp=stamp,nonce=nonce,device_id=self.desktop_id
        )
        headers["Content-Type"]="application/json"
        request=Request(self.base + path, data=body, headers=headers, method="POST")
        try:
            with self.opener.open(request, timeout=self.timeout_s) as response:
                if response.status != 200:
                    return False
                content=response.read(8192)
                if len(content)>4096:
                    return False
                receipt=json.loads(content)
                return (receipt.get("accepted") is True
                        and receipt.get("event_id") == event_id
                        and receipt.get("sha256") == sha256(body).hexdigest())
        except (OSError, HTTPError, URLError, ValueError, TypeError, json.JSONDecodeError):
            return False


class RemoteLocationPoller:
    """Retry queued reports promptly after reachability returns while awake.

    Every successful report is acked only after an authenticated receipt.
    A millisecond-long Internet blip is not guaranteed to complete HTTPS.
    """
    def __init__(self, tracking: TrackingService, sender: SignedLocationSender):
        self.tracking, self.sender = tracking, sender
        self.stop_event=threading.Event()
        self.thread: threading.Thread | None = None

    def start(self) -> None:
        if self.thread is not None and self.thread.is_alive():
            return
        self.stop_event.clear()
        self.thread=threading.Thread(target=self._loop, name="LaptopWatch-LastSeenUpload",daemon=True)
        self.thread.start()

    def _loop(self) -> None:
        failure_delay=2.0
        while not self.stop_event.is_set():
            try:
                pending=self.tracking.store.pending(limit=50)
                delivered=self.tracking.on_secure_transport_available(self.sender)
                if pending and delivered==0:
                    failure_delay=min(failure_delay*2, 60.0)
                    delay=failure_delay
                else:
                    failure_delay=2.0
                    delay=2.0
            except Exception:
                failure_delay=min(failure_delay*2,60.0)
                delay=failure_delay
            self.stop_event.wait(delay)

    def stop(self) -> None:
        self.stop_event.set()
        if self.thread is not None:
            self.thread.join(timeout=16)
            self.thread=None

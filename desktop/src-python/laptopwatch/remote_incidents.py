"""Durable, signed, idempotent remote delivery for verified person incidents.

This is optional. No camera images, MP4, microphones or precise coordinates
ever leave the laptop through the incident relay. A matching server receipt
is required before advancing the persistent cursor.
"""
from __future__ import annotations

from hashlib import sha256
import json
from pathlib import Path
import sqlite3
import threading
import time
import uuid
from urllib.error import HTTPError, URLError
from urllib.request import HTTPSHandler, Request, build_opener

from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

from .incidents import IncidentStore
from .remote_location import RedirectDenied, validated_relay_url
from .security import b64url, sign_request


class IncidentDeliveryCursor:
    def __init__(self, path: str | Path):
        self.path=Path(path)
        self.path.parent.mkdir(mode=0o700,parents=True,exist_ok=True)
        self._db=sqlite3.connect(self.path,isolation_level=None,timeout=5,check_same_thread=False)
        self._db.execute("PRAGMA journal_mode=WAL")
        self._db.execute("CREATE TABLE IF NOT EXISTS state(last_ack INTEGER NOT NULL)")
        if self._db.execute("SELECT COUNT(*) FROM state").fetchone()[0]==0:
            self._db.execute("INSERT INTO state VALUES (0)")
        self._lock=threading.RLock()

    def last(self) -> int:
        with self._lock:
            return int(self._db.execute("SELECT last_ack FROM state").fetchone()[0])

    def ack(self, event_id: int) -> None:
        if event_id<=0:
            raise ValueError("Invalid incident ID")
        with self._lock:
            self._db.execute("UPDATE state SET last_ack=MAX(last_ack,?)",(event_id,))

    def close(self):
        with self._lock:
            self._db.close()


class SignedIncidentSender:
    def __init__(self, base_url: str, desktop_id: str,
                 private_key: Ed25519PrivateKey, *, timeout_seconds:float=5.0):
        uuid.UUID(desktop_id)
        self.url=validated_relay_url(base_url)+"/api/incidents/report"
        self.desktop_id=desktop_id
        self.key=private_key
        self.timeout=min(max(timeout_seconds,1),15)
        self.opener=build_opener(HTTPSHandler(),RedirectDenied())

    def send(self, event: dict) -> bool:
        if (event.get("kind")!="person_verified" or
            not isinstance(event.get("id"),int) or event["id"]<=0):
            return False
        data={
            "event_id":event["id"],
            "observed_at_ms":event["observed_at_ms"],
            "confidence":float(event["confidence"]),
            "evidence_id":event.get("evidence_id"),
        }
        body=json.dumps(data,sort_keys=True,separators=(",",":"),
                        allow_nan=False).encode("utf8")
        path="/api/incidents/report"
        stamp=int(time.time())
        nonce=b64url(__import__("secrets").token_bytes(16))
        headers=sign_request(
            self.key,method="POST",path=path,body=body,
            timestamp=stamp,nonce=nonce,device_id=self.desktop_id)
        headers["Content-Type"]="application/json"
        try:
            request=Request(self.url,data=body,headers=headers,method="POST")
            with self.opener.open(request,timeout=self.timeout) as response:
                if response.status!=200:
                    return False
                raw=response.read(4096)
                receipt=json.loads(raw)
                return (receipt.get("accepted") is True and
                        receipt.get("event_id")==event["id"] and
                        receipt.get("sha256")==sha256(body).hexdigest())
        except (OSError,URLError,HTTPError,ValueError,TypeError,json.JSONDecodeError):
            return False


class RemoteIncidentPoller:
    """Retry while awake, never invent success when mobile connection is absent."""
    def __init__(self, store: IncidentStore, cursor: IncidentDeliveryCursor,
                 sender:SignedIncidentSender):
        self.store,self.cursor,self.sender=store,cursor,sender
        self._stop=threading.Event()
        self._thread:threading.Thread | None=None

    def start(self):
        if self._thread is not None and self._thread.is_alive():
            return
        self._stop.clear()
        self._thread=threading.Thread(
            target=self._run,name="LaptopWatch-IncidentDelivery",daemon=True)
        self._thread.start()

    def _run(self):
        delay=2.0
        while not self._stop.is_set():
            try:
                events=self.store.recent(after_id=self.cursor.last(),limit=100)
                all_ok=True
                for event in events:
                    if self._stop.is_set():
                        break
                    if not self.sender.send(event):
                        all_ok=False
                        break
                    self.cursor.ack(event["id"])
                delay=2.0 if all_ok else min(delay*2,60.0)
            except Exception:
                delay=min(delay*2,60.0)
            self._stop.wait(delay)

    def stop(self):
        self._stop.set()
        if self._thread is not None:
            self._thread.join(timeout=16)
            self._thread=None

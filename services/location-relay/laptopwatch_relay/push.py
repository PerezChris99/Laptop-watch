"""Optional one-owner Firebase Cloud Messaging delivery for verified laptop incidents.

Only enrolled phone keys may register delivery tokens. Relay reports are signed
by the enrolled desktop. This stores ONLY event metadata and opaque FCM tokens,
encrypted with the relay's existing AES-GCM key. No frames, biometric data,
audio, precise location or private signing keys are sent to Firebase.
"""
from __future__ import annotations

from hashlib import sha256
import json
import os
import re
import time
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.request import Request, build_opener, HTTPSHandler, HTTPRedirectHandler

from pydantic import BaseModel, ConfigDict, Field


class IncidentReport(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    event_id: int = Field(ge=1)
    observed_at_ms: int = Field(gt=0)
    confidence: float = Field(ge=0.5, le=1.0)
    evidence_id: str | None = None

    def validate_incident(self):
        import math
        if not math.isfinite(self.confidence) or self.observed_at_ms > int(time.time()*1000)+30_000:
            raise ValueError("Invalid incident confidence or timestamp")
        if self.evidence_id is not None and not re.fullmatch("[0-9a-f]{32}",self.evidence_id):
            raise ValueError("Invalid local evidence reference")


class PushToken(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    token: str

    def validate_token(self):
        if not 20 <= len(self.token) <= 4096 or any(ord(c)<33 or ord(c)>126 for c in self.token):
            raise ValueError("Invalid push registration token")


class IncidentCursor(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    after_id: int = Field(ge=0)
    limit: int = Field(ge=1, le=100)


class IncidentRelayStore:
    """Idempotent reporter receipts and bounded push queue under one-owner DB."""
    def __init__(self, storage: Any):
        self.storage=storage
        with storage.lock:
            storage.db.execute(
                "CREATE TABLE IF NOT EXISTS push_targets("
                "phone_id TEXT PRIMARY KEY, token_enc BLOB NOT NULL, updated_at_ms INTEGER NOT NULL)")
            storage.db.execute(
                "CREATE TABLE IF NOT EXISTS incident_reports("
                "desktop_id TEXT NOT NULL, event_id INTEGER NOT NULL,"
                "event_enc BLOB NOT NULL, observed_at_ms INTEGER NOT NULL,"
                "received_at_ms INTEGER NOT NULL, digest TEXT NOT NULL,"
                "PRIMARY KEY(desktop_id,event_id))")
            storage.db.execute(
                "CREATE INDEX IF NOT EXISTS idx_incident_reports "
                "ON incident_reports(observed_at_ms DESC)")
            storage.db.execute(
                "CREATE TABLE IF NOT EXISTS push_delivery("
                "desktop_id TEXT NOT NULL, event_id INTEGER NOT NULL,"
                "phone_id TEXT NOT NULL, attempts INTEGER NOT NULL DEFAULT 0,"
                "next_attempt_ms INTEGER NOT NULL DEFAULT 0,"
                "delivered_at_ms INTEGER,"
                "PRIMARY KEY(desktop_id,event_id,phone_id))")
            storage.db.execute(
                "CREATE INDEX IF NOT EXISTS idx_push_pending "
                "ON push_delivery(delivered_at_ms,next_attempt_ms)")

    def _seal(self, aad: str, doc: dict) -> bytes:
        nonce=os.urandom(12)
        payload=json.dumps(doc,sort_keys=True,separators=(",",":"),
                           allow_nan=False).encode("utf8")
        return nonce+self.storage.cipher.encrypt(nonce,payload,aad.encode("ascii"))

    def _open(self, aad: str, encrypted: bytes) -> dict:
        if len(encrypted)<29:
            raise RuntimeError("Encrypted relay incident record corrupt")
        return json.loads(self.storage.cipher.decrypt(
            encrypted[:12],encrypted[12:],aad.encode("ascii")))

    def register_phone(self, phone_id: str, token: str):
        PushToken(token=token).validate_token()
        now=int(time.time()*1000)
        secret=self._seal(f"push:{phone_id}",{"token":token})
        with self.storage.lock:
            self.storage.db.execute(
                "INSERT INTO push_targets VALUES (?,?,?) ON CONFLICT(phone_id) "
                "DO UPDATE SET token_enc=excluded.token_enc,updated_at_ms=excluded.updated_at_ms",
                (phone_id,secret,now))
            # Requeue only as-yet-unexpired incidents when the owner registers.
            self.storage.db.execute(
                "INSERT OR IGNORE INTO push_delivery(desktop_id,event_id,phone_id) "
                "SELECT desktop_id,event_id,? FROM incident_reports "
                "WHERE observed_at_ms>=?",(phone_id,now-86_400_000))

    def unregister_phone(self, phone_id: str):
        with self.storage.lock:
            self.storage.db.execute("DELETE FROM push_targets WHERE phone_id=?",(phone_id,))
            self.storage.db.execute("DELETE FROM push_delivery WHERE phone_id=?",(phone_id,))

    def is_registered(self, phone_id: str)->bool:
        with self.storage.lock:
            return self.storage.db.execute(
                "SELECT 1 FROM push_targets WHERE phone_id=?",(phone_id,)).fetchone() is not None

    def report(self, desktop_id: str, event: IncidentReport, digest: str) -> bool:
        event.validate_incident()
        now=int(time.time()*1000)
        with self.storage.lock:
            previous=self.storage.db.execute(
                "SELECT digest FROM incident_reports WHERE desktop_id=? AND event_id=?",
                (desktop_id,event.event_id)).fetchone()
            if previous is not None:
                if previous[0]!=digest:
                    raise ValueError("Conflicting signed incident event ID")
                return False
            details=event.model_dump()
            enc=self._seal(f"event:{desktop_id}:{event.event_id}",details)
            self.storage.db.execute("INSERT INTO incident_reports VALUES (?,?,?,?,?,?)",
                (desktop_id,event.event_id,enc,event.observed_at_ms,now,digest))
            phones=self.storage.db.execute("SELECT phone_id FROM push_targets").fetchall()
            for (phone_id,) in phones:
                self.storage.db.execute(
                    "INSERT OR IGNORE INTO push_delivery(desktop_id,event_id,phone_id) VALUES (?,?,?)",
                    (desktop_id,event.event_id,phone_id))
            # Minimize data residency; a user can still export earlier records locally.
            retention=now-30*86_400_000
            self.storage.db.execute(
                "DELETE FROM push_delivery WHERE (desktop_id,event_id) IN "
                "(SELECT desktop_id,event_id FROM incident_reports WHERE received_at_ms < ?)",
                (retention,))
            self.storage.db.execute(
                "DELETE FROM incident_reports WHERE received_at_ms < ?",(retention,))
            return True

    def query(self, after_id: int, limit: int) -> list[dict]:
        if after_id<0 or not 1<=limit<=100:
            raise ValueError("Invalid cursor")
        with self.storage.lock:
            rows=self.storage.db.execute(
                "SELECT desktop_id,event_id,event_enc,received_at_ms "
                "FROM incident_reports WHERE event_id>? ORDER BY event_id ASC LIMIT ?",
                (after_id,limit)).fetchall()
        result=[]
        for desktop,event_id,encrypted,received in rows:
            event=self._open(f"event:{desktop}:{event_id}",encrypted)
            result.append({"desktop_id":desktop,**event,"kind":"person_verified",
                           "id":event_id,"received_at_ms":received,
                           "is_verified_person":True})
        return result

    def pending_push(self, limit:int=10):
        now=int(time.time()*1000)
        with self.storage.lock:
            rows=self.storage.db.execute(
                "SELECT d.desktop_id,d.event_id,d.phone_id,t.token_enc,r.event_enc "
                "FROM push_delivery AS d "
                "JOIN push_targets AS t ON d.phone_id=t.phone_id "
                "JOIN incident_reports AS r ON r.desktop_id=d.desktop_id AND r.event_id=d.event_id "
                "WHERE d.delivered_at_ms IS NULL "
                "AND d.next_attempt_ms <= ? ORDER BY r.observed_at_ms ASC LIMIT ?",
                (now,limit)).fetchall()
        return [{"desktop_id":desktop,"event_id":event_id,"phone_id":phone,
                 "token":self._open(f"push:{phone}",token)["token"],
                 "report":self._open(f"event:{desktop}:{event_id}",payload)}
                for desktop,event_id,phone,token,payload in rows]

    def mark_push(self, desktop_id: str, event_id: int, phone_id: str, success: bool):
        now=int(time.time()*1000)
        with self.storage.lock:
            row=self.storage.db.execute(
                "SELECT attempts FROM push_delivery WHERE desktop_id=? AND event_id=? AND phone_id=?",
                (desktop_id,event_id,phone_id)).fetchone()
            if row is None:
                return
            attempts=int(row[0])
            delay=min(3_600_000, 15_000 * (2 ** min(attempts, 8)))
            self.storage.db.execute(
                "UPDATE push_delivery SET attempts=attempts+1,"
                "delivered_at_ms=CASE WHEN ? THEN ? ELSE delivered_at_ms END,"
                "next_attempt_ms=? WHERE desktop_id=? AND event_id=? AND phone_id=?",
                (int(success),now,now if success else now+delay,
                 desktop_id,event_id,phone_id))

    def clear_events(self):
        with self.storage.lock:
            self.storage.db.execute("DELETE FROM push_delivery")
            self.storage.db.execute("DELETE FROM incident_reports")


class BlockRedirect(HTTPRedirectHandler):
    def redirect_request(self,req,fp,code,msg,headers,newurl):
        return None


class FirebaseProvider:
    """Use ADC (managed identity preferred); NEVER bundle service-account secrets."""
    def __init__(self, project_id: str):
        if not re.fullmatch("[a-zA-Z0-9][a-zA-Z0-9-]{4,100}",project_id):
            raise ValueError("Invalid Firebase project identifier")
        self.project_id=project_id
        self.opener=build_opener(HTTPSHandler(),BlockRedirect())

    def send(self, token: str, desktop_id: str, event_id: int) -> bool:
        # Lazy import: relay remains operational without Firebase credentials.
        import google.auth
        from google.auth.transport.requests import Request as GoogleRequest
        credentials,_=google.auth.default(
            scopes=["https://www.googleapis.com/auth/firebase.messaging"])
        if not credentials.valid:
            credentials.refresh(GoogleRequest())
        body=json.dumps({"message":{
            "token":token,
            "data":{
                "kind":"verified_person_hint",
                "desktop_id":desktop_id,
                "event_id":str(event_id),
            },
            "android":{"priority":"HIGH","ttl":"900s"},
        }},separators=(",",":")).encode("utf8")
        request=Request(
            f"https://fcm.googleapis.com/v1/projects/{self.project_id}/messages:send",
            data=body,method="POST",
            headers={"Authorization":"Bearer "+credentials.token,
                     "Content-Type":"application/json"})
        try:
            with self.opener.open(request,timeout=10) as response:
                return response.status==200
        except (HTTPError,URLError,OSError):
            return False

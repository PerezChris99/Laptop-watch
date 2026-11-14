"""Single-owner signed remote last-seen relay (never stores video or audio).

Public deployment REQUIRES a real TLS-terminating reverse proxy, owner-only
enrollment of desktop and phone Ed25519 keys, and encrypted host storage.
"""
from __future__ import annotations

from dataclasses import dataclass
import asyncio
from hashlib import sha256
import json
import math
import os
from pathlib import Path
import sqlite3
import threading
import time
import uuid

from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import JSONResponse
from pydantic import BaseModel, ConfigDict, Field, ValidationError

from .push import IncidentReport, IncidentCursor, PushToken, IncidentRelayStore, FirebaseProvider

from laptopwatch.security import (
    AuthenticationError, PairingRegistry, SignedRequestVerifier,
    SlidingWindowLimiter
)

MAX_BODY = 4096


class VerifiedFix(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    event_id: int = Field(ge=1)
    latitude: float = Field(ge=-90, le=90)
    longitude: float = Field(ge=-180, le=180)
    accuracy_meters: float = Field(gt=0, le=100_000)
    observed_at_ms: int = Field(gt=0)
    provider: str

    def validate_location(self) -> None:
        if not all(math.isfinite(v) for v in
                   (self.latitude, self.longitude, self.accuracy_meters)):
            raise ValueError("invalid location")
        if self.provider not in {"gpsd-gnss", "os-consented-location"}:
            raise ValueError("unverified location provider")
        now = int(time.time() * 1000)
        if self.observed_at_ms > now + 30_000:
            raise ValueError("future location fix is invalid")
        # An old fix is historical, not a live track. Do not discard it
        # just because the laptop was offline for days.


class RelayStore:
    """Store private GPS coordinates encrypted under a per-deployment key.

    Encryption limits accidental SQLite/WAL/backups disclosure, but does NOT
    protect against a compromised running server or stolen keyfile. Full-disk
    encryption, role isolation and owner-controlled key backup remain required.
    """
    def __init__(self, db: str | Path):
        from cryptography.hazmat.primitives.ciphers.aead import AESGCM
        self.path=str(db)
        db_path=Path(self.path)
        db_path.parent.mkdir(parents=True,exist_ok=True,mode=0o700)
        if os.name=="posix":
            db_path.parent.chmod(0o700)
        keyfile=db_path.with_suffix(".location-key")
        try:
            handle=os.open(keyfile,os.O_CREAT | os.O_EXCL | os.O_WRONLY,0o600)
        except FileExistsError:
            value=keyfile.read_bytes()
        else:
            with os.fdopen(handle,"wb") as f:
                value=os.urandom(32)
                f.write(value)
                f.flush()
                os.fsync(f.fileno())
        if len(value)!=32:
            raise RuntimeError("Location key unavailable/corrupt; do not discard old encrypted records")
        if os.name=="posix" and (keyfile.stat().st_mode & 0o077):
            raise RuntimeError("Location encryption key must be user-private (0600)")
        self.cipher=AESGCM(value)
        self.lock=threading.RLock()
        self.db=sqlite3.connect(self.path,isolation_level=None,timeout=5,check_same_thread=False)
        self.db.execute("PRAGMA busy_timeout=5000")
        self.db.execute("PRAGMA journal_mode=WAL")
        self.db.execute("CREATE TABLE IF NOT EXISTS device_roles("
                        "id TEXT PRIMARY KEY, role TEXT NOT NULL CHECK(role IN ('owner_phone','desktop')))")
        old_columns={row[1] for row in self.db.execute("PRAGMA table_info(location_reports)")}
        if "latitude" in old_columns:
            self.db.close()
            raise RuntimeError("Old plaintext location table found: perform an explicit encrypted migration first")
        self.db.execute("CREATE TABLE IF NOT EXISTS location_reports("
                        "desktop_id TEXT NOT NULL, event_id INTEGER NOT NULL,"
                        "encrypted BLOB NOT NULL, observed_at_ms INTEGER NOT NULL,"
                        "received_at_ms INTEGER NOT NULL,payload_digest TEXT NOT NULL,"
                        "PRIMARY KEY(desktop_id,event_id))")
        self.db.execute("CREATE INDEX IF NOT EXISTS idx_report_newest "
                        "ON location_reports(observed_at_ms DESC,received_at_ms DESC)")
        if os.name=="posix":
            os.chmod(self.path,0o600)

    def enroll(self, registry: PairingRegistry, device_id: str, public_key: bytes, role: str):
        if role not in ("owner_phone", "desktop"):
            raise ValueError("invalid role")
        uuid.UUID(device_id)
        with self.lock:
            registry.pair(device_id,public_key,locally_approved=True)
            self.db.execute("INSERT INTO device_roles VALUES (?,?)",(device_id,role))

    def role(self, device_id: str) -> str | None:
        with self.lock:
            row=self.db.execute("SELECT role FROM device_roles WHERE id=?",(device_id,)).fetchone()
            return row[0] if row else None

    def accept(self, desktop_id: str, fix: VerifiedFix, digest: str) -> bool:
        now=int(time.time()*1000)
        with self.lock:
            previous=self.db.execute(
                "SELECT payload_digest FROM location_reports WHERE desktop_id=? AND event_id=?",
                (desktop_id,fix.event_id)).fetchone()
            if previous:
                if previous[0]!=digest:
                    raise ValueError("event ID cannot be rewritten")
                return False
            data=json.dumps({
                "latitude":fix.latitude,"longitude":fix.longitude,
                "accuracy_meters":fix.accuracy_meters,"provider":fix.provider,
                "observed_at_ms":fix.observed_at_ms,
            },sort_keys=True,separators=(",",":"),allow_nan=False).encode("utf8")
            nonce=os.urandom(12)
            aad=f"{desktop_id}:{fix.event_id}".encode("ascii")
            sealed=nonce+self.cipher.encrypt(nonce,data,aad)
            self.db.execute(
                "INSERT INTO location_reports VALUES (?,?,?,?,?,?)",
                (desktop_id,fix.event_id,sealed,fix.observed_at_ms,now,digest))
            return True

    def latest(self) -> dict | None:
        with self.lock:
            row=self.db.execute(
                "SELECT desktop_id,event_id,encrypted,observed_at_ms,received_at_ms "
                "FROM location_reports ORDER BY observed_at_ms DESC,received_at_ms DESC LIMIT 1"
            ).fetchone()
        if not row:
            return None
        desktop,event_id,sealed,observed,received=row
        if len(sealed)<29:
            raise RuntimeError("Encrypted location record corrupted")
        try:
            decoded=self.cipher.decrypt(
                sealed[:12],sealed[12:],f"{desktop}:{event_id}".encode("ascii"))
            details=json.loads(decoded)
        except Exception as exc:
            raise RuntimeError("Encrypted location record failed authentication") from exc
        if details["observed_at_ms"]!=observed:
            raise RuntimeError("Location timestamp integrity check failed")
        return {"desktop_id":desktop,"event_id":event_id,
                **details,"received_at_ms":received}

    def clear(self) -> None:
        with self.lock:
            self.db.execute("DELETE FROM location_reports")

    def close(self) -> None:
        with self.lock:
            self.db.close()


def create_relay(registry: PairingRegistry, storage: RelayStore,
                 push_provider: FirebaseProvider | None = None) -> FastAPI:
    app = FastAPI(title="LaptopWatch Private Location Relay",
                  docs_url=None, openapi_url=None, redoc_url=None)
    verifier = SignedRequestVerifier(registry, SlidingWindowLimiter(max_attempts=90, period=60))
    incidents = IncidentRelayStore(storage)

    async def flush_push():
        while True:
            try:
                if push_provider is not None:
                    for item in await asyncio.to_thread(incidents.pending_push):
                        accepted = await asyncio.to_thread(
                            push_provider.send, item["token"], item["desktop_id"],
                            item["event_id"])
                        await asyncio.to_thread(
                            incidents.mark_push, item["desktop_id"],item["event_id"],
                            item["phone_id"], accepted)
            except asyncio.CancelledError:
                raise
            except Exception:
                # Avoid leaking tokens or Google credentials into logs.
                pass
            await asyncio.sleep(6)

    @app.on_event("startup")
    async def start_push_delivery():
        app.state.push_task = asyncio.create_task(flush_push())

    @app.on_event("shutdown")
    async def stop_push_delivery():
        task = getattr(app.state, "push_task", None)
        if task is not None:
            task.cancel()
            try:
                await task
            except asyncio.CancelledError:
                pass

    @app.middleware("http")
    async def enforce_identity(request: Request, call_next):
        if request.url.path not in {
            "/api/location/report","/api/location/latest","/api/location/clear",
            "/api/incidents/report","/api/incidents/query","/api/incidents/clear",
            "/api/push/register","/api/push/unregister","/api/push/status",
        } or request.url.query:
            return JSONResponse({"error":"Not found"}, status_code=404)
        data = bytearray()
        size = request.headers.get("content-length")
        if size and (not size.isascii() or not size.isdecimal() or int(size)>MAX_BODY):
            return JSONResponse({"error":"Request body too large"},status_code=413)
        async for part in request.stream():
            data.extend(part)
            if len(data)>MAX_BODY:
                return JSONResponse({"error":"Request body too large"},status_code=413)
        request.state.raw_body = bytes(data)
        try:
            request.state.device_id=verifier.verify(
                request.method, request.url.path, request.state.raw_body,
                request.headers,
                client_ip=request.client.host if request.client else "unknown",
            )
        except (AuthenticationError, ValueError, TypeError, OverflowError):
            return JSONResponse({"error":"Unauthorized"},status_code=401)
        role=storage.role(request.state.device_id)
        desktop_path=request.url.path in {"/api/location/report","/api/incidents/report"}
        if desktop_path and role!="desktop":
            return JSONResponse({"error":"Insufficient device authority"},status_code=403)
        if not desktop_path and role!="owner_phone":
            return JSONResponse({"error":"Insufficient device authority"},status_code=403)
        response=await call_next(request)
        response.headers["Cache-Control"]="no-store"
        response.headers["X-Content-Type-Options"]="nosniff"
        return response

    @app.post("/api/location/report")
    async def report(request: Request):
        try:
            fix=VerifiedFix.model_validate_json(request.state.raw_body)
            fix.validate_location()
            digest=sha256(request.state.raw_body).hexdigest()
            storage.accept(request.state.device_id,fix,digest)
        except (ValidationError, ValueError):
            raise HTTPException(status_code=422,detail="Invalid or conflicting location event")
        return {"accepted":True,"event_id":fix.event_id,"sha256":digest}

    @app.get("/api/location/latest")
    async def latest(request: Request):
        location=storage.latest()
        if location is None:
            raise HTTPException(status_code=404, detail="No verified location delivered")
        # Historical even if very recent, because the laptop is not continuously
        # observed by the remote location service.
        return {**location,"is_live_fix":False,"source":"remotely-delivered-last-seen"}

    @app.post("/api/location/clear")
    async def clear(request: Request):
        if request.state.raw_body:
            raise HTTPException(status_code=400,detail="Body not allowed")
        storage.clear()
        return {"deleted":True}

    @app.post("/api/incidents/report")
    async def report_incident(request: Request):
        try:
            event=IncidentReport.model_validate_json(request.state.raw_body)
            event.validate_incident()
            digest=sha256(request.state.raw_body).hexdigest()
            incidents.report(request.state.device_id,event,digest)
        except (ValidationError, ValueError):
            raise HTTPException(status_code=422,detail="Invalid or conflicting incident event")
        return {"accepted":True,"event_id":event.event_id,"sha256":digest}

    @app.post("/api/incidents/query")
    async def query_incidents(request: Request):
        try:
            cursor=IncidentCursor.model_validate_json(request.state.raw_body)
        except (ValidationError,ValueError):
            raise HTTPException(status_code=400,detail="Invalid incident cursor")
        rows=incidents.query(cursor.after_id,cursor.limit)
        return {"events":rows,
                "next_after_id":rows[-1]["event_id"] if rows else cursor.after_id}

    @app.post("/api/incidents/clear")
    async def delete_incidents(request: Request):
        if request.state.raw_body:
            raise HTTPException(status_code=400,detail="No body allowed")
        incidents.clear_events()
        return {"deleted":True}

    @app.post("/api/push/register")
    async def push_register(request: Request):
        try:
            item=PushToken.model_validate_json(request.state.raw_body)
            item.validate_token()
            incidents.register_phone(request.state.device_id,item.token)
        except (ValidationError,ValueError):
            raise HTTPException(status_code=400,detail="Invalid push registration")
        return {"registered":True,"configured":push_provider is not None}

    @app.post("/api/push/unregister")
    async def push_unregister(request: Request):
        if request.state.raw_body:
            raise HTTPException(status_code=400,detail="No body allowed")
        incidents.unregister_phone(request.state.device_id)
        return {"registered":False}

    @app.get("/api/push/status")
    async def push_status(request: Request):
        return {"registered":incidents.is_registered(request.state.device_id),
                "provider_configured":push_provider is not None}

    return app

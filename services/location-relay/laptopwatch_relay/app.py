"""Single-owner signed remote last-seen relay (never stores video or audio).

Public deployment REQUIRES a real TLS-terminating reverse proxy, owner-only
enrollment of desktop and phone Ed25519 keys, and encrypted host storage.
"""
from __future__ import annotations

from dataclasses import dataclass
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
    def __init__(self, db: str | Path):
        self.path = str(db)
        Path(self.path).parent.mkdir(parents=True, exist_ok=True, mode=0o700)
        self.lock = threading.RLock()
        self.db = sqlite3.connect(self.path, isolation_level=None, timeout=5, check_same_thread=False)
        self.db.execute("PRAGMA busy_timeout=5000")
        self.db.execute("PRAGMA journal_mode=WAL")
        self.db.execute("CREATE TABLE IF NOT EXISTS device_roles("
                        "id TEXT PRIMARY KEY, role TEXT NOT NULL CHECK(role IN ('owner_phone','desktop')))")
        self.db.execute("CREATE TABLE IF NOT EXISTS location_reports("
                        "desktop_id TEXT NOT NULL, event_id INTEGER NOT NULL,"
                        "latitude REAL NOT NULL, longitude REAL NOT NULL,"
                        "accuracy_meters REAL NOT NULL, observed_at_ms INTEGER NOT NULL,"
                        "provider TEXT NOT NULL, received_at_ms INTEGER NOT NULL,"
                        "payload_digest TEXT NOT NULL,"
                        "PRIMARY KEY(desktop_id, event_id))")
        self.db.execute("CREATE INDEX IF NOT EXISTS idx_report_newest "
                        "ON location_reports(observed_at_ms DESC,received_at_ms DESC)")
        if os.name == "posix":
            os.chmod(self.path, 0o600)

    def enroll(self, registry: PairingRegistry, device_id: str, public_key: bytes, role: str):
        if role not in ("owner_phone", "desktop"):
            raise ValueError("invalid role")
        uuid.UUID(device_id)
        with self.lock:
            registry.pair(device_id, public_key, locally_approved=True)
            self.db.execute("INSERT INTO device_roles VALUES (?,?)", (device_id, role))

    def role(self, device_id: str) -> str | None:
        with self.lock:
            row=self.db.execute("SELECT role FROM device_roles WHERE id=?", (device_id,)).fetchone()
            return row[0] if row else None

    def accept(self, desktop_id: str, fix: VerifiedFix, digest: str) -> bool:
        now = int(time.time() * 1000)
        with self.lock:
            previous = self.db.execute(
                "SELECT payload_digest FROM location_reports WHERE desktop_id=? AND event_id=?",
                (desktop_id, fix.event_id)
            ).fetchone()
            if previous:
                if previous[0] != digest:
                    raise ValueError("event ID cannot be rewritten")
                return False
            self.db.execute(
                "INSERT INTO location_reports VALUES (?,?,?,?,?,?,?,?,?)",
                (desktop_id, fix.event_id, fix.latitude, fix.longitude,
                 fix.accuracy_meters, fix.observed_at_ms, fix.provider, now, digest),
            )
            return True

    def latest(self) -> dict | None:
        with self.lock:
            row=self.db.execute(
                "SELECT desktop_id,event_id,latitude,longitude,accuracy_meters,"
                "observed_at_ms,provider,received_at_ms FROM location_reports "
                "ORDER BY observed_at_ms DESC,received_at_ms DESC LIMIT 1"
            ).fetchone()
            if not row:
                return None
            return dict(zip(
                ("desktop_id","event_id","latitude","longitude","accuracy_meters",
                 "observed_at_ms","provider","received_at_ms"),row
            ))

    def clear(self) -> None:
        with self.lock:
            self.db.execute("DELETE FROM location_reports")

    def close(self) -> None:
        with self.lock:
            self.db.close()


def create_relay(registry: PairingRegistry, storage: RelayStore) -> FastAPI:
    app = FastAPI(title="LaptopWatch Private Location Relay",
                  docs_url=None, openapi_url=None, redoc_url=None)
    verifier = SignedRequestVerifier(registry, SlidingWindowLimiter(max_attempts=90, period=60))

    @app.middleware("http")
    async def enforce_identity(request: Request, call_next):
        if request.url.path not in {"/api/location/report", "/api/location/latest",
                                     "/api/location/clear"} or request.url.query:
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
        if request.url.path.endswith("/report") and role!="desktop":
            return JSONResponse({"error":"Insufficient device authority"},status_code=403)
        if not request.url.path.endswith("/report") and role!="owner_phone":
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

    return app

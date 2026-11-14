"""Signed control gateway. No unpaired requests, no URL credentials, no CORS.

Deploy only behind TLS with a pinned certificate. LAN binding is explicitly opt-in
at the process launcher; this API has no remote pairing or administrative route.
"""
from __future__ import annotations

from dataclasses import dataclass, field
import asyncio
import json
import re
import threading
from typing import Protocol

from fastapi import FastAPI, Request, HTTPException
from fastapi.responses import JSONResponse, Response, FileResponse
from pydantic import BaseModel, ConfigDict

from .audit import SignedAuditLog
from .security import AuthenticationError, SignedRequestVerifier
from .evidence import CaptureUnavailable, EvidenceRecorder
from .tracking import TrackingService

MAX_CONTROL_BODY = 64 * 1024


class ControlSurface(Protocol):
    def lock(self) -> bool: ...
    def alarm(self) -> bool: ...
    def snapshot(self) -> bytes | None: ...


@dataclass
class GatewayState:
    verifier: SignedRequestVerifier
    audit: SignedAuditLog
    controller: ControlSurface
    evidence: EvidenceRecorder | None = None
    tracking: TrackingService | None = None
    armed: bool = False
    _lock: threading.Lock = field(default_factory=threading.Lock, repr=False)

    def set_armed(self, armed: bool) -> None:
        with self._lock:
            self.armed = armed

    def get_armed(self) -> bool:
        with self._lock:
            return self.armed


class ArmRequest(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    armed: bool


def create_app(state: GatewayState) -> FastAPI:
    app = FastAPI(title="LaptopWatch Paired Gateway", docs_url=None, redoc_url=None, openapi_url=None)
    # Serializes camera side effects against disarm/stop requests so a
    # concurrent start cannot turn the camera back on after the owner disarms.
    media_lock = asyncio.Lock()

    @app.middleware("http")
    async def secure_request(request: Request, call_next):
        path = request.url.path
        if not path.startswith("/api/"):
            return JSONResponse({"error": "Not found"}, status_code=404)
        # No GET query credentials or arguments: all protected APIs use signed headers.
        if request.url.query:
            return JSONResponse({"error": "Query parameters forbidden"}, status_code=400)
        # Trust the transport socket, not client-supplied Forwarded/X-Forwarded headers.
        if request.url.scheme != "https" and (not request.client or request.client.host not in {"127.0.0.1", "::1"}):
            return JSONResponse({"error": "TLS required"}, status_code=403)
        length = request.headers.get("content-length")
        if length is not None:
            try:
                if int(length) > MAX_CONTROL_BODY or int(length) < 0:
                    return JSONResponse({"error": "Body too large"}, status_code=413)
            except ValueError:
                return JSONResponse({"error": "Invalid Content-Length"}, status_code=400)
        data = bytearray()
        async for chunk in request.stream():
            data.extend(chunk)
            if len(data) > MAX_CONTROL_BODY:
                return JSONResponse({"error": "Body too large"}, status_code=413)
        request.state.raw_body = bytes(data)
        peer_ip = request.client.host if request.client else "unknown"
        try:
            request.state.paired_device = state.verifier.verify(
                request.method, path, request.state.raw_body, request.headers,
                client_ip=peer_ip,
            )
        except (AuthenticationError, ValueError, KeyError):
            return JSONResponse({"error": "Unauthorized"}, status_code=401, headers={"Cache-Control": "no-store"})
        response = await call_next(request)
        response.headers["Cache-Control"] = "no-store"
        response.headers["X-Content-Type-Options"] = "nosniff"
        return response

    @app.get("/api/status")
    async def status(request: Request):
        return {"online": True, "motion_armed": state.get_armed(), "location": None,
                "subject_identified": None, "media_transport": "NOT_CONFIGURED"}

    @app.post("/api/config/armed")
    async def arm(request: Request):
        async with media_lock:
            try:
                parsed = ArmRequest.model_validate_json(request.state.raw_body)
            except Exception:
                raise HTTPException(status_code=400, detail="Invalid arm payload")
            if not parsed.armed and state.evidence is not None:
                try:
                    await asyncio.to_thread(state.evidence.stop_if_recording)
                except CaptureUnavailable:
                    raise HTTPException(status_code=503, detail="Camera could not be released")
            state.set_armed(parsed.armed)
            state.audit.append("config", request.state.paired_device, result="success", metadata={"armed": str(parsed.armed)})
            return {"success": True, "armed": parsed.armed}

    @app.post("/api/lock")
    async def lock(request: Request):
        if request.state.raw_body:
            raise HTTPException(status_code=400, detail="No body allowed")
        try:
            ok = await asyncio.to_thread(state.controller.lock)
        except Exception:
            ok = False
        state.audit.append("lock", request.state.paired_device, result="success" if ok else "failure")
        if not ok:
            raise HTTPException(status_code=503, detail="OS lock failed")
        return {"success": True}

    @app.post("/api/alarm")
    async def alarm(request: Request):
        if request.state.raw_body:
            raise HTTPException(status_code=400, detail="No body allowed")
        try:
            ok = await asyncio.to_thread(state.controller.alarm)
        except Exception:
            ok = False
        state.audit.append("alarm", request.state.paired_device, result="success" if ok else "failure")
        if not ok:
            raise HTTPException(status_code=503, detail="OS alarm unavailable")
        return {"success": True}

    @app.get("/api/camera/frame")
    async def camera_snapshot(request: Request):
        async with media_lock:
            if not state.get_armed():
                raise HTTPException(status_code=409, detail="Camera monitoring is disarmed")
            try:
                if state.evidence is not None:
                    image = await asyncio.to_thread(state.evidence.snapshot)
                else:
                    image = await asyncio.to_thread(state.controller.snapshot)
            except Exception:
                image = None
            if not image:
                raise HTTPException(status_code=503, detail="Camera unavailable")
            return Response(image, media_type="image/jpeg")

    @app.post("/api/camera/photo")
    async def take_photo(request: Request):
        async with media_lock:
            if not state.get_armed():
                raise HTTPException(status_code=409, detail="Camera monitoring is disarmed")
            if request.state.raw_body:
                raise HTTPException(status_code=400, detail="No body allowed")
            if state.evidence is None:
                raise HTTPException(status_code=503, detail="Evidence recorder not configured")
            try:
                result = await asyncio.to_thread(state.evidence.take_photo)
            except (CaptureUnavailable, OSError):
                state.audit.append("snapshot", request.state.paired_device, result="failure")
                raise HTTPException(status_code=503, detail="Unable to capture photo")
            state.audit.append("snapshot", request.state.paired_device, result="success",
                               metadata={"evidence_id": str(result["id"])})
            return {"success": True, **result}

    @app.post("/api/camera/record/start")
    async def start_recording(request: Request):
        async with media_lock:
            if not state.get_armed():
                raise HTTPException(status_code=409, detail="Camera monitoring is disarmed")
            if request.state.raw_body:
                raise HTTPException(status_code=400, detail="No body allowed")
            if state.evidence is None:
                raise HTTPException(status_code=503, detail="Evidence recorder not configured")
            try:
                session = await asyncio.to_thread(state.evidence.start_recording)
            except (CaptureUnavailable, OSError):
                state.audit.append("record_start", request.state.paired_device, result="failure")
                raise HTTPException(status_code=503, detail="Video recorder unavailable")
            state.audit.append("record_start", request.state.paired_device, result="success",
                               metadata={"evidence_id": session})
            return {"success": True, "recording": True, "id": session, "max_duration_seconds": state.evidence.max_duration_seconds}

    @app.post("/api/camera/record/stop")
    async def stop_recording(request: Request):
        async with media_lock:
            if request.state.raw_body:
                raise HTTPException(status_code=400, detail="No body allowed")
            if state.evidence is None:
                raise HTTPException(status_code=503, detail="Evidence recorder not configured")
            try:
                clip = await asyncio.to_thread(state.evidence.stop_recording)
            except CaptureUnavailable:
                state.audit.append("record_stop", request.state.paired_device, result="failure")
                raise HTTPException(status_code=409, detail="No completed video recording")
            state.audit.append("record_stop", request.state.paired_device, result="success",
                               metadata={"evidence_id": clip.id})
            return {"success": True, "recording": False, "id": clip.id,
                    "filename": clip.relative_file, "frames": clip.frames, "size_bytes": clip.file_bytes}

    @app.get("/api/evidence/{kind}/{evidence_id}")
    async def download_evidence(kind: str, evidence_id: str, request: Request):
        if state.evidence is None:
            raise HTTPException(status_code=503, detail="Evidence store not configured")
        # The complete signed path is checked before this handler; the
        # allowlist also prevents any traversal or user-chosen filesystem path.
        if kind not in {"photo", "video"} or re.fullmatch(r"[0-9a-f]{32}", evidence_id) is None:
            raise HTTPException(status_code=404, detail="Evidence not found")
        suffix = "jpg" if kind == "photo" else "mp4"
        filename = f"{kind}-{evidence_id}.{suffix}"
        path = state.evidence.directory / filename
        if not path.is_file() or path.is_symlink():
            raise HTTPException(status_code=404, detail="Evidence not found")
        return FileResponse(
            path,
            media_type="image/jpeg" if kind == "photo" else "video/mp4",
            filename=filename,
            content_disposition_type="attachment",
            headers={"Cache-Control": "no-store", "X-Content-Type-Options": "nosniff"},
        )

    @app.get("/api/location/last-seen")
    async def last_seen(request: Request):
        if state.tracking is None:
            raise HTTPException(status_code=503, detail="Location tracking not enabled")
        location = state.tracking.store.latest()
        if location is None:
            raise HTTPException(status_code=404, detail="No verified laptop location recorded")
        return location

    @app.post("/api/location/refresh")
    async def refresh_location(request: Request):
        if request.state.raw_body:
            raise HTTPException(status_code=400, detail="No body allowed")
        if state.tracking is None:
            raise HTTPException(status_code=503, detail="Location tracking not enabled")
        fix = await asyncio.to_thread(state.tracking.refresh)
        if fix is None:
            raise HTTPException(status_code=503, detail="No real GNSS fix available")
        return fix

    return app

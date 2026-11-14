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
from .pairing import PairingCoordinator
from .incidents import IncidentStore, PersonCameraGuard
from .webrtc import RtcController, MediaUnavailable

MAX_CONTROL_BODY = 64 * 1024


class ControlSurface(Protocol):
    def lock(self) -> bool: ...
    def alarm(self) -> bool: ...
    def speak(self, text: str) -> bool: ...
    def snapshot(self) -> bytes | None: ...


@dataclass
class GatewayState:
    verifier: SignedRequestVerifier
    audit: SignedAuditLog
    controller: ControlSurface
    evidence: EvidenceRecorder | None = None
    tracking: TrackingService | None = None
    pairing: PairingCoordinator | None = None
    rtc: RtcController | None = None
    incidents: IncidentStore | None = None
    guard: PersonCameraGuard | None = None
    armed: bool = False
    _lock: threading.Lock = field(default_factory=threading.Lock, repr=False)

    def set_armed(self, armed: bool) -> None:
        with self._lock:
            self.armed = armed

    def get_armed(self) -> bool:
        with self._lock:
            return self.armed


class PersonMonitorPreferences(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    auto_snapshot: bool = True
    auto_record: bool = False
    auto_alarm: bool = False


class IncidentQuery(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    after_id: int = 0
    limit: int = 100


class ArmRequest(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    armed: bool


class RtcOffer(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    sdp: str
    type: str
    include_audio: bool = False


class SpeakRequest(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    message: str


class PairingRequest(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    device_id: str
    pubkey: str
    nonce: str
    proof: str
    label: str


def create_app(state: GatewayState) -> FastAPI:
    app = FastAPI(title="LaptopWatch Paired Gateway", docs_url=None, redoc_url=None, openapi_url=None)
    # Serializes camera side effects against disarm/stop requests so a
    # concurrent start cannot turn the camera back on after the owner disarms.
    media_lock = asyncio.Lock()
    pairing_limit = __import__('laptopwatch.security', fromlist=['SlidingWindowLimiter']).SlidingWindowLimiter(max_attempts=6, period=60)

    @app.on_event("shutdown")
    async def shutdown_media():
        if state.rtc is not None:
            await state.rtc.close()
        if state.evidence is not None:
            await asyncio.to_thread(state.evidence.stop_if_recording)
        if state.guard is not None:
            await asyncio.to_thread(state.guard.stop)

    @app.middleware("http")
    async def secure_request(request: Request, call_next):
        path = request.url.path
        is_pairing = path == "/pair/request" and request.method == "POST"
        if not path.startswith("/api/") and not is_pairing:
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
        if is_pairing:
            if not pairing_limit.allow(peer_ip, float(__import__('time').time())):
                return JSONResponse({"error": "Too many pairing attempts"}, status_code=429)
            if len(request.state.raw_body) > 4096:
                return JSONResponse({"error": "Pairing request too large"}, status_code=413)
        else:
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

    @app.post("/pair/request")
    async def request_pairing(request: Request):
        if state.pairing is None:
            raise HTTPException(status_code=404, detail="Pairing disabled")
        try:
            payload = PairingRequest.model_validate_json(request.state.raw_body)
            pending_id = state.pairing.request(
                device_id=payload.device_id, pubkey=payload.pubkey,
                challenge=payload.nonce, proof=payload.proof, label=payload.label,
            )
        except (AuthenticationError, ValueError):
            raise HTTPException(status_code=403, detail="Invalid or expired pairing invitation")
        return {"pending_id": pending_id, "status": "AWAITING_LOCAL_APPROVAL"}

    @app.get("/api/status")
    async def status(request: Request):
        return {"online": True, "motion_armed": state.get_armed(), "location": None,
                "subject_identified": None,
                "media_transport": "WEBRTC" if state.rtc is not None else "SNAPSHOT_ONLY",
                "media_active": bool(state.rtc is not None and state.rtc.active),
                "person_detector_configured": state.guard is not None,
                "person_monitor_operational": state.guard.operational if state.guard is not None else False,
                "person_monitor_error": state.guard.last_error if state.guard is not None else None}

    @app.post("/api/warning/tts")
    async def tts_warning(request: Request):
        try:
            payload=SpeakRequest.model_validate_json(request.state.raw_body)
            text=payload.message.strip()
            if not 1 <= len(text) <= 400 or any(ord(c)<32 for c in text):
                raise ValueError("Invalid text")
        except (ValueError, TypeError):
            raise HTTPException(status_code=400, detail="Invalid text warning")
        try:
            ok=await asyncio.to_thread(state.controller.speak,text)
        except (OSError, RuntimeError):
            ok=False
        state.audit.append("tts",request.state.paired_device,result="success" if ok else "failure")
        if not ok:
            raise HTTPException(status_code=503, detail="Speech output unavailable on laptop")
        return {"success":True}

    @app.post("/api/config/armed")
    async def arm(request: Request):
        async with media_lock:
            try:
                parsed = ArmRequest.model_validate_json(request.state.raw_body)
            except Exception:
                raise HTTPException(status_code=400, detail="Invalid arm payload")
            if not parsed.armed:
                # Deny every new capture BEFORE media cleanup; a failed camera
                # device release can no longer leave the app armed.
                state.set_armed(False)
                try:
                    if state.guard is not None:
                        await asyncio.to_thread(state.guard.disarm)
                    if state.rtc is not None:
                        await state.rtc.close()
                    if state.evidence is not None:
                        await asyncio.to_thread(state.evidence.stop_if_recording)
                except Exception:
                    state.audit.append("config", request.state.paired_device, result="failure",
                                       metadata={"armed":"False","release":"failed"})
                    raise HTTPException(status_code=503, detail="Disarmed but camera release failed")
            else:
                if state.guard is not None:
                    await asyncio.to_thread(state.guard.arm)
                state.set_armed(True)
            state.audit.append("config", request.state.paired_device, result="success", metadata={"armed": str(parsed.armed)})
            return {"success": True, "armed": parsed.armed}

    @app.post("/api/config/person-monitor")
    async def configure_person_monitor(request: Request):
        async with media_lock:
            if state.guard is None:
                raise HTTPException(status_code=503, detail="Person detector model not installed")
            try:
                pref=PersonMonitorPreferences.model_validate_json(request.state.raw_body)
            except ValueError:
                raise HTTPException(status_code=400, detail="Invalid monitor configuration")
            settings=await asyncio.to_thread(
                state.guard.configure, auto_snapshot=pref.auto_snapshot,
                auto_alarm=pref.auto_alarm, auto_record=pref.auto_record,
            )
            state.audit.append("config", request.state.paired_device, result="success",
                metadata={k:str(v) for k,v in settings.items()})
            return {"configured":True,**settings}

    @app.post("/api/incidents/query")
    async def query_incidents(request: Request):
        if state.incidents is None:
            raise HTTPException(status_code=503, detail="Incident monitor storage not configured")
        try:
            query = IncidentQuery.model_validate_json(request.state.raw_body)
            if query.after_id < 0 or not 1 <= query.limit <= 100:
                raise ValueError("invalid incident cursor")
        except (ValueError, TypeError):
            raise HTTPException(status_code=400, detail="Invalid incident query")
        incidents = await asyncio.to_thread(
            state.incidents.recent, after_id=query.after_id, limit=query.limit
        )
        return {"events": incidents, "next_after_id": (
            incidents[-1]["id"] if incidents else query.after_id)}

    @app.post("/api/incidents/clear")
    async def clear_incidents(request: Request):
        if request.state.raw_body:
            raise HTTPException(status_code=400, detail="No body allowed")
        if state.incidents is None:
            raise HTTPException(status_code=503, detail="Incident monitor storage not configured")
        count = await asyncio.to_thread(state.incidents.clear)
        state.audit.append("incident_clear", request.state.paired_device, result="success",
                           metadata={"count":str(count)})
        return {"deleted":count}

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

    async def pause_person_monitor():
        if state.guard is not None:
            await asyncio.to_thread(state.guard.pause)

    def resume_person_monitor():
        if state.guard is not None and state.get_armed() and not (
            state.rtc is not None and state.rtc.active
        ) and not (state.evidence is not None and state.evidence.recording):
            state.guard.resume()

    @app.get("/api/camera/frame")
    async def camera_snapshot(request: Request):
        async with media_lock:
            if not state.get_armed():
                raise HTTPException(status_code=409, detail="Camera monitoring is disarmed")
            await pause_person_monitor()
            try:
                if state.rtc is not None and state.rtc.active:
                    image = await state.rtc.snapshot_jpeg()
                elif state.evidence is not None:
                    image = await asyncio.to_thread(state.evidence.snapshot)
                else:
                    image = await asyncio.to_thread(state.controller.snapshot)
            except Exception:
                image = None
            finally:
                resume_person_monitor()
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
            await pause_person_monitor()
            try:
                if state.rtc is not None and state.rtc.active:
                    jpeg = await state.rtc.snapshot_jpeg()
                    if not jpeg:
                        raise CaptureUnavailable("No current WebRTC video frame")
                    result = await asyncio.to_thread(state.evidence.store_photo, jpeg)
                else:
                    result = await asyncio.to_thread(state.evidence.take_photo)
            except (CaptureUnavailable, OSError):
                state.audit.append("snapshot", request.state.paired_device, result="failure")
                raise HTTPException(status_code=503, detail="Unable to capture photo")
            finally:
                resume_person_monitor()
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
                await pause_person_monitor()
                # A single-owner webcam may not be opened by both WebRTC and
                # a separate MP4 writer. Recording pauses live media; clients
                # must reconnect deliberately after the clip finalizes.
                if state.rtc is not None and state.rtc.active:
                    await state.rtc.close()
                session = await asyncio.to_thread(state.evidence.start_recording)
            except (CaptureUnavailable, OSError):
                resume_person_monitor()
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
            finally:
                resume_person_monitor()
            state.audit.append("record_stop", request.state.paired_device, result="success",
                               metadata={"evidence_id": clip.id})
            return {"success": True, "recording": False, "id": clip.id,
                    "filename": clip.relative_file, "frames": clip.frames, "size_bytes": clip.file_bytes}

    @app.post("/api/webrtc/offer")
    async def webrtc_offer(request: Request):
        async with media_lock:
            if not state.get_armed():
                raise HTTPException(status_code=409, detail="Camera is disarmed")
            if state.rtc is None:
                raise HTTPException(status_code=503, detail="WebRTC transport unavailable")
            if state.evidence is not None and state.evidence.recording:
                raise HTTPException(status_code=409, detail="MP4 recording has exclusive camera access")
            try:
                await pause_person_monitor()
                info=RtcOffer.model_validate_json(request.state.raw_body)
                if info.type!="offer":
                    raise ValueError("Expected an SDP offer")
                return await state.rtc.offer(request.state.paired_device,
                                             info.sdp, include_audio=info.include_audio)
            except (MediaUnavailable, ValueError, OSError) as error:
                resume_person_monitor()
                raise HTTPException(status_code=503, detail=str(error))

    @app.post("/api/webrtc/stop")
    async def webrtc_stop(request: Request):
        async with media_lock:
            if request.state.raw_body:
                raise HTTPException(status_code=400, detail="No body allowed")
            if state.rtc is not None:
                await state.rtc.close(request.state.paired_device)
            resume_person_monitor()
            return {"success": True}

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

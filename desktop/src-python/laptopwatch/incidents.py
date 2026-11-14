"""Consent-gated, camera-visible person-event monitoring on the owned laptop.

A motion trigger alone is never an intruder incident. No model weights are
bundled. The owner must install and hash-pin an authorized detection model,
arm the camera, and ensure lawful notices. This service never runs during
unarmed, paused or powered-off states.
"""
from __future__ import annotations

from dataclasses import dataclass
import math
import os
from pathlib import Path
import sqlite3
import threading
import time
from typing import Any, Callable
import uuid

from .evidence import EvidenceRecorder, CaptureUnavailable
from .person_detector import MotionPersonGate


class IncidentStore:
    """Local, bounded incident journal; unlike a motion event, person is verified."""
    def __init__(self, path: str | Path, *, retention_days: int = 30, max_rows: int = 1000):
        if not 1 <= retention_days <= 365:
            raise ValueError("Retention must be between 1 and 365 days")
        if not 10 <= max_rows <= 100_000:
            raise ValueError("Invalid max incident count")
        self.path = Path(path)
        self.path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
        self._lock = threading.RLock()
        self._db = sqlite3.connect(self.path, timeout=5, isolation_level=None, check_same_thread=False)
        self._db.execute("PRAGMA journal_mode=WAL")
        self._db.execute("PRAGMA busy_timeout=5000")
        self._db.execute(
            "CREATE TABLE IF NOT EXISTS incidents("
            "id INTEGER PRIMARY KEY AUTOINCREMENT,"
            "observed_at_ms INTEGER NOT NULL, kind TEXT NOT NULL,"
            "confidence REAL NOT NULL, evidence_id TEXT, video_id TEXT,"
            "state TEXT NOT NULL DEFAULT 'unread')")
        self._db.execute("CREATE INDEX IF NOT EXISTS idx_incident_time ON incidents(observed_at_ms DESC)")
        self.retention_days, self.max_rows = retention_days, max_rows
        if os.name == "posix":
            os.chmod(self.path, 0o600)

    def add(self, confidence: float, evidence_id: str | None, *, now_ms: int | None = None) -> int:
        if not math.isfinite(confidence) or not 0.0 <= confidence <= 1.0:
            raise ValueError("Invalid person confidence")
        if evidence_id is not None and (len(evidence_id)!=32 or
            any(c not in "0123456789abcdef" for c in evidence_id)):
            raise ValueError("Invalid evidence ID")
        now_ms = now_ms if now_ms is not None else time.time_ns() // 1_000_000
        with self._lock:
            cursor=self._db.execute(
                "INSERT INTO incidents(observed_at_ms,kind,confidence,evidence_id) VALUES (?,?,?,?)",
                (now_ms, "person_verified", confidence, evidence_id))
            self._db.execute("DELETE FROM incidents WHERE observed_at_ms < ?",
                             (now_ms - self.retention_days * 86_400_000,))
            self._db.execute("DELETE FROM incidents WHERE id NOT IN "
                             "(SELECT id FROM incidents ORDER BY id DESC LIMIT ?)",
                             (self.max_rows,))
            return int(cursor.lastrowid)

    def attach_video(self, event_id: int, video_id: str) -> None:
        if len(video_id) != 32 or any(c not in "0123456789abcdef" for c in video_id):
            raise ValueError("Invalid video evidence identifier")
        with self._lock:
            self._db.execute("UPDATE incidents SET video_id=? WHERE id=?",
                             (video_id, event_id))

    def recent(self, *, after_id: int = 0, limit: int = 100) -> list[dict]:
        if not 0 <= after_id <= 9_223_372_036_854_775_807:
            raise ValueError("Invalid cursor")
        limit=min(max(limit, 1), 100)
        with self._lock:
            rows=self._db.execute(
                "SELECT id,observed_at_ms,kind,confidence,evidence_id,video_id,state "
                "FROM incidents WHERE id>? ORDER BY id ASC LIMIT ?",
                (after_id, limit)).fetchall()
        return [dict(zip(("id","observed_at_ms","kind","confidence","evidence_id","video_id","state"), row))
                for row in rows]

    def clear(self) -> int:
        with self._lock:
            count=self._db.execute("SELECT COUNT(*) FROM incidents").fetchone()[0]
            self._db.execute("DELETE FROM incidents")
            return int(count)

    def close(self) -> None:
        with self._lock:
            self._db.close()


class PersonCameraGuard:
    """Exclusive idle-time camera monitor; yields webcam to photo/MP4/WebRTC.

    Pause is fail-closed: controller won't open a second camera handle unless
    this monitor has acknowledged and released its current capture handle.
    No person model -> no guard object -> no false 'intruder detected' events.
    """
    def __init__(self, detector: MotionPersonGate, store: IncidentStore,
                 evidence: EvidenceRecorder, *, cv2_module: Any = None,
                 sample_interval_seconds: float = 0.4, audit: Any = None):
        self.detector,self.store,self.evidence=detector,store,evidence
        self.cv2=cv2_module
        self.audit=audit
        self.interval=max(0.2,min(2.0,sample_interval_seconds))
        self._enabled=threading.Event()
        self._pause=threading.Event()
        self._released=threading.Event()
        self._released.set()
        self._stop=threading.Event()
        self._thread: threading.Thread | None = None
        self._lock=threading.RLock()
        self._healthy=False
        self._error: str | None = None
        self._triggered=0
        self.auto_snapshot=True
        self.auto_alarm=False
        self.auto_record=False
        self.alarm_controller=None
        self._writer=None
        self._clip_id: str | None=None
        self._clip_path=None
        self._clip_incident_id=None
        self._clip_started=0.0
        self._clip_frames=0

    @property
    def armed(self) -> bool:
        return self._enabled.is_set()

    @property
    def operational(self) -> bool:
        with self._lock:
            return self._enabled.is_set() and self._healthy and not self._pause.is_set()

    @property
    def last_error(self) -> str | None:
        with self._lock:
            return self._error

    def configure(self, *, auto_snapshot: bool, auto_alarm: bool,
                  auto_record: bool = False) -> dict:
        if not all(type(v) is bool for v in (auto_snapshot, auto_alarm, auto_record)):
            raise ValueError("Person monitor settings must be booleans")
        with self._lock:
            self.auto_snapshot=auto_snapshot
            self.auto_alarm=auto_alarm
            self.auto_record=auto_record
        return {"auto_snapshot":auto_snapshot,"auto_alarm":auto_alarm,
                "auto_record":auto_record}

    def arm(self) -> None:
        with self._lock:
            if self._thread is None or not self._thread.is_alive():
                self._stop.clear()
                self._thread=threading.Thread(
                    target=self._run, daemon=True, name="LaptopWatch-PersonGuard")
                self._thread.start()
            self._pause.clear()
            self._enabled.set()

    def pause(self, *, timeout_seconds: float = 5.0) -> None:
        if not self._enabled.is_set():
            return
        self._pause.set()
        if not self._released.wait(timeout_seconds):
            raise CaptureUnavailable("Camera monitor did not release hardware safely")

    def resume(self) -> None:
        if self._enabled.is_set() and not self._stop.is_set():
            self._pause.clear()

    def disarm(self) -> None:
        self._enabled.clear()
        self._pause.set()
        if not self._released.wait(5.0):
            raise CaptureUnavailable("Camera guard did not release camera")

    def stop(self) -> None:
        self._enabled.clear()
        self._pause.set()
        self._stop.set()
        thread=self._thread
        if thread is not None:
            thread.join(timeout=6.0)
            if thread.is_alive():
                raise CaptureUnavailable("Camera guard worker could not terminate")
        self._thread=None

    def _begin_clip(self, cv2, frame, incident_id: int) -> None:
        """Bounded 12-second MP4 from the already-owned monitoring webcam.

        Never opens a second capture device, so recording does not fight the
        armed camera guard. Missing codecs fail without inventing a video ID.
        """
        clip_id=uuid.uuid4().hex
        target=self.evidence.directory / f"video-{clip_id}.mp4"
        height,width=frame.shape[:2]
        fps=max(1.0,min(5.0,1.0/self.interval))
        writer=cv2.VideoWriter(str(target),
                               cv2.VideoWriter_fourcc(*"mp4v"), fps,
                               (width,height))
        if not writer.isOpened():
            writer.release()
            target.unlink(missing_ok=True)
            return
        if os.name=="posix":
            target.chmod(0o600)
        self._writer=writer
        self._clip_id,self._clip_path,self._clip_incident_id=clip_id,target,incident_id
        self._clip_frames=0
        self._clip_started=time.monotonic()

    def _end_clip(self) -> None:
        writer,self._writer=self._writer,None
        if writer is None:
            return
        try:
            writer.release()
            if (self._clip_frames >= 2 and self._clip_path is not None and
                self._clip_path.is_file() and
                0 < self._clip_path.stat().st_size <= 128*1024*1024):
                self.store.attach_video(self._clip_incident_id, self._clip_id)
            elif self._clip_path is not None:
                self._clip_path.unlink(missing_ok=True)
        finally:
            self._clip_id=self._clip_path=self._clip_incident_id=None
            self._clip_frames=0

    def _run(self) -> None:
        camera=None
        cv2=self.cv2
        try:
            if cv2 is None:
                import cv2 as opencv
                cv2=opencv
            while not self._stop.is_set():
                if not self._enabled.is_set() or self._pause.is_set():
                    self._end_clip()
                    if camera is not None:
                        camera.release()
                        camera=None
                    with self._lock:
                        self._healthy=False
                    self._released.set()
                    self._stop.wait(0.07)
                    continue
                if camera is None:
                    try:
                        camera=cv2.VideoCapture(0)
                        if not camera.isOpened():
                            raise CaptureUnavailable("Webcam unavailable for person monitor")
                        camera.set(cv2.CAP_PROP_BUFFERSIZE, 1)
                        self._released.clear()
                    except Exception:
                        if camera is not None:
                            camera.release()
                        camera=None
                        with self._lock:
                            self._healthy=False
                            self._error="Camera unavailable"
                        self._released.set()
                        self._stop.wait(2)
                        continue
                ok,frame=camera.read()
                if not ok:
                    with self._lock:
                        self._healthy=False
                        self._error="Camera stopped producing frames"
                    camera.release()
                    camera=None
                    self._released.set()
                    self._stop.wait(1)
                    continue
                with self._lock:
                    self._healthy=True
                    self._error=None
                try:
                    result=self.detector.inspect(frame)
                    if result["alert"] and self._enabled.is_set() and not self._pause.is_set():
                        with self._lock:
                            take_photo=self.auto_snapshot
                            take_clip=self.auto_record
                            sound_alarm=self.auto_alarm
                        evidence_id=None
                        if take_photo:
                            image_ok,encoded=cv2.imencode(
                                ".jpg",frame,[int(cv2.IMWRITE_JPEG_QUALITY),80])
                            if image_ok:
                                saved=self.evidence.store_photo(encoded.tobytes())
                                evidence_id=str(saved["id"])
                        incident_id=self.store.add(
                            float(result["confidence"]),evidence_id)
                        self._triggered+=1
                        if take_clip and self._writer is None:
                            self._begin_clip(cv2, frame, incident_id)
                        if sound_alarm and self.alarm_controller is not None:
                            self.alarm_controller.alarm()
                        if self.audit is not None:
                            self.audit.append(
                                "person_alert", "local-person-guard", result="success",
                                metadata={"event_id":str(incident_id)})
                    if self._writer is not None:
                        self._writer.write(frame)
                        self._clip_frames+=1
                        if (time.monotonic() - self._clip_started >= 12 or
                            self._clip_frames >= 60):
                            self._end_clip()
                except Exception:
                    # A bad model or encoder must never synthesize an alert.
                    with self._lock:
                        self._error="Person detector, evidence or audit failed"
                self._stop.wait(self.interval)
        except Exception:
            with self._lock:
                self._healthy=False
                self._error="Person guard stopped unexpectedly"
        finally:
            self._end_clip()
            if camera is not None:
                camera.release()
            with self._lock:
                self._healthy=False
            self._released.set()

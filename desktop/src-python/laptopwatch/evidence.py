"""Explicitly armed webcam photos and bounded MP4 clips stored on the laptop.

The capture LED stays under operating-system/hardware control. Does not
implement hidden capture, background audio, or remote upload.
"""
from __future__ import annotations

from dataclasses import dataclass
import os
from pathlib import Path
import threading
import time
import uuid
from typing import Any, Callable


class CaptureUnavailable(RuntimeError):
    pass


@dataclass(frozen=True)
class VideoResult:
    id: str
    frames: int
    file_bytes: int
    relative_file: str


class EvidenceRecorder:
    """Single-owner camera lifecycle, no duplicated camera opens during recording.

    Never reports recording success until a decodable frame has been written.
    A bounded clip ends automatically; caller must stop explicitly to receive
    its result. Evidence stays under a private user-specific directory.
    """
    def __init__(self, directory: str | Path, *, cv2_module: Any | None = None,
                 max_duration_seconds: float = 180.0):
        self.directory = Path(directory)
        self.directory.mkdir(parents=True, exist_ok=True, mode=0o700)
        if os.name == "posix":
            self.directory.chmod(0o700)
        self._cv2 = cv2_module
        self._lock = threading.RLock()
        self._capture = None
        self._writer = None
        self._thread: threading.Thread | None = None
        self._stop = threading.Event()
        self._last_frame = None
        self._current_id: str | None = None
        self._file: Path | None = None
        self._frames = 0
        self._failure: str | None = None
        self._last_result: VideoResult | None = None
        self.max_duration_seconds = min(max(1.0, float(max_duration_seconds)), 300.0)

    @property
    def recording(self) -> bool:
        with self._lock:
            return self._thread is not None and self._thread.is_alive()

    def _import_cv2(self):
        if self._cv2 is None:
            try:
                import cv2
            except ImportError as exc:
                raise CaptureUnavailable("OpenCV capture is not installed") from exc
            self._cv2 = cv2
        return self._cv2

    def _open(self):
        cv2 = self._import_cv2()
        cap = cv2.VideoCapture(0)
        if not cap.isOpened():
            cap.release()
            raise CaptureUnavailable("Camera not available or permission denied")
        cap.set(cv2.CAP_PROP_BUFFERSIZE, 1)
        return cap

    def snapshot(self) -> bytes:
        """Read a single current JPEG frame, without leaving the camera open."""
        cv2 = self._import_cv2()
        with self._lock:
            frame = self._last_frame.copy() if self._last_frame is not None and self.recording else None
            if frame is None:
                # Protect shared device access: never open a second handle
                # while the recording worker is active.
                if self.recording:
                    raise CaptureUnavailable("Waiting for first recording frame")
                cap = self._open()
                try:
                    ok, frame = cap.read()
                    if not ok:
                        raise CaptureUnavailable("Camera did not produce a frame")
                finally:
                    cap.release()
            ok, jpeg = cv2.imencode('.jpg', frame, [int(cv2.IMWRITE_JPEG_QUALITY), 80])
            if not ok:
                raise CaptureUnavailable("Unable to encode JPEG")
            return jpeg.tobytes()

    def take_photo(self) -> dict[str, str | int]:
        jpeg = self.snapshot()
        clip_id = uuid.uuid4().hex
        filename = f"photo-{clip_id}.jpg"
        path = self.directory / filename
        fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, 'wb') as handle:
            handle.write(jpeg)
            handle.flush()
            os.fsync(handle.fileno())
        return {"id": clip_id, "kind": "photo", "filename": filename,
                "size_bytes": len(jpeg)}

    def start_recording(self) -> str:
        with self._lock:
            if self.recording:
                raise CaptureUnavailable("Already recording")
            cv2 = self._import_cv2()
            cap = self._open()
            try:
                ok, first = cap.read()
                if not ok:
                    raise CaptureUnavailable("No initial video frame")
                height, width = first.shape[:2]
                if width < 64 or height < 64:
                    raise CaptureUnavailable("Invalid camera frame dimensions")
                fps = cap.get(cv2.CAP_PROP_FPS)
                fps = float(fps) if 1 <= fps <= 60 else 15.0
                clip_id = uuid.uuid4().hex
                path = self.directory / f"video-{clip_id}.mp4"
                writer = cv2.VideoWriter(str(path), cv2.VideoWriter_fourcc(*'mp4v'), fps, (width, height))
                if not writer.isOpened():
                    writer.release()
                    raise CaptureUnavailable("MP4 encoder unavailable on this system")
                if os.name == 'posix':
                    path.chmod(0o600)
                self._capture, self._writer = cap, writer
                self._current_id, self._file = clip_id, path
                self._frames = 0
                self._failure = None
                self._last_result = None
                self._stop.clear()
                self._last_frame = first.copy()
                self._thread = threading.Thread(target=self._record_loop, args=(first,), daemon=True,
                                                name='LaptopWatch-VideoRecorder')
                self._thread.start()
                return clip_id
            except Exception:
                cap.release()
                raise

    def _record_loop(self, first) -> None:
        start = time.monotonic()
        try:
            with self._lock:
                self._writer.write(first)
                self._frames += 1
            while not self._stop.is_set() and time.monotonic() - start < self.max_duration_seconds:
                ok, frame = self._capture.read()
                if not ok:
                    self._failure = "Camera stopped producing frames"
                    break
                with self._lock:
                    self._writer.write(frame)
                    self._last_frame = frame.copy()
                    self._frames += 1
        except Exception:
            self._failure = "Camera encoder failed"
        finally:
            with self._lock:
                try:
                    if self._writer:
                        self._writer.release()
                finally:
                    if self._capture:
                        self._capture.release()
                self._capture = self._writer = self._last_frame = None
                if self._file and self._file.exists():
                    size = self._file.stat().st_size
                    if self._frames == 0 or size == 0:
                        self._file.unlink(missing_ok=True)
                    else:
                        self._last_result = VideoResult(self._current_id or '', self._frames,
                                                        size, self._file.name)

    def stop_recording(self) -> VideoResult:
        with self._lock:
            thread = self._thread
            if thread is None:
                raise CaptureUnavailable("No active video session")
            self._stop.set()
        thread.join(timeout=8)
        if thread.is_alive():
            raise CaptureUnavailable("Recording termination timed out")
        with self._lock:
            self._thread = None
            result = self._last_result
            if result is None:
                raise CaptureUnavailable(self._failure or "Video has no frames")
            return result

    def stop_if_recording(self) -> None:
        with self._lock:
            active = self._thread is not None
        if active:
            self.stop_recording()

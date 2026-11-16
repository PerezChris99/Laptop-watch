"""Regression tests for evidence-loss and partially-written media defects."""
from __future__ import annotations

import threading
from concurrent.futures import ThreadPoolExecutor
import pytest

from laptopwatch.evidence import EvidenceRecorder, CaptureUnavailable


JPEG = bytes((0xff, 0xd8)) + b'actual-photo-bytes' + bytes((0xff, 0xd9))


def test_finished_recording_cannot_be_replaced_before_finalization(tmp_path):
    recorder = EvidenceRecorder(tmp_path)
    completed = threading.Thread(target=lambda: None)
    completed.start()
    completed.join()
    recorder._thread = completed
    recorder._last_result = object()  # must not be discarded
    with pytest.raises(CaptureUnavailable, match="Finish the previous clip"):
        recorder.start_recording()
    assert recorder._thread is completed
    assert recorder._last_result is not None


class DummyFrame:
    shape = (480, 640, 3)
    def copy(self):
        return self


class DummyCapture:
    def __init__(self):
        self.released = False
    def isOpened(self):
        return True
    def set(self, *_args):
        return True
    def get(self, *_args):
        return 15
    def read(self):
        return True, DummyFrame()
    def release(self):
        self.released = True


class FailingEncoder:
    def __init__(self):
        self.released = False
    def isOpened(self):
        return False
    def release(self):
        self.released = True


class DummyOpenCV:
    CAP_PROP_BUFFERSIZE = 1
    CAP_PROP_FPS = 2
    def __init__(self):
        self.camera = DummyCapture()
        self.encoder = FailingEncoder()
    def VideoCapture(self, *_args):
        return self.camera
    def VideoWriter(self, *_args):
        return self.encoder
    def VideoWriter_fourcc(self, *_args):
        return 0


def test_failed_mp4_encoder_releases_camera_and_does_not_mark_active(tmp_path):
    driver = DummyOpenCV()
    recorder = EvidenceRecorder(tmp_path, cv2_module=driver)
    with pytest.raises(CaptureUnavailable, match="MP4 encoder"):
        recorder.start_recording()
    assert driver.camera.released
    assert driver.encoder.released
    assert recorder._thread is None
    assert recorder._capture is None and recorder._writer is None
    assert list(tmp_path.glob("video-*.mp4")) == []


def test_concurrent_jpeg_saves_are_durable_and_unique(tmp_path):
    recorder = EvidenceRecorder(tmp_path)
    with ThreadPoolExecutor(max_workers=6) as pool:
        records = list(pool.map(recorder.store_photo, [JPEG] * 12))
    assert len({record["id"] for record in records}) == 12
    assert all((tmp_path / record["filename"]).read_bytes() == JPEG
               for record in records)
    assert recorder.storage_policy.usage().files == 12


def test_fsync_failure_removes_partial_photo(tmp_path, monkeypatch):
    recorder = EvidenceRecorder(tmp_path)
    def broken_fsync(_fd):
        raise OSError("disk write failed")
    monkeypatch.setattr("laptopwatch.evidence.os.fsync", broken_fsync)
    with pytest.raises(OSError):
        recorder.store_photo(JPEG)
    assert list(tmp_path.glob("photo-*.jpg")) == []

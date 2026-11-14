"""Security/behavior regression tests for consent-gated person incident storage."""
from __future__ import annotations

import os
import threading
import time

import pytest

from laptopwatch.incidents import IncidentStore, PersonCameraGuard


def test_incident_store_is_bounded_and_cursor_is_monotonic(tmp_path):
    store=IncidentStore(tmp_path/"audit"/"incidents.db",retention_days=30,max_rows=10)
    ids=[store.add(0.86,None,now_ms=1_700_000_000_000+i*1000) for i in range(15)]
    assert ids==sorted(ids)
    rows=store.recent(limit=100)
    assert len(rows)==10
    assert rows[0]["id"]==ids[-10]
    assert all(row["kind"]=="person_verified" for row in rows)
    store.attach_video(ids[-1],"a"*32)
    tail=store.recent(after_id=ids[-2])
    assert len(tail)==1 and tail[0]["video_id"]=="a"*32
    assert store.clear()==10
    assert store.recent()==[]
    store.close()


def test_incident_store_rejects_fabricated_confidence_and_path_ids(tmp_path):
    store=IncidentStore(tmp_path/"incidents.db")
    for score in (float("nan"),float("inf"),-0.01,1.1):
        with pytest.raises(ValueError):
            store.add(score,None)
    with pytest.raises(ValueError):
        store.add(.98,"../private")
    with pytest.raises(ValueError):
        store.attach_video(5,"../private")
    store.close()


def test_guard_settings_are_explicit_and_do_not_autostart_camera(tmp_path):
    class FakeDetector:
        def inspect(self,frame):
            return {"alert":False,"person":False,"motion":False,"confidence":0.0}
    class Evidence:
        directory=tmp_path/"evidence"
    store=IncidentStore(tmp_path/"incidents.db")
    guard=PersonCameraGuard(FakeDetector(),store,Evidence())
    assert not guard.armed
    assert not guard.operational
    assert guard.configure(auto_snapshot=False,auto_alarm=True,auto_record=False)=={
        "auto_snapshot":False,"auto_alarm":True,"auto_record":False}
    with pytest.raises(ValueError):
        guard.configure(auto_snapshot="yes",auto_alarm=True,auto_record=False)
    store.close()


def test_bad_model_not_falsely_reported_as_person(tmp_path):
    class FakeDetector:
        def inspect(self,frame):
            return {"alert":False,"person":False,"motion":True,"confidence":0.0}
    import numpy as np
    class FakeCamera:
        def __init__(self):self.closed=False
        def isOpened(self):return True
        def set(self,*a):pass
        def read(self):return True,np.zeros((64,64,3),dtype=np.uint8)
        def release(self):self.closed=True
    class FakeCv2:
        CAP_PROP_BUFFERSIZE=1
        def __init__(self):self.opened=[]
        def VideoCapture(self,id):
            cam=FakeCamera()
            self.opened.append(cam)
            return cam
    class FakeEvidence:
        directory=tmp_path
        def store_photo(self,img):
            raise AssertionError("No person, photo must never be taken")
    store=IncidentStore(tmp_path/"incidents.db")
    cv2=FakeCv2()
    guard=PersonCameraGuard(FakeDetector(),store,FakeEvidence(),cv2_module=cv2,
                            sample_interval_seconds=0.2)
    guard.arm()
    time.sleep(0.45)
    assert guard.armed and guard.operational
    guard.pause()
    assert not guard.operational
    assert all(c.closed for c in cv2.opened)
    assert store.recent()==[]
    guard.disarm()
    guard.stop()
    store.close()


def test_photo_magic_validation(tmp_path):
    from laptopwatch.evidence import EvidenceRecorder, CaptureUnavailable
    recorder=EvidenceRecorder(tmp_path/"evidence")
    correct=b"\xff\xd8" + b"JPEG bytes" + b"\xff\xd9"
    saved=recorder.store_photo(correct)
    assert (tmp_path/"evidence"/saved["filename"]).read_bytes()==correct
    with pytest.raises(CaptureUnavailable):
        recorder.store_photo(b"\\xff\\xd8not-a-jpeg\\xff\\xd9")

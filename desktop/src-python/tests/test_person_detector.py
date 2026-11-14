"""Synthetic detector contracts, not a substitute for real-scene FP evaluation."""
from hashlib import sha256
from pathlib import Path

import numpy as np
import pytest
import cv2

from laptopwatch.person_detector import (
    PersonDetection, YoloxNanoDetector, MotionPersonGate, DetectorUnavailable,
)


def test_refuses_unsigned_or_modified_model(tmp_path):
    model=tmp_path/"yolox_nano.onnx"
    model.write_bytes(b"fake-model-for-unit-tests")
    with pytest.raises(DetectorUnavailable,match="SHA256"):
        YoloxNanoDetector(model,required_sha256="0"*64)
    with pytest.raises(DetectorUnavailable,match="SHA256"):
        YoloxNanoDetector(model,required_sha256="")


def test_yolox_person_class_scoring_and_bbox(tmp_path):
    class Net:
        def __init__(self): self.tensor=None
        def setInput(self,tensor):self.tensor=tensor
        def forward(self):
            output=np.zeros((1,3549,85),dtype=np.float32)
            output[0,0,0:6]=[208,208,100,200,.9,.95]  # person
            output[0,1,0:6]=[100,100,80,120,.9,.01]   # not a person
            return output
    class FakeDNN:
        def __init__(self):self.net=Net()
        def readNetFromONNX(self,path):return self.net
        def NMSBoxes(self,boxes,scores,confidence,nms):
            return np.array([0],dtype=np.int32)
    class FakeCV:
        def __init__(self): self.dnn=FakeDNN()
        def resize(self,image,size):return cv2.resize(image,size)
    path=tmp_path/"fake.onnx"
    path.write_bytes(b"dummy ONNX file")
    adapter=FakeCV()
    detector=YoloxNanoDetector(path,required_sha256=sha256(path.read_bytes()).hexdigest(),
                               cv2_module=adapter)
    frame=np.zeros((480,640,3),dtype=np.uint8)
    found=detector.detect(frame)
    assert len(found)==1
    assert found[0].score>=.8
    assert found[0].right>found[0].left
    assert adapter.dnn.net.tensor.shape==(1,3,416,416)


def test_person_alert_gated_by_classification_and_cooldown():
    class FakeDetector:
        def __init__(self):self.has_person=False
        def detect(self,frame):
            return [PersonDetection(.92,10,10,100,200)] if self.has_person else []
    detector=FakeDetector()
    gate=MotionPersonGate(detector,motion_threshold=.01,cooldown_seconds=60)
    dark=np.zeros((480,640,3),dtype=np.uint8)
    bright=np.full((480,640,3),255,dtype=np.uint8)
    assert gate.inspect(dark,now=0)["alert"] is False
    assert gate.inspect(bright,now=1)["motion"] is True
    assert gate.inspect(bright,now=2)["alert"] is False  # TV/curtain without a person
    detector.has_person=True
    assert gate.inspect(bright,now=3)["alert"] is False
    assert gate.inspect(bright,now=4)["alert"] is True
    assert gate.inspect(bright,now=5)["alert"] is False  # same person cooldown

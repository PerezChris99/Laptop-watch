"""CPU-conscious person-only incident detector.

Motion subtraction is a cheap pre-filter; a licensed, hash-verified YOLOX
Nano ONNX classifier must confirm a person before an intrusion alert.
Without installed model/valid weights, do not trigger person alerts.
"""
from __future__ import annotations

from dataclasses import dataclass
from hashlib import sha256
from pathlib import Path
import math
import threading
import time
from typing import Any, Callable


class DetectorUnavailable(RuntimeError):
    pass


@dataclass(frozen=True)
class PersonDetection:
    score: float
    left: int
    top: int
    right: int
    bottom: int


class YoloxNanoDetector:
    """YOLOX Nano COCO ONNX 416px; person class index zero.

    The model must have the official output [1,3549,85], with box
    coordinates decoded to 416x416 pixel space and sigmoid scores.
    """
    def __init__(self, model_path: Path | str, *, required_sha256: str,
                 confidence: float = 0.5, cv2_module: Any = None):
        self.model_path=Path(model_path)
        if not self.model_path.is_file():
            raise DetectorUnavailable("No signed/verified person model installed")
        if not required_sha256 or len(required_sha256)!=64 or any(c not in "0123456789abcdef" for c in required_sha256):
            raise DetectorUnavailable("Person model requires an owner-approved SHA256 pin")
        if sha256(self.model_path.read_bytes()).hexdigest()!=required_sha256:
            raise DetectorUnavailable("Person detector model hash mismatch")
        if not 0.1<=confidence<=0.95:
            raise ValueError("Confidence threshold outside safe range")
        try:
            import cv2
            import numpy as np
        except ImportError as exc:
            raise DetectorUnavailable("OpenCV/Numpy not installed") from exc
        self.cv2=cv2_module or cv2
        self.np=np
        self.net=self.cv2.dnn.readNetFromONNX(str(self.model_path))
        self.confidence=confidence
        self._lock=threading.Lock()

    def detect(self, image: Any) -> list[PersonDetection]:
        cv2=self.cv2
        np=self.np
        height,width=image.shape[:2]
        if height<64 or width<64:
            return []
        ratio=min(416/height,416/width)
        nw,nh=int(width*ratio),int(height*ratio)
        resized=cv2.resize(image,(nw,nh))
        prepared=np.full((416,416,3),114,dtype=np.uint8)
        prepared[:nh,:nw]=resized
        # YOLOX export expects raw BGR float32, NCHW, no /255.
        tensor=np.ascontiguousarray(prepared.transpose(2,0,1)[None],dtype=np.float32)
        with self._lock:
            self.net.setInput(tensor)
            output=self.net.forward()
        output=np.asarray(output)
        if output.shape!=(1,3549,85):
            raise DetectorUnavailable(f"Unexpected YOLOX output dimensions: {output.shape}")
        rows=output[0]
        confidence=rows[:,4]*rows[:,5]  # objectness × person class probability
        candidates=np.where(confidence>=self.confidence)[0]
        if len(candidates)==0:
            return []
        boxes=[]
        scores=[]
        for i in candidates:
            center_x,center_y,w,h=rows[i,:4]
            x0=int(max(0,(center_x-w/2)/ratio))
            y0=int(max(0,(center_y-h/2)/ratio))
            x1=int(min(width,(center_x+w/2)/ratio))
            y1=int(min(height,(center_y+h/2)/ratio))
            if x1<=x0 or y1<=y0:
                continue
            boxes.append([x0,y0,x1-x0,y1-y0])
            scores.append(float(confidence[i]))
        if not boxes:
            return []
        retained=cv2.dnn.NMSBoxes(boxes,scores,self.confidence,0.45)
        return [PersonDetection(scores[int(i)],
                boxes[int(i)][0], boxes[int(i)][1],
                boxes[int(i)][0]+boxes[int(i)][2],
                boxes[int(i)][1]+boxes[int(i)][3])
                for i in np.asarray(retained).flatten()]


class MotionPersonGate:
    """No person alert is emitted without a genuine classifier confirmation."""
    def __init__(self, detector: YoloxNanoDetector, *,
                 motion_threshold: float = 0.025,
                 cooldown_seconds: float = 30.0):
        if not 0 < motion_threshold < 1:
            raise ValueError("Invalid motion threshold")
        self.detector=detector
        self.motion_threshold=motion_threshold
        self.cooldown=cooldown_seconds
        self._previous=None
        self._last_alert=-float("inf")
        self._motion_grace=0
        self._confirmed=0
        self._lock=threading.Lock()

    def inspect(self, image, *, now: float | None = None) -> dict:
        import cv2
        import numpy as np
        now=time.monotonic() if now is None else now
        with self._lock:
            thumb=cv2.resize(image,(320,240))
            gray=cv2.GaussianBlur(cv2.cvtColor(thumb,cv2.COLOR_BGR2GRAY),(21,21),0)
            previous=self._previous
            self._previous=gray
            if previous is None:
                return {"motion":False,"person":False,"alert":False,"confidence":0.0}
            delta=cv2.absdiff(previous,gray)
            threshold=cv2.threshold(delta,25,255,cv2.THRESH_BINARY)[1]
            threshold=cv2.dilate(threshold,None,iterations=2)
            motion=cv2.countNonZero(threshold)/(320*240)>=self.motion_threshold
            self._motion_grace=5 if motion else max(0,self._motion_grace-1)
            if self._motion_grace==0:
                self._confirmed=0
                return {"motion":motion,"person":False,"alert":False,"confidence":0.0}
            detected=self.detector.detect(image)
            confidence=max((d.score for d in detected),default=0.0)
            if detected:
                self._confirmed=min(self._confirmed+1,3)
            else:
                self._confirmed=0
            person=self._confirmed>=2
            alert=person and (now-self._last_alert)>=self.cooldown
            if alert:
                self._last_alert=now
            return {"motion":motion,"person":person,"alert":alert,"confidence":confidence}

import time
from pathlib import Path
import pytest
from laptopwatch.evidence import EvidenceRecorder, CaptureUnavailable


class FakeFrame:
    shape = (120, 160, 3)
    def copy(self): return self


class FakeJpeg:
    def tobytes(self): return b"\xff\xd8\xff\xd9"


class Camera:
    def __init__(self, works=True): self.works=works; self.released=False; self.reads=0
    def isOpened(self): return self.works
    def set(self, *args): pass
    def get(self, *args): return 15
    def read(self):
        self.reads+=1
        time.sleep(0.002)
        return True, FakeFrame()
    def release(self): self.released=True


class Writer:
    def __init__(self, name, works=True):
        self.name=name; self.works=works; self.fp=open(name,'wb') if works else None
    def isOpened(self): return self.works
    def write(self, frame): self.fp.write(b'encoded-frame-1')
    def release(self):
        if self.fp: self.fp.close()


class MockCV:
    CAP_PROP_BUFFERSIZE=1
    CAP_PROP_FPS=2
    IMWRITE_JPEG_QUALITY=3
    def __init__(self): self.cameras=[]; self.encoders=[]; self.camera_works=True;self.writer_works=True
    def VideoCapture(self, i):
        assert i==0
        cam=Camera(self.camera_works)
        self.cameras.append(cam)
        return cam
    def VideoWriter_fourcc(self,*args):return 44
    def VideoWriter(self,name,*args):
        result=Writer(name,self.writer_works)
        self.encoders.append(result)
        return result
    def imencode(self, ext, frame, *args):
        return True, FakeJpeg()


def test_photo_persists_real_jpeg_and_releases_camera(tmp_path):
    cv=MockCV(); recorder=EvidenceRecorder(tmp_path,cv2_module=cv)
    result=recorder.take_photo()
    assert result['size_bytes']==4
    assert (tmp_path/result['filename']).read_bytes() == b'\xff\xd8\xff\xd9'
    assert cv.cameras[-1].released


def test_record_video_and_release_hardware(tmp_path):
    cv=MockCV(); r=EvidenceRecorder(tmp_path,cv2_module=cv,max_duration_seconds=1)
    id_=r.start_recording()
    time.sleep(.03)
    assert r.recording
    result=r.stop_recording()
    assert result.id==id_
    assert result.frames>=1
    assert result.file_bytes>0
    assert (tmp_path/result.relative_file).exists()
    assert cv.cameras[-1].released
    assert not r.recording


def test_no_camera_failure_has_no_evidence(tmp_path):
    cv=MockCV(); cv.camera_works=False
    r=EvidenceRecorder(tmp_path,cv2_module=cv)
    with pytest.raises(CaptureUnavailable):r.take_photo()
    with pytest.raises(CaptureUnavailable):r.start_recording()
    assert all(c.released for c in cv.cameras)
    assert list(tmp_path.iterdir())==[]


def test_no_mp4_encoder_fails_closed(tmp_path):
    cv=MockCV(); cv.writer_works=False
    r=EvidenceRecorder(tmp_path,cv2_module=cv)
    with pytest.raises(CaptureUnavailable): r.start_recording()
    assert cv.cameras[-1].released


def test_duplicate_recording_rejected(tmp_path):
    r=EvidenceRecorder(tmp_path,cv2_module=MockCV())
    r.start_recording()
    with pytest.raises(CaptureUnavailable):r.start_recording()
    r.stop_if_recording()
    assert not r.recording

def test_bounded_local_evidence_inventory_excludes_unrelated_and_symlinks(tmp_path):
    """Metadata list is bounded and never returns a path outside the vault."""
    from laptopwatch.evidence import EvidenceRecorder
    from uuid import uuid4

    vault = EvidenceRecorder(tmp_path)
    photo = vault.store_photo(b'\\xff\\xd8safe-jpeg\\xff\\xd9')
    for _ in range(104):
        (tmp_path / ('photo-' + uuid4().hex + '.jpg')).write_bytes(b'\\xff\\xd8x\\xff\\xd9')
    (tmp_path / 'private.txt').write_text('do not disclose', encoding='utf-8')
    try:
        (tmp_path / ('photo-' + 'e' * 32 + '.jpg')).symlink_to(tmp_path / 'private.txt')
    except (OSError, NotImplementedError):
        pass  # Windows without Developer Mode or symlink privileges.
    page = vault.list_evidence()
    assert len(page) == 100
    assert all(entry['kind'] == 'photo' and len(entry['id']) == 32 for entry in page)
    assert all(entry['size_bytes'] > 0 for entry in page)
    assert 'e' * 32 not in [entry['id'] for entry in page]


"""OS controls and consent-gated still capture for the paired control gateway.

The gateway refuses camera capture when disarmed. WebRTC continuous capture and
person-only motion alerts are separate future subsystems.
"""
from __future__ import annotations

import ctypes
import math
import os
import platform
import shutil
import subprocess
import tempfile
import threading
import wave


class NativeController:
    """Only performs explicitly signed actions requested by a paired device."""
    def __init__(self):
        self._camera_lock = threading.Lock()

    def lock(self) -> bool:
        system = platform.system()
        try:
            if system == 'Windows':
                return bool(ctypes.windll.user32.LockWorkStation())
            if system == 'Darwin':
                path = '/System/Library/CoreServices/Menu Extras/User.menu/Contents/Resources/CGSession'
                return subprocess.run([path, '-suspend'], timeout=5, check=False).returncode == 0
            if system == 'Linux':
                # logind is session-aware on many X11/Wayland systems. Others
                # require an explicit supported portal; report failure rather
                # than pretending a display power-off locked the OS.
                return subprocess.run(['loginctl', 'lock-session'], timeout=5, check=False).returncode == 0
        except (OSError, subprocess.SubprocessError, AttributeError):
            pass
        return False

    def alarm(self) -> bool:
        system = platform.system()
        if system == 'Windows':
            try:
                import winsound
                def play():
                    for hz in (1000, 1500, 1000):
                        winsound.Beep(hz, 250)
                threading.Thread(target=play, daemon=True).start()
                return True
            except (OSError, ImportError):
                return False
        player = shutil.which('afplay') if system == 'Darwin' else shutil.which('aplay')
        if not player:
            return False
        def play_pcm():
            filename = None
            try:
                with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as tmp:
                    filename = tmp.name
                    with wave.open(tmp, 'wb') as sound:
                        sound.setnchannels(1)
                        sound.setsampwidth(2)
                        sound.setframerate(16000)
                        frames = bytearray()
                        for i in range(16000):
                            frequency = 1100 if (i // 4000) % 2 else 1500
                            sample = int(22000 * math.sin(2 * math.pi * frequency * i / 16000))
                            frames.extend(sample.to_bytes(2, 'little', signed=True))
                        sound.writeframes(frames)
                subprocess.run([player, filename], timeout=5, check=False,
                               stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            finally:
                if filename:
                    try: os.remove(filename)
                    except OSError: pass
        threading.Thread(target=play_pcm, daemon=True).start()
        return True

    def snapshot(self) -> bytes | None:
        """Snapshot fallback only; webcam is opened on demand and always released."""
        with self._camera_lock:
            try:
                import cv2
                camera = cv2.VideoCapture(0)
                if not camera.isOpened():
                    camera.release()
                    return None
                try:
                    camera.set(cv2.CAP_PROP_BUFFERSIZE, 1)
                    ok, frame = camera.read()
                    if not ok:
                        return None
                    ok, encoded = cv2.imencode('.jpg', frame, [int(cv2.IMWRITE_JPEG_QUALITY), 75])
                    return encoded.tobytes() if ok else None
                finally:
                    camera.release()
            except (ImportError, OSError, ValueError):
                return None

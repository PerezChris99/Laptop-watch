"""Consent-gated aiortc peer for encrypted low-latency video and optional audio.

No media is created before the owner arms monitoring. The WebRTC handshake
is carried by authenticated, replay-protected HTTPS controls. TURN requires
explicit user configuration; no covert video relay or telemetry is enabled.
"""
from __future__ import annotations

import asyncio
from fractions import Fraction
import os
import platform
import time
from typing import Any


class MediaUnavailable(RuntimeError):
    pass


class LiveCameraTrack:
    """Factory wrapper avoids importing large media frameworks at app startup."""
    def __init__(self):
        try:
            import av
            import cv2
            from aiortc import VideoStreamTrack
        except ImportError as exc:
            raise MediaUnavailable("WebRTC requires aiortc, PyAV and OpenCV") from exc

        class Track(VideoStreamTrack):
            kind = "video"

            def __init__(self):
                super().__init__()
                # Prefer platform camera backend, but allow a fallback.
                sysname=platform.system()
                backend={
                    "Windows":getattr(cv2,"CAP_MSMF",cv2.CAP_ANY),
                    "Darwin":getattr(cv2,"CAP_AVFOUNDATION",cv2.CAP_ANY),
                    "Linux":getattr(cv2,"CAP_V4L2",cv2.CAP_ANY),
                }.get(sysname,cv2.CAP_ANY)
                self.capture=cv2.VideoCapture(0,backend)
                if not self.capture.isOpened():
                    self.capture.release()
                    self.capture=cv2.VideoCapture(0)
                if not self.capture.isOpened():
                    self.capture.release()
                    raise MediaUnavailable("Physical laptop camera is unavailable")
                self.capture.set(cv2.CAP_PROP_BUFFERSIZE, 1)
                self.capture.set(cv2.CAP_PROP_FRAME_WIDTH, 640)
                self.capture.set(cv2.CAP_PROP_FRAME_HEIGHT, 480)
                self._last=None

            async def recv(self):
                pts,time_base=await self.next_timestamp()
                ok,image=await asyncio.to_thread(self.capture.read)
                if not ok:
                    self.stop()
                    raise MediaUnavailable("Camera stopped producing video frames")
                # Never queue frames in Python: new capture reads the newest
                # available frame from the low-buffer device backend.
                self._last=image.copy()
                frame=av.VideoFrame.from_ndarray(image,format="bgr24")
                frame.pts=pts
                frame.time_base=time_base
                return frame

            def stop(self):
                if getattr(self,"capture",None) is not None:
                    self.capture.release()
                    self.capture=None
                super().stop()

        self.track=Track()


class LaptopMicrophoneTrack:
    """Opus-ready PCM track, enabled only after explicit phone intercom request."""
    def __init__(self):
        try:
            import av
            import sounddevice as sd
            from aiortc import AudioStreamTrack
        except ImportError as exc:
            raise MediaUnavailable("Audio requires sounddevice, PyAV and aiortc") from exc

        class Track(AudioStreamTrack):
            kind="audio"
            def __init__(self):
                super().__init__()
                self.input=sd.RawInputStream(samplerate=48000,channels=1,dtype="int16",blocksize=960)
                self.input.start()
                self._timestamp=0
                self._started=time.monotonic()

            async def recv(self):
                # 20ms at 48kHz. RawInputStream.read may block, so move off
                # event loop. Never start this stream without a user action.
                samples,overflow=await asyncio.to_thread(self.input.read,960)
                frame=av.AudioFrame(format="s16",layout="mono",samples=960)
                frame.sample_rate=48000
                frame.pts=self._timestamp
                frame.time_base=Fraction(1,48000)
                frame.planes[0].update(bytes(samples))
                self._timestamp+=960
                return frame

            def stop(self):
                if getattr(self,"input",None) is not None:
                    self.input.stop()
                    self.input.close()
                    self.input=None
                super().stop()

        self.track=Track()


class RtcController:
    """One active phone media peer per laptop (more requires camera relay)."""
    def __init__(self, *, stun_url: str | None = None,
                 turn_url: str | None = None,
                 turn_username: str | None = None,
                 turn_password: str | None = None):
        self.stun_url=stun_url
        self.turn_url=turn_url
        self.turn_username=turn_username
        self.turn_password=turn_password
        if turn_url and not (turn_username and turn_password):
            raise ValueError("TURN requires credentials; never allow an open relay")
        self._peer=None
        self._video=None
        self._mic=None
        self._speaker_task=None
        self._lock=asyncio.Lock()
        self._owner=None

    def _ice_configuration(self):
        from aiortc import RTCConfiguration, RTCIceServer
        servers=[]
        if self.stun_url:
            if not self.stun_url.startswith("stun:"):
                raise MediaUnavailable("Invalid STUN server configuration")
            servers.append(RTCIceServer(urls=self.stun_url))
        if self.turn_url:
            if not self.turn_url.startswith(("turn:","turns:")):
                raise MediaUnavailable("Invalid TURN server configuration")
            servers.append(RTCIceServer(
                urls=self.turn_url,
                username=self.turn_username,
                credential=self.turn_password
            ))
        return RTCConfiguration(iceServers=servers)

    async def offer(self, device_id: str, sdp: str, *,
                    include_audio: bool = False) -> dict:
        if len(sdp)>65_000 or len(sdp)<40:
            raise MediaUnavailable("Invalid SDP offer length")
        if "m=video" not in sdp:
            raise MediaUnavailable("Phone did not request a video track")
        try:
            from aiortc import RTCPeerConnection, RTCSessionDescription
            from aiortc.rtcrtpsender import RTCRtpSender
        except ImportError as exc:
            raise MediaUnavailable("aiortc is not installed") from exc

        async with self._lock:
            # Never silently take over a second user's session.
            if self._peer is not None and self._owner != device_id:
                raise MediaUnavailable("Another paired phone is using this camera")
            if self._peer is not None and self._owner == device_id:
                if include_audio != (self._mic is not None):
                    raise MediaUnavailable("Intercom configuration cannot change during ICE restart")
                # Keep the DTLS identity and media tracks for an ICE restart.
                # Rebuilding the peer here would break the remote's existing
                # RTCPeerConnection on Wi-Fi/cellular changes.
                peer=self._peer
                await peer.setRemoteDescription(RTCSessionDescription(sdp=sdp,type="offer"))
                answer=await peer.createAnswer()
                await peer.setLocalDescription(answer)
                return {
                    "type":peer.localDescription.type,
                    "sdp":peer.localDescription.sdp,
                    "audio_enabled":self._mic is not None,
                    "media_encryption":"DTLS-SRTP",
                }
            await self._close_unlocked()
            peer=RTCPeerConnection(self._ice_configuration())
            self._owner=device_id
            self._peer=peer
            try:
                video=LiveCameraTrack()
                self._video=video
                sender=peer.addTrack(video.track)
                transceiver=next(
                    t for t in peer.getTransceivers() if t.sender==sender
                )
                codecs=RTCRtpSender.getCapabilities("video").codecs
                # H.264 preferred when both sides support it; software
                # encoder fallback is determined by aiortc capabilities.
                ordered=sorted(codecs,key=lambda c:0 if c.mimeType.lower()=="video/h264" else 1)
                if ordered:
                    transceiver.setCodecPreferences(ordered)

                if include_audio:
                    if "m=audio" not in sdp:
                        raise MediaUnavailable("Phone did not negotiate an audio track")
                    mic=LaptopMicrophoneTrack()
                    self._mic=mic
                    peer.addTrack(mic.track)

                    @peer.on("track")
                    def on_track(track):
                        if track.kind=="audio":
                            self._speaker_task=asyncio.create_task(self._play_speaker(track))

                @peer.on("connectionstatechange")
                async def on_change():
                    if peer.connectionState in ("failed","closed"):
                        # Defer cleanup to avoid deadlock from the RTC callback.
                        asyncio.create_task(self.close(device_id))

                await peer.setRemoteDescription(RTCSessionDescription(sdp=sdp,type="offer"))
                answer=await peer.createAnswer()
                await peer.setLocalDescription(answer)
                return {
                    "type":peer.localDescription.type,
                    "sdp":peer.localDescription.sdp,
                    "audio_enabled":bool(include_audio),
                    "media_encryption":"DTLS-SRTP",
                }
            except Exception:
                await self._close_unlocked()
                raise

    async def snapshot_jpeg(self) -> bytes | None:
        holder=self._video
        if holder is None or holder.track._last is None:
            return None
        import cv2
        image=holder.track._last.copy()
        ok,encoded=await asyncio.to_thread(
            cv2.imencode, ".jpg", image,
            [int(cv2.IMWRITE_JPEG_QUALITY),80]
        )
        return encoded.tobytes() if ok else None

    async def _play_speaker(self, track) -> None:
        """Phone→laptop audio. Never invoke without explicit intercom request."""
        import av
        import sounddevice as sd
        stream=sd.RawOutputStream(samplerate=48000,channels=1,dtype="int16")
        stream.start()
        try:
            resampler=av.AudioResampler(format="s16",layout="mono",rate=48000)
            while True:
                frame=await track.recv()
                for pcm in resampler.resample(frame):
                    data=bytes(pcm.planes[0])
                    await asyncio.to_thread(stream.write,data)
        except asyncio.CancelledError:
            raise
        except Exception:
            pass
        finally:
            stream.stop()
            stream.close()

    async def _close_unlocked(self) -> None:
        peer,self._peer=self._peer,None
        self._owner=None
        speaker,self._speaker_task=self._speaker_task,None
        if speaker is not None:
            speaker.cancel()
        for holder in (self._video,self._mic):
            if holder is not None:
                holder.track.stop()
        self._video=self._mic=None
        if peer is not None:
            await peer.close()

    async def close(self, device_id: str | None = None) -> None:
        async with self._lock:
            if device_id is None or device_id==self._owner:
                await self._close_unlocked()

    @property
    def active(self) -> bool:
        return self._peer is not None

"""Explicitly advertised, non-secret LAN presence for paired discovery.

mDNS does not authenticate a laptop. The Android client MUST match the TXT
fingerprint and desktop identifier against its locally pinned QR identity.
"""
from __future__ import annotations

import ipaddress
import socket
import threading
from typing import Any

SERVICE_TYPE = "_laptopwatch._tcp.local."


class LaptopWatchAdvertiser:
    def __init__(self, host: str, port: int, device_id: str, fingerprint: str, *, name: str = "LaptopWatch"):
        ip = ipaddress.ip_address(host)
        if ip.version != 4 or not ip.is_private or ip.is_unspecified:
            raise ValueError("mDNS host must be a private IPv4 address")
        if not 1 <= port <= 65535:
            raise ValueError("Invalid advertised port")
        if len(fingerprint) != 64 or any(c not in "0123456789abcdef" for c in fingerprint):
            raise ValueError("Invalid TLS fingerprint")
        import uuid
        uuid.UUID(device_id)
        self.host, self.port, self.device_id = str(ip), port, device_id
        self.fingerprint, self.name = fingerprint, name[:64]
        self._zc: Any = None
        self._info: Any = None
        self._lock = threading.RLock()

    def start(self) -> None:
        with self._lock:
            if self._zc is not None:
                return
            try:
                from zeroconf import Zeroconf, ServiceInfo
            except ImportError as exc:
                raise RuntimeError("mDNS requires the optional zeroconf package") from exc
            hostname = socket.gethostname().split(".")[0].lower()
            service_name = f"{self.device_id}.{SERVICE_TYPE}"
            properties = {
                "id": self.device_id,
                "name": self.name,
                "v": "1",
                "fp": self.fingerprint,
            }
            info = ServiceInfo(
                type_=SERVICE_TYPE,
                name=service_name,
                addresses=[socket.inet_aton(self.host)],
                port=self.port,
                properties=properties,
                server=f"{hostname}.local.",
            )
            zc = Zeroconf()
            try:
                zc.register_service(info, allow_name_change=False)
            except Exception:
                zc.close()
                raise
            self._info, self._zc = info, zc

    def stop(self) -> None:
        with self._lock:
            zc, info = self._zc, self._info
            self._zc = self._info = None
            if zc is not None:
                try:
                    if info is not None:
                        zc.unregister_service(info)
                finally:
                    zc.close()

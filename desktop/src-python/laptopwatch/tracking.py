"""Consent-gated GPS-receiver fixes, durable last-seen and reconnect queue.

A laptop without a real location provider returns no fix. A sleeping/offline
laptop cannot run this process. Any network sender must be separately paired,
TLS-pinned, explicitly opt-in, and configured by a trusted local user.
"""
from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
import json
import math
import os
from pathlib import Path
import socket
import sqlite3
import threading
import time
from typing import Callable


@dataclass(frozen=True)
class LocationFix:
    latitude: float
    longitude: float
    accuracy_m: float
    observed_at_ms: int
    provider: str = "gpsd-gnss"

    def validate(self, now_ms: int) -> None:
        if not all(math.isfinite(x) for x in (self.latitude, self.longitude, self.accuracy_m)):
            raise ValueError("non-finite location")
        if not -90 <= self.latitude <= 90 or not -180 <= self.longitude <= 180:
            raise ValueError("coordinates out of range")
        if not 0 < self.accuracy_m <= 100_000:
            raise ValueError("location accuracy unknown or impossible")
        if self.observed_at_ms < now_ms - 300_000 or self.observed_at_ms > now_ms + 30_000:
            raise ValueError("location fix stale or future-dated")
        if self.provider != "gpsd-gnss":
            raise ValueError("unverified location provider")


class GpsdReceiver:
    """Read an actual gpsd TPV report from a locally configured GNSS receiver."""
    def __init__(self, host: str = "127.0.0.1", port: int = 2947, timeout: float = 2.0):
        if host not in {"localhost", "127.0.0.1", "::1"}:
            raise ValueError("gpsd must be accessed on loopback")
        self.host, self.port, self.timeout = host, port, timeout

    @staticmethod
    def parse_tpv(raw: dict, *, now_ms: int) -> LocationFix | None:
        if raw.get("class") != "TPV" or not isinstance(raw.get("mode"), (int, float)) or raw.get("mode") < 2:
            return None
        try:
            lat, lon = float(raw["lat"]), float(raw["lon"])
            epx, epy = float(raw["epx"]), float(raw["epy"])
            # gpsd reports separate horizontal error estimates; report the
            # conservative combined uncertainty without inventing submeter GPS.
            accuracy = math.hypot(epx, epy)
            stamp = datetime.fromisoformat(str(raw["time"]).replace("Z", "+00:00"))
            if stamp.tzinfo is None:
                return None
            seen_ms = int(stamp.timestamp() * 1000)
            fix = LocationFix(lat, lon, accuracy, seen_ms)
            fix.validate(now_ms)
            return fix
        except (TypeError, ValueError, KeyError, OverflowError):
            return None

    def read_fix(self, *, now_ms: int | None = None) -> LocationFix | None:
        now_ms = now_ms if now_ms is not None else int(time.time() * 1000)
        try:
            with socket.create_connection((self.host, self.port), self.timeout) as sock:
                sock.settimeout(self.timeout)
                sock.sendall(b'?WATCH={"enable":true,"json":true};\n')
                # A bounded read prevents a malicious/stalled local socket
                # from exhausting memory or blocking the monitoring thread.
                with sock.makefile("rb") as stream:
                    for _ in range(30):
                        line = stream.readline(8193)
                        if not line or len(line) > 8192:
                            break
                        try:
                            obj = json.loads(line)
                        except (ValueError, UnicodeDecodeError):
                            continue
                        if not isinstance(obj, dict):
                            continue
                        result = self.parse_tpv(obj, now_ms=now_ms)
                        if result is not None:
                            return result
        except (OSError, TimeoutError):
            return None
        return None


class LastSeenStore:
    """Local, append-only position history with an unacknowledged sync queue."""
    def __init__(self, db: str | Path):
        db_path = Path(db)
        db_path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
        self._lock = threading.RLock()
        self._db = sqlite3.connect(str(db_path), check_same_thread=False, isolation_level=None, timeout=5)
        if os.name == "posix":
            db_path.chmod(0o600)
        self._db.execute("PRAGMA journal_mode=WAL")
        self._db.execute("CREATE TABLE IF NOT EXISTS fixes ("
                         "id INTEGER PRIMARY KEY AUTOINCREMENT, latitude REAL NOT NULL, "
                         "longitude REAL NOT NULL, accuracy_m REAL NOT NULL, "
                         "observed_at_ms INTEGER NOT NULL, provider TEXT NOT NULL, "
                         "acked_at_ms INTEGER)")
        self._db.execute("CREATE INDEX IF NOT EXISTS idx_fixes_pending ON fixes(acked_at_ms, id)")

    def add(self, fix: LocationFix, *, now_ms: int | None = None) -> int:
        now_ms = int(time.time() * 1000) if now_ms is None else now_ms
        fix.validate(now_ms)
        with self._lock:
            cursor = self._db.execute(
                "INSERT INTO fixes(latitude,longitude,accuracy_m,observed_at_ms,provider) VALUES (?,?,?,?,?)",
                (fix.latitude, fix.longitude, fix.accuracy_m, fix.observed_at_ms, fix.provider),
            )
            return int(cursor.lastrowid)

    def latest(self, *, now_ms: int | None = None) -> dict | None:
        now_ms = int(time.time() * 1000) if now_ms is None else now_ms
        with self._lock:
            row = self._db.execute(
                "SELECT id,latitude,longitude,accuracy_m,observed_at_ms,provider "
                "FROM fixes ORDER BY observed_at_ms DESC,id DESC LIMIT 1"
            ).fetchone()
        if row is None:
            return None
        id_, lat, lon, accuracy, seen, provider = row
        age_ms = max(0, now_ms - seen)
        return {"event_id": id_, "latitude": lat, "longitude": lon,
                "accuracy_meters": accuracy, "observed_at_ms": seen,
                "age_ms": age_ms, "is_recent_fix": age_ms <= 120_000,
                "is_live_fix": False,
                "provider": provider}

    def pending(self, limit: int = 100) -> list[tuple[int, LocationFix]]:
        with self._lock:
            rows = self._db.execute(
                "SELECT id,latitude,longitude,accuracy_m,observed_at_ms,provider "
                "FROM fixes WHERE acked_at_ms IS NULL ORDER BY id LIMIT ?", (max(1, min(limit, 100)),)
            ).fetchall()
        return [(row[0], LocationFix(*row[1:])) for row in rows]

    def ack(self, event_id: int, *, now_ms: int | None = None) -> None:
        now_ms = int(time.time() * 1000) if now_ms is None else now_ms
        with self._lock:
            self._db.execute("UPDATE fixes SET acked_at_ms=? WHERE id=? AND acked_at_ms IS NULL",
                             (now_ms, event_id))

    def close(self) -> None:
        with self._lock:
            self._db.close()


class TrackingService:
    """On-device receiver + offline queue; no cloud sender is implied or built-in."""
    def __init__(self, store: LastSeenStore, receiver: GpsdReceiver):
        self.store, self.receiver = store, receiver

    def refresh(self, *, now_ms: int | None = None) -> dict | None:
        fix = self.receiver.read_fix(now_ms=now_ms)
        if fix is None:
            return None
        current_ms = int(time.time() * 1000) if now_ms is None else now_ms
        if current_ms - fix.observed_at_ms > 30_000:
            return None
        self.store.add(fix, now_ms=now_ms)
        return {**self.store.latest(now_ms=now_ms), "is_live_fix": True}

    def on_secure_transport_available(self, sender: Callable[[int, LocationFix], bool]) -> int:
        """Hook for paired, TLS-pinned WAN sender upon network restoration.

        Sender MUST confirm durable remote receipt before returning True.
        False/errors leave records in queue; never transmit to arbitrary URL.
        """
        delivered = 0
        for event_id, fix in self.store.pending():
            try:
                if not sender(event_id, fix):
                    break
            except Exception:
                break
            self.store.ack(event_id)
            delivered += 1
        return delivered


class LocationPoller:
    """Opt-in GNSS sampling while process runs; never wakes a sleeping OS."""
    def __init__(self, tracking: TrackingService, *, interval_seconds: float = 30.0):
        if interval_seconds < 1.0:
            raise ValueError('Minimum location poll interval is one second')
        self.tracking = tracking
        self.interval_seconds = interval_seconds
        self._stop = threading.Event()
        self._thread: threading.Thread | None = None

    def start(self) -> None:
        if self._thread and self._thread.is_alive():
            return
        self._stop.clear()
        self._thread = threading.Thread(target=self._loop, name='LaptopWatch-GNSS', daemon=True)
        self._thread.start()

    def _loop(self) -> None:
        while not self._stop.is_set():
            try:
                self.tracking.refresh()
            except (OSError, ValueError, RuntimeError):
                # A missing receiver must not terminate the desktop application.
                pass
            self._stop.wait(self.interval_seconds)

    def stop(self) -> None:
        self._stop.set()
        if self._thread:
            self._thread.join(timeout=5)
        self._thread = None

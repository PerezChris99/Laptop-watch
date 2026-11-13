"""Explicitly paired Ed25519 control authentication with durable nonce replay protection.

This module is intentionally not connected to the legacy Flask server: that
server's insecure PIN protocol must be removed atomically when a new paired TLS
gateway and Android client are ready. Never expose the legacy server to WAN.
"""
from __future__ import annotations

import base64
from collections import defaultdict, deque
from hashlib import sha256
import json
import re
import sqlite3
import threading
import time
import uuid
from pathlib import Path
from typing import Mapping

from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey, Ed25519PublicKey

PROTOCOL_VERSION = "LW1"
CLOCK_SKEW_SECONDS = 60
NONCE_BYTES = 16
_PATH_RE = re.compile(r"^/api/[a-zA-Z0-9/_-]{1,180}$")
_B64_RE = re.compile(r"^[A-Za-z0-9_-]+$")


class AuthenticationError(Exception):
    """Caller must return a generic unauthorized response with no secret detail."""


def b64url(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).decode("ascii").rstrip("=")


def unb64url(text: str, expected_size: int) -> bytes:
    if not isinstance(text, str) or not _B64_RE.fullmatch(text) or len(text) > expected_size * 2:
        raise AuthenticationError("invalid base64 encoding")
    try:
        result = base64.b64decode(text + "=" * (-len(text) % 4), altchars=b"-_", validate=True)
    except (ValueError, base64.binascii.Error) as exc:
        raise AuthenticationError("invalid base64 encoding") from exc
    if len(result) != expected_size or b64url(result) != text:
        raise AuthenticationError("invalid encoded length")
    return result


def canonical_request(method: str, path: str, body: bytes, timestamp: int, nonce: str, device_id: str) -> bytes:
    """Strict signature transcript; requests with query strings are rejected.

    The gateway must pass the exact parsed path and raw request body, before
    any JSON normalization; never trust forwarded client identity headers.
    """
    method = method.upper()
    if method not in {"GET", "POST", "PUT", "DELETE"}:
        raise AuthenticationError("unsupported HTTP method")
    if not _PATH_RE.fullmatch(path) or "//" in path or ".." in path:
        raise AuthenticationError("invalid signed path")
    try:
        uuid.UUID(device_id)
    except (ValueError, TypeError) as exc:
        raise AuthenticationError("invalid device identifier") from exc
    unb64url(nonce, NONCE_BYTES)
    if not isinstance(body, bytes) or len(body) > 1_048_576:
        raise AuthenticationError("invalid body size")
    return (f"{PROTOCOL_VERSION}\n{method}\n{path}\n{sha256(body).hexdigest()}\n"
            f"{timestamp}\n{nonce}\n{device_id}\n").encode("ascii")


def sign_request(private_key: Ed25519PrivateKey, *, method: str, path: str, body: bytes,
                 device_id: str, nonce: str, timestamp: int) -> dict[str, str]:
    canonical = canonical_request(method, path, body, timestamp, nonce, device_id)
    return {
        "X-LaptopWatch-Device": device_id,
        "X-LaptopWatch-Nonce": nonce,
        "X-LaptopWatch-Timestamp": str(timestamp),
        "X-LaptopWatch-Signature": b64url(private_key.sign(canonical)),
    }


class PairingRegistry:
    """SQLite-backed key inventory and persistent replay defense.

    Pairing must be invoked ONLY by an authenticated local UI after physical
    desktop approval. No remote unauthenticated endpoint may call pair().
    """
    def __init__(self, path: Path | str):
        self.path = str(path)
        self._lock = threading.RLock()
        self._db = sqlite3.connect(self.path, check_same_thread=False, isolation_level=None, timeout=5)
        self._db.execute("PRAGMA busy_timeout=5000")
        self._db.execute("PRAGMA journal_mode=WAL")
        with self._lock:
            self._db.execute("CREATE TABLE IF NOT EXISTS paired_devices (id TEXT PRIMARY KEY, pubkey BLOB NOT NULL, revoked INTEGER NOT NULL DEFAULT 0)")
            self._db.execute("CREATE TABLE IF NOT EXISTS used_nonces (device_id TEXT NOT NULL, nonce TEXT NOT NULL, created_at INTEGER NOT NULL, PRIMARY KEY(device_id, nonce))")
            self._db.execute("CREATE INDEX IF NOT EXISTS idx_nonce_time ON used_nonces(created_at)")

    def pair(self, device_id: str, public_key: bytes, *, locally_approved: bool = False) -> None:
        if not locally_approved:
            raise PermissionError("pairing requires explicit local user approval")
        try:
            uuid.UUID(device_id)
            Ed25519PublicKey.from_public_bytes(public_key)
        except (ValueError, TypeError) as exc:
            raise ValueError("invalid pairing identity") from exc
        with self._lock:
            # Never overwrite an existing identity without deliberate unpair.
            self._db.execute("INSERT INTO paired_devices(id,pubkey,revoked) VALUES (?,?,0)", (device_id, public_key))

    def revoke(self, device_id: str) -> None:
        with self._lock:
            self._db.execute("UPDATE paired_devices SET revoked=1 WHERE id=?", (device_id,))

    def get_public_key(self, device_id: str) -> Ed25519PublicKey | None:
        with self._lock:
            row = self._db.execute("SELECT pubkey FROM paired_devices WHERE id=? AND revoked=0", (device_id,)).fetchone()
        return Ed25519PublicKey.from_public_bytes(row[0]) if row else None

    def claim_nonce(self, device_id: str, nonce: str, now: int) -> bool:
        """Exclusive SQLite insert ensures replay stays rejected across restarts."""
        with self._lock:
            try:
                self._db.execute("BEGIN IMMEDIATE")
                self._db.execute("DELETE FROM used_nonces WHERE created_at < ?", (now - 2 * CLOCK_SKEW_SECONDS - 5,))
                self._db.execute("INSERT INTO used_nonces(device_id,nonce,created_at) VALUES (?,?,?)", (device_id, nonce, now))
                self._db.execute("COMMIT")
                return True
            except sqlite3.IntegrityError:
                self._db.execute("ROLLBACK")
                return False
            except Exception:
                self._db.execute("ROLLBACK")
                raise

    def close(self) -> None:
        with self._lock:
            self._db.close()


class SlidingWindowLimiter:
    def __init__(self, *, max_attempts: int = 40, period: int = 60):
        self.max_attempts, self.period = max_attempts, period
        self._lock = threading.Lock()
        self._windows: dict[str, deque[float]] = defaultdict(deque)

    def allow(self, key: str, now: float) -> bool:
        with self._lock:
            values = self._windows[key]
            while values and values[0] <= now - self.period:
                values.popleft()
            if len(values) >= self.max_attempts:
                return False
            values.append(now)
            return True


class SignedRequestVerifier:
    def __init__(self, registry: PairingRegistry, limiter: SlidingWindowLimiter | None = None):
        self.registry = registry
        self.limiter = limiter if limiter is not None else SlidingWindowLimiter()

    def verify(self, method: str, path: str, body: bytes, headers: Mapping[str, str], *,
               client_ip: str, now: int | None = None) -> str:
        now = int(time.time()) if now is None else now
        # client_ip is from socket peer; never accept X-Forwarded-For blindly.
        if not self.limiter.allow(client_ip, float(now)):
            raise AuthenticationError("rate limited")
        try:
            device_id = headers["X-LaptopWatch-Device"]
            nonce = headers["X-LaptopWatch-Nonce"]
            timestamp = int(headers["X-LaptopWatch-Timestamp"])
            signature = unb64url(headers["X-LaptopWatch-Signature"], 64)
        except (KeyError, TypeError, ValueError) as exc:
            raise AuthenticationError("invalid signature headers") from exc
        if abs(now - timestamp) > CLOCK_SKEW_SECONDS:
            raise AuthenticationError("expired signature")
        pubkey = self.registry.get_public_key(device_id)
        if pubkey is None:
            raise AuthenticationError("unknown or revoked client")
        message = canonical_request(method, path, body, timestamp, nonce, device_id)
        try:
            pubkey.verify(signature, message)
        except InvalidSignature as exc:
            raise AuthenticationError("signature verification failed") from exc
        if not self.registry.claim_nonce(device_id, nonce, now):
            raise AuthenticationError("replayed request")
        return device_id

"""Signed, hash-chained local audit ledger. Requires protected key + external anchors."""
from __future__ import annotations

from hashlib import sha256
import json
import os
from pathlib import Path
import threading
import time
from typing import Any

from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey, Ed25519PublicKey

from .security import b64url, unb64url


class AuditIntegrityError(RuntimeError):
    pass


def _json(data: dict[str, Any]) -> bytes:
    return json.dumps(data, ensure_ascii=False, sort_keys=True, separators=(",", ":"), allow_nan=False).encode("utf8")


class SignedAuditLog:
    """No raw secrets, frames or audio in event fields; exports need user approval."""
    def __init__(self, path: Path | str, private_key: Ed25519PrivateKey):
        self.path = Path(path)
        self.private_key = private_key
        self._lock = threading.RLock()
        self.path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
        if self.path.exists():
            self._head = self.verify(self.path, self.private_key.public_key())
        else:
            self._head = "0" * 64

    @staticmethod
    def verify(path: Path | str, pubkey: Ed25519PublicKey) -> str:
        previous = "0" * 64
        try:
            with open(path, "rb") as handle:
                for line in handle:
                    document = json.loads(line)
                    if set(document) != {"event", "prev", "sha256", "signature"} or document["prev"] != previous:
                        raise AuditIntegrityError("broken audit chain")
                    payload = _json({"event": document["event"], "prev": document["prev"]})
                    digest = sha256(payload).hexdigest()
                    if digest != document["sha256"]:
                        raise AuditIntegrityError("audit record modified")
                    pubkey.verify(unb64url(document["signature"], 64), bytes.fromhex(digest))
                    previous = digest
        except (ValueError, KeyError, TypeError, InvalidSignature) as exc:
            raise AuditIntegrityError("audit verification failed") from exc
        return previous

    def append(self, action: str, device_id: str, *, result: str, metadata: dict[str, str] | None = None) -> str:
        if action not in {"lock", "alarm", "record_start", "record_stop", "config", "pair", "unpair", "auth_reject"}:
            raise ValueError("action not allowlisted")
        if result not in {"success", "failure", "denied"}:
            raise ValueError("invalid result")
        metadata = {} if metadata is None else metadata
        if len(metadata) > 16 or any(not isinstance(k, str) or not isinstance(v, str) or len(k) > 64 or len(v) > 256 for k, v in metadata.items()):
            raise ValueError("invalid metadata size")
        # Never include credential, key, token, raw body, audio or video in audit metadata.
        forbidden = ("token", "pin", "password", "secret", "authorization", "key", "audio", "video", "image")
        if any(any(term in k.lower() for term in forbidden) for k in metadata):
            raise ValueError("metadata may not contain sensitive fields")
        with self._lock:
            event = {"action": action, "device": device_id, "result": result, "timestamp_ms": time.time_ns() // 1_000_000, "metadata": metadata}
            body = {"event": event, "prev": self._head}
            digest = sha256(_json(body)).hexdigest()
            line = _json({**body, "sha256": digest, "signature": b64url(self.private_key.sign(bytes.fromhex(digest)))}) + b"\n"
            fd = os.open(self.path, os.O_APPEND | os.O_CREAT | os.O_WRONLY, 0o600)
            try:
                with os.fdopen(fd, "ab", buffering=0) as handle:
                    handle.write(line)
                    os.fsync(handle.fileno())
            except Exception:
                # Never advance trusted head if durable write failed.
                raise
            self._head = digest
            return digest

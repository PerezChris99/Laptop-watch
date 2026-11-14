"""Explicit local-approval pairing over a short-lived QR challenge.

QR is authenticated by a device identity key to prevent alteration; scanning
it in person bootstraps the self-signed TLS certificate pin. A mobile phone
proves possession of its Ed25519 key before the laptop displays approval.
"""
from __future__ import annotations

import ipaddress
from hashlib import sha256
from pathlib import Path
import os
import secrets
import sqlite3
import threading
import time
from urllib.parse import urlencode
import uuid

from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey, Ed25519PublicKey
from cryptography.hazmat.primitives.serialization import Encoding, PublicFormat

from .security import PairingRegistry, AuthenticationError, b64url, unb64url

PAIR_TTL_SECONDS = 120


def qr_transcript(laptop_id: str, host: str, port: int, pubkey: str, fp: str,
                  challenge: str, expires: int) -> bytes:
    """Canonical ASCII format shared with the Kotlin QR parser."""
    return f'LWPAIR1\n{laptop_id}\n{host}\n{port}\n{pubkey}\n{fp}\n{challenge}\n{expires}\n'.encode('ascii')


def phone_transcript(device_id: str, pubkey: str, challenge: str) -> bytes:
    return f'LWPAIR-PROOF1\n{device_id}\n{pubkey}\n{challenge}\n'.encode('ascii')


def validate_host(host: str) -> str:
    """Initial pairing is LAN-only; never direct an enrolled client to a web URL."""
    try:
        ip = ipaddress.ip_address(host)
    except ValueError as exc:
        raise ValueError('QR pairing host must be a numeric local IP') from exc
    if ip.version != 4 or not (ip.is_private or ip.is_loopback):
        raise ValueError('QR pairing host must be a private IPv4 address')
    if ip.is_unspecified or ip.is_multicast or ip.is_link_local or ip.is_reserved:
        raise ValueError('QR pairing host is not a usable LAN address')
    return str(ip)


class PairingCoordinator:
    def __init__(self, path: str | Path, registry: PairingRegistry):
        self.registry = registry
        self._mutex = threading.RLock()
        self._db = sqlite3.connect(str(path), timeout=5, isolation_level=None, check_same_thread=False)
        self._db.execute('PRAGMA journal_mode=WAL')
        self._db.execute('CREATE TABLE IF NOT EXISTS pairing_sessions ('
                         'challenge TEXT PRIMARY KEY, expires INTEGER NOT NULL, laptop_id TEXT NOT NULL)')
        self._db.execute('CREATE TABLE IF NOT EXISTS pending_pairs ('
                         'request_id TEXT PRIMARY KEY, phone_id TEXT NOT NULL UNIQUE, '
                         'phone_pubkey BLOB NOT NULL, phone_label TEXT NOT NULL, '
                         'challenge TEXT NOT NULL, received INTEGER NOT NULL)')

    def close(self) -> None:
        with self._mutex:
            self._db.close()

    def create_qr(self, *, host: str, port: int, laptop_id: str, public_key: bytes,
                  fingerprint_hex: str, signing_key: Ed25519PrivateKey, now: int | None = None) -> str:
        now = int(time.time()) if now is None else now
        host = validate_host(host)
        uuid.UUID(laptop_id)
        if not 1024 <= port <= 65535:
            raise ValueError('invalid gateway port')
        if len(fingerprint_hex) != 64 or any(c not in '0123456789abcdef' for c in fingerprint_hex):
            raise ValueError('invalid TLS fingerprint')
        if len(public_key) != 32:
            raise ValueError('invalid desktop public key')
        challenge = b64url(secrets.token_bytes(16))
        expires = now + PAIR_TTL_SECONDS
        public_b64 = b64url(public_key)
        signature = b64url(signing_key.sign(qr_transcript(laptop_id, host, port,
                        public_b64, fingerprint_hex, challenge, expires)))
        with self._mutex:
            self._db.execute('DELETE FROM pairing_sessions')
            self._db.execute('DELETE FROM pending_pairs')
            self._db.execute('INSERT INTO pairing_sessions VALUES (?, ?, ?)',
                             (challenge, expires, laptop_id))
        values = [('v', '1'), ('id', laptop_id), ('host', host), ('port', str(port)),
                  ('pk', public_b64), ('fp', fingerprint_hex), ('nonce', challenge),
                  ('exp', str(expires)), ('sig', signature)]
        return 'laptopwatch://pair?' + urlencode(values)

    def active(self, *, now: int | None = None) -> bool:
        now = int(time.time()) if now is None else now
        with self._mutex:
            return self._db.execute('SELECT 1 FROM pairing_sessions WHERE expires >= ? LIMIT 1',
                                    (now,)).fetchone() is not None

    def request(self, *, device_id: str, pubkey: str, challenge: str, proof: str,
                label: str, now: int | None = None) -> str:
        now = int(time.time()) if now is None else now
        try:
            uuid.UUID(device_id)
            key_bytes = unb64url(pubkey, 32)
            signature = unb64url(proof, 64)
            unb64url(challenge, 16)
            if not (1 <= len(label) <= 48) or not label.isprintable():
                raise ValueError('invalid device label')
            Ed25519PublicKey.from_public_bytes(key_bytes).verify(
                signature, phone_transcript(device_id, pubkey, challenge))
        except (ValueError, TypeError, InvalidSignature, AuthenticationError) as exc:
            raise AuthenticationError('invalid pairing proof') from exc
        with self._mutex:
            valid = self._db.execute('SELECT 1 FROM pairing_sessions WHERE challenge=? AND expires>=?',
                                     (challenge, now)).fetchone()
            if not valid:
                raise AuthenticationError('pairing invitation is closed or expired')
            if self.registry.get_public_key(device_id) is not None:
                raise AuthenticationError('already paired')
            request_id = str(uuid.uuid4())
            try:
                self._db.execute('INSERT INTO pending_pairs VALUES (?, ?, ?, ?, ?, ?)',
                                 (request_id, device_id, key_bytes, label, challenge, now))
            except sqlite3.IntegrityError as exc:
                raise AuthenticationError('duplicate request') from exc
            return request_id

    def pending(self, *, now: int | None = None) -> list[dict[str, str]]:
        now = int(time.time()) if now is None else now
        with self._mutex:
            rows = self._db.execute('SELECT p.request_id, p.phone_id, p.phone_pubkey, p.phone_label '
                         'FROM pending_pairs AS p JOIN pairing_sessions AS s ON p.challenge=s.challenge '
                         'WHERE s.expires>=? ORDER BY p.received', (now,)).fetchall()
        return [{'request_id': r[0], 'device_id': r[1],
                 'public_key_sha256': sha256(r[2]).hexdigest(), 'label': r[3]}
                for r in rows]

    def approve(self, request_id: str, *, now: int | None = None) -> str:
        now = int(time.time()) if now is None else now
        uuid.UUID(request_id)
        with self._mutex:
            row = self._db.execute('SELECT p.phone_id, p.phone_pubkey FROM pending_pairs AS p '
                  'JOIN pairing_sessions AS s ON p.challenge=s.challenge '
                  'WHERE p.request_id=? AND s.expires>=?', (request_id, now)).fetchone()
            if row is None:
                raise AuthenticationError('pending pairing expired')
            phone_id, public_key = row
            self.registry.pair(phone_id, public_key, locally_approved=True)
            self._db.execute('DELETE FROM pending_pairs WHERE request_id=?', (request_id,))
            self._db.execute('DELETE FROM pairing_sessions')
            return phone_id

    def reject(self, request_id: str) -> None:
        uuid.UUID(request_id)
        with self._mutex:
            self._db.execute('DELETE FROM pending_pairs WHERE request_id=?', (request_id,))

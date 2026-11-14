"""Byte-for-byte interop fixtures from the existing Python verifier.

The deterministic Ed25519 seed is TEST-ONLY; never deploy or import it outside
these fixtures. Companion must reject any changed request body/signature.
"""
from __future__ import annotations

import json
from pathlib import Path

import pytest
from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from cryptography.hazmat.primitives.serialization import Encoding, PublicFormat

from laptopwatch.security import b64url, canonical_request, sign_request, unb64url
from laptopwatch.pairing import qr_transcript, phone_transcript


VECTORS = json.loads((
    Path(__file__).resolve().parents[3] / "shared" /
    "protocol-vectors" / "ed25519-v1.json"
).read_text(encoding="utf-8"))


@pytest.mark.parametrize("vector", VECTORS["signed_requests"])
def test_python_signed_interop_vector(vector):
    key = Ed25519PrivateKey.from_private_bytes(bytes.fromhex(VECTORS["test_seed_hex"]))
    public = key.public_key().public_bytes(Encoding.Raw, PublicFormat.Raw)
    assert b64url(public) == VECTORS["public_key_b64url"]
    message = canonical_request(
        vector["method"], vector["path"], vector["body_utf8"].encode("utf-8"),
        vector["timestamp"], vector["nonce"], vector["device_id"]
    )
    assert message == vector["canonical_utf8"].encode("ascii")
    headers = sign_request(
        key, method=vector["method"], path=vector["path"],
        body=vector["body_utf8"].encode("utf-8"),
        device_id=vector["device_id"], nonce=vector["nonce"],
        timestamp=vector["timestamp"]
    )
    assert headers["X-LaptopWatch-Signature"] == vector["signature_b64url"]
    signature = unb64url(vector["signature_b64url"], 64)
    key.public_key().verify(signature, message)
    with pytest.raises(InvalidSignature):
        key.public_key().verify(signature, message + b"tampered")


def test_qr_and_phone_proof_interop_vectors():
    key = Ed25519PrivateKey.from_private_bytes(bytes.fromhex(VECTORS["test_seed_hex"]))
    qr = VECTORS["pairing_qr"]
    message = qr_transcript(
        qr["laptop_id"], qr["host"], qr["port"], qr["public_key_b64url"],
        qr["tls_fingerprint_hex"], qr["challenge"], qr["expires"]
    )
    assert message == qr["canonical_utf8"].encode("ascii")
    assert b64url(key.sign(message)) == qr["signature_b64url"]
    proof = VECTORS["pairing_proof"]
    proof_message = phone_transcript(
        proof["device_id"], proof["phone_public_key_b64url"], proof["challenge"]
    )
    assert proof_message == proof["canonical_utf8"].encode("ascii")
    assert b64url(key.sign(proof_message)) == proof["signature_b64url"]

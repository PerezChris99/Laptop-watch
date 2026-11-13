"""Manual development runner for the secure gateway; not a packaged desktop UI."""
from __future__ import annotations

import argparse
import base64
import json
from pathlib import Path
import sys
import uuid

from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PublicKey

from .audit import SignedAuditLog
from .controller import NativeController
from .gateway import GatewayState, create_app
from .identity import initialize_identity, get_audit_key
from .security import PairingRegistry, SignedRequestVerifier, unb64url


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description='LaptopWatch secure gateway developer CLI')
    parser.add_argument('--state-dir', type=Path, default=Path.home() / '.laptopwatch-dev')
    sub = parser.add_subparsers(dest='command', required=True)
    sub.add_parser('init', help='create local identities (development only)')
    pair = sub.add_parser('pair', help='local, physical approval only')
    pair.add_argument('--device-id', required=True)
    pair.add_argument('--public-key-b64url', required=True)
    revoke = sub.add_parser('revoke', help='revoke a previously paired phone')
    revoke.add_argument('--device-id', required=True)
    serve = sub.add_parser('serve', help='HTTPS signed-control gateway')
    serve.add_argument('--port', type=int, default=8765)
    serve.add_argument('--listen-lan', action='store_true')
    args = parser.parse_args(argv)
    state_dir = args.state_dir
    if args.command == 'init':
        print(json.dumps(initialize_identity(state_dir), indent=2))
        return 0
    if not (state_dir / 'tls_cert.pem').is_file():
        parser.error('initialize local identity first: laptopwatch --state-dir PATH init')
    identity = initialize_identity(state_dir)
    registry = PairingRegistry(state_dir / 'paired.db')
    try:
        if args.command == 'pair':
            try:
                uuid.UUID(args.device_id)
                pub = unb64url(args.public_key_b64url, 32)
                Ed25519PublicKey.from_public_bytes(pub)
            except Exception:
                parser.error('invalid device id or public key')
            print('Physical approval required on this laptop. Confirm phone identity out of band.')
            if input('Type PAIR to approve this phone: ').strip() != 'PAIR':
                print('Pairing cancelled')
                return 1
            registry.pair(args.device_id, pub, locally_approved=True)
            SignedAuditLog(state_dir / 'audit.jsonl', get_audit_key(state_dir)).append('pair', args.device_id, result='success')
            print('Paired successfully. The phone must also pin the TLS fingerprint:', identity['tls_fingerprint_sha256'])
            return 0
        if args.command == 'revoke':
            registry.revoke(args.device_id)
            SignedAuditLog(state_dir / 'audit.jsonl', get_audit_key(state_dir)).append('unpair', args.device_id, result='success')
            print('Phone revoked')
            return 0
        if args.command == 'serve':
            if not 1 <= args.port <= 65535:
                parser.error('port out of range')
            host = '0.0.0.0' if args.listen_lan else '127.0.0.1'
            if args.listen_lan:
                # Public listener requires a physically approved, explicitly paired phone.
                if not registry._db.execute('SELECT 1 FROM paired_devices WHERE revoked=0 LIMIT 1').fetchone():
                    parser.error('LAN listener requires at least one locally approved paired phone')
                print('LAN access enabled: only signed requests accepted. Pin TLS fingerprint on phone.')
            print('TLS certificate SHA-256 fingerprint:', identity['tls_fingerprint_sha256'])
            from uvicorn import run
            app = create_app(GatewayState(SignedRequestVerifier(registry),
                   SignedAuditLog(state_dir / 'audit.jsonl', get_audit_key(state_dir)), NativeController()))
            run(app, host=host, port=args.port,
                ssl_keyfile=str(state_dir / 'tls_key.pem'),
                ssl_certfile=str(state_dir / 'tls_cert.pem'),
                access_log=False, proxy_headers=False, server_header=False)
            return 0
    finally:
        registry.close()
    return 2


if __name__ == '__main__':
    sys.exit(main())

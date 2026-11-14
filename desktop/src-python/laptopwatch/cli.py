"""Manual development runner for the secure gateway; not a packaged desktop UI."""
from __future__ import annotations

import argparse
import base64
import json
from pathlib import Path
import sys
import uuid

from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PublicKey
from cryptography.hazmat.primitives.serialization import Encoding, PublicFormat

from .audit import SignedAuditLog
from .evidence import EvidenceRecorder
from .tracking import GpsdReceiver, LastSeenStore, TrackingService, LocationPoller
from .controller import NativeController
from .gateway import GatewayState, create_app
from .identity import initialize_identity, get_audit_key
from .pairing import PairingCoordinator
from .security import PairingRegistry, SignedRequestVerifier, unb64url


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description='LaptopWatch secure gateway developer CLI')
    parser.add_argument('--state-dir', type=Path, default=Path.home() / '.laptopwatch-dev')
    sub = parser.add_subparsers(dest='command', required=True)
    sub.add_parser('init', help='create local identities (development only)')
    pair_code = sub.add_parser('pair-code', help='generate a two-minute signed QR invitation')
    pair_code.add_argument('--host', required=True, help='numerical private LAN IP of this laptop')
    pair_code.add_argument('--port', type=int, default=8765)
    sub.add_parser('pending', help='list phone enrollment requests awaiting approval')
    approve = sub.add_parser('approve', help='approve pairing from local desktop UI')
    approve.add_argument('--request-id', required=True)
    reject = sub.add_parser('reject', help='reject pairing from local desktop UI')
    reject.add_argument('--request-id', required=True)
    sub.add_parser('devices', help='list paired phone ids')

    pair = sub.add_parser('pair', help='local, physical approval only')
    pair.add_argument('--device-id', required=True)
    pair.add_argument('--public-key-b64url', required=True)
    revoke = sub.add_parser('revoke', help='revoke a previously paired phone')
    revoke.add_argument('--device-id', required=True)
    serve = sub.add_parser('serve', help='HTTPS signed-control gateway')
    serve.add_argument('--port', type=int, default=8765)
    serve.add_argument('--listen-lan', action='store_true')
    serve.add_argument('--enable-gpsd', action='store_true',
                       help='Opt in to reading a locally configured GNSS receiver via gpsd')
    args = parser.parse_args(argv)
    state_dir = args.state_dir
    if args.command == 'init':
        print(json.dumps(initialize_identity(state_dir), indent=2))
        return 0
    if not (state_dir / 'tls_cert.pem').is_file():
        parser.error('initialize local identity first: laptopwatch --state-dir PATH init')
    identity = initialize_identity(state_dir)
    registry = PairingRegistry(state_dir / 'paired.db')
    pairing = PairingCoordinator(state_dir / 'pairing.db', registry)
    try:
        if args.command == 'pair-code':
            desktop_key = get_audit_key(state_dir)
            desktop_id_path = state_dir / 'desktop_id'
            if desktop_id_path.exists():
                laptop_id = desktop_id_path.read_text(encoding='ascii').strip()
                uuid.UUID(laptop_id)
            else:
                laptop_id = str(uuid.uuid4())
                with desktop_id_path.open('x', encoding='ascii') as handle:
                    handle.write(laptop_id)
            public = desktop_key.public_key().public_bytes(Encoding.Raw, PublicFormat.Raw)
            uri = pairing.create_qr(host=args.host, port=args.port, laptop_id=laptop_id,
                                    public_key=public, fingerprint_hex=identity['tls_fingerprint_sha256'],
                                    signing_key=desktop_key)
            print(json.dumps({'uri':uri, 'expires_seconds':120}))
            return 0
        if args.command == 'pending':
            print(json.dumps(pairing.pending()))
            return 0
        if args.command == 'approve':
            phone_id = pairing.approve(args.request_id)
            SignedAuditLog(state_dir / 'audit.jsonl', get_audit_key(state_dir)).append(
                'pair', phone_id, result='success')
            print(json.dumps({'paired_device':phone_id}))
            return 0
        if args.command == 'reject':
            pairing.reject(args.request_id)
            print('Rejected')
            return 0
        if args.command == 'devices':
            phones = registry._db.execute('SELECT id,revoked FROM paired_devices ORDER BY id').fetchall()
            print(json.dumps([{'id':p[0], 'revoked':bool(p[1])} for p in phones]))
            return 0
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
                if not registry._db.execute('SELECT 1 FROM paired_devices WHERE revoked=0 LIMIT 1').fetchone() and not pairing.active():
                    parser.error('LAN listener requires an active QR pairing window or a locally approved phone')
                print('LAN access enabled: only signed requests accepted. Pin TLS fingerprint on phone.')
            print('TLS certificate SHA-256 fingerprint:', identity['tls_fingerprint_sha256'])
            from uvicorn import run
            evidence = EvidenceRecorder(state_dir / 'evidence')
            location_store = LastSeenStore(state_dir / 'last_seen.db') if args.enable_gpsd else None
            tracking = TrackingService(location_store, GpsdReceiver()) if location_store is not None else None
            app = create_app(GatewayState(
                SignedRequestVerifier(registry),
                SignedAuditLog(state_dir / 'audit.jsonl', get_audit_key(state_dir)),
                NativeController(),
                evidence=evidence,
                tracking=tracking,
                pairing=pairing,
            ))
            poller = LocationPoller(tracking) if tracking is not None else None
            if poller is not None:
                poller.start()
            try:
                run(app, host=host, port=args.port,
                    ssl_keyfile=str(state_dir / 'tls_key.pem'),
                    ssl_certfile=str(state_dir / 'tls_cert.pem'),
                    access_log=False, proxy_headers=False, server_header=False)
            finally:
                if poller is not None:
                    poller.stop()
                evidence.stop_if_recording()
                if location_store is not None:
                    location_store.close()
            return 0
    finally:
        pairing.close()
        registry.close()
    return 2


if __name__ == '__main__':
    sys.exit(main())

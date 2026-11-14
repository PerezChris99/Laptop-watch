"""Manual development runner for the secure gateway; not a packaged desktop UI."""
from __future__ import annotations

import argparse
import base64
import json
import os
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
from .discovery import LaptopWatchAdvertiser
from .remote_location import SignedLocationSender, RemoteLocationPoller
from .webrtc import RtcController
from .incidents import IncidentStore, PersonCameraGuard
from .person_detector import YoloxNanoDetector, MotionPersonGate, DetectorUnavailable
from .remote_incidents import SignedIncidentSender, RemoteIncidentPoller, IncidentDeliveryCursor
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
    sub.add_parser('incidents', help='list recent verified person incidents')
    sub.add_parser('clear-incidents', help='delete local incident metadata (not media)')

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
    serve.add_argument('--relay-url', default=None,
                       help='Explicit opt-in HTTPS last-seen relay (requires --enable-gpsd)')
    serve.add_argument('--stun-url', default=None, help='Optional STUN host for WAN ICE')
    serve.add_argument('--turn-url', default=None, help='Optional owner-hosted TURN URL')
    serve.add_argument('--turn-username', default=None)
    serve.add_argument('--turn-password-env', default='LAPTOPWATCH_TURN_PASSWORD',
                       help='Name of an environment variable holding the TURN credential')
    serve.add_argument('--person-model', type=Path, default=None,
                       help='Optional user-supplied, hash-verified person classifier (ONNX)')
    serve.add_argument('--person-model-sha256', default=None,
                       help='Required checksum for the explicitly installed ONNX model')
    serve.add_argument('--person-confidence', type=float, default=0.60)
    serve.add_argument('--incident-retention-days', type=int, default=30)
    serve.add_argument('--incident-relay-url', default=None,
                       help='Opt-in encrypted person-event relay; requires --person-model')
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
            (state_dir / 'pairing_host').write_text(args.host, encoding='ascii')
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
        if args.command in ('incidents', 'clear-incidents'):
            store = IncidentStore(state_dir / 'incidents.db')
            try:
                if args.command == 'incidents':
                    print(json.dumps(store.recent(limit=100)))
                else:
                    print(json.dumps({'deleted':store.clear()}))
            finally:
                store.close()
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
            if args.relay_url and not args.enable_gpsd:
                parser.error('--relay-url requires real GNSS consent via --enable-gpsd')
            if args.incident_relay_url and args.person_model is None:
                parser.error('--incident-relay-url requires a verified person model')
            host = '0.0.0.0' if args.listen_lan else '127.0.0.1'
            if args.listen_lan:
                # Public listener requires a physically approved, explicitly paired phone.
                if not registry._db.execute('SELECT 1 FROM paired_devices WHERE revoked=0 LIMIT 1').fetchone() and not pairing.active():
                    parser.error('LAN listener requires an active QR pairing window or a locally approved phone')
                print('LAN access enabled: only signed requests accepted. Pin TLS fingerprint on phone.')
            print('TLS certificate SHA-256 fingerprint:', identity['tls_fingerprint_sha256'])
            from uvicorn import run
            evidence = EvidenceRecorder(state_dir / 'evidence')
            native = NativeController()
            audit = SignedAuditLog(state_dir / 'audit.jsonl', get_audit_key(state_dir))
            incidents = IncidentStore(state_dir / 'incidents.db',
                                      retention_days=args.incident_retention_days)
            guard = None
            if args.person_model is not None:
                if args.person_model_sha256 is None:
                    parser.error('--person-model requires --person-model-sha256')
                try:
                    model = YoloxNanoDetector(args.person_model,
                        required_sha256=args.person_model_sha256,
                        confidence=args.person_confidence)
                    guard = PersonCameraGuard(MotionPersonGate(model), incidents, evidence,
                                              audit=audit)
                    guard.alarm_controller = native
                except (DetectorUnavailable, ValueError) as exc:
                    parser.error('Person detector unavailable: ' + str(exc))
            location_store = LastSeenStore(state_dir / 'last_seen.db') if args.enable_gpsd else None
            tracking = TrackingService(location_store, GpsdReceiver()) if location_store is not None else None
            rtc = None
            try:
                import aiortc
                rtc = RtcController(
                    stun_url=args.stun_url,
                    turn_url=args.turn_url,
                    turn_username=args.turn_username,
                    turn_password=os.getenv(args.turn_password_env),
                )
            except ImportError:
                print('WebRTC module not installed: still-frame fallback only')
            app = create_app(GatewayState(
                SignedRequestVerifier(registry),
                audit,
                native,
                evidence=evidence,
                tracking=tracking,
                pairing=pairing,
                rtc=rtc,
                incidents=incidents,
                guard=guard,
            ))
            incident_cursor = None
            incident_poller = None
            if args.incident_relay_url:
                desktop_id_file = state_dir / 'desktop_id'
                if not desktop_id_file.is_file():
                    parser.error('Pair with a phone before enabling remote incident alerts')
                incident_cursor = IncidentDeliveryCursor(state_dir / 'incident_delivery.db')
                incident_sender = SignedIncidentSender(
                    args.incident_relay_url,
                    desktop_id_file.read_text(encoding='ascii').strip(),
                    get_audit_key(state_dir),
                )
                incident_poller = RemoteIncidentPoller(incidents, incident_cursor, incident_sender)
            poller = LocationPoller(tracking) if tracking is not None else None
            remote_poller = None
            if args.relay_url:
                desktop_id_path = state_dir / 'desktop_id'
                if not desktop_id_path.exists():
                    parser.error('Generate and approve local laptop pairing before enabling a relay')
                sender = SignedLocationSender(
                    args.relay_url,
                    desktop_id_path.read_text(encoding='ascii').strip(),
                    get_audit_key(state_dir),
                )
                remote_poller = RemoteLocationPoller(tracking, sender)
            mdns = None
            if args.listen_lan:
                host_file = state_dir / 'pairing_host'
                id_file = state_dir / 'desktop_id'
                if host_file.exists() and id_file.exists():
                    mdns = LaptopWatchAdvertiser(
                        host_file.read_text(encoding='ascii').strip(), args.port,
                        id_file.read_text(encoding='ascii').strip(),
                        identity['tls_fingerprint_sha256'],
                    )
                    try:
                        mdns.start()
                    except (RuntimeError, OSError) as err:
                        print('mDNS unavailable, use the pairing QR:', str(err))
                        mdns = None
            if poller is not None:
                poller.start()
            if remote_poller is not None:
                remote_poller.start()
            if incident_poller is not None:
                incident_poller.start()
            try:
                run(app, host=host, port=args.port,
                    ssl_keyfile=str(state_dir / 'tls_key.pem'),
                    ssl_certfile=str(state_dir / 'tls_cert.pem'),
                    access_log=False, proxy_headers=False, server_header=False)
            finally:
                if incident_poller is not None:
                    incident_poller.stop()
                if incident_cursor is not None:
                    incident_cursor.close()
                if remote_poller is not None:
                    remote_poller.stop()
                if mdns is not None:
                    mdns.stop()
                if poller is not None:
                    poller.stop()
                if guard is not None:
                    guard.stop()
                evidence.stop_if_recording()
                incidents.close()
                if location_store is not None:
                    location_store.close()
            return 0
    finally:
        pairing.close()
        registry.close()
    return 2


if __name__ == '__main__':
    sys.exit(main())

"""Owner-only provisioning and relay launcher. Never expose enrollment over HTTP."""
from __future__ import annotations

import argparse
import os
from pathlib import Path
import sys

from laptopwatch.security import PairingRegistry, unb64url

from .app import RelayStore, create_relay
from .push import FirebaseProvider


def main(argv=None):
    parser=argparse.ArgumentParser(description="LaptopWatch private location relay")
    parser.add_argument("--data-dir", type=Path,
                        default=Path(os.environ.get("LAPTOPWATCH_RELAY_DATA", "/srv/laptopwatch")))
    subs=parser.add_subparsers(dest="command",required=True)
    enroll=subs.add_parser("enroll",help="owner-only local enrollment, never over HTTP")
    enroll.add_argument("--id",required=True)
    enroll.add_argument("--key-b64url",required=True)
    enroll.add_argument("--role",choices=["desktop","owner_phone"],required=True)
    revoke=subs.add_parser("revoke")
    revoke.add_argument("--id",required=True)
    serve=subs.add_parser("serve")
    serve.add_argument("--port",type=int,default=8080)
    args=parser.parse_args(argv)
    args.data_dir.mkdir(mode=0o700,parents=True,exist_ok=True)
    registry=PairingRegistry(args.data_dir / "devices.sqlite")
    storage=RelayStore(args.data_dir / "location.sqlite")
    try:
        if args.command=="enroll":
            key=unb64url(args.key_b64url,32)
            storage.enroll(registry,args.id,key,args.role)
            print("Device enrolled locally; grant only authorized owner keys")
        elif args.command=="revoke":
            registry.revoke(args.id)
            print("Device revoked")
        elif args.command=="serve":
            if not 1024<=args.port<=65535:
                parser.error("port out of range")
            import uvicorn
            # Only the reverse proxy should expose this container to WAN.
            project_id=os.environ.get("LAPTOPWATCH_FIREBASE_PROJECT_ID","").strip()
            provider=FirebaseProvider(project_id) if project_id else None
            uvicorn.run(create_relay(registry,storage,push_provider=provider),host="0.0.0.0",
                        port=args.port, access_log=False,proxy_headers=False)
    finally:
        storage.close()
        registry.close()
    return 0


if __name__=="__main__":
    sys.exit(main())

"""Owner-only, separate loopback health listener. Never expose control actions.

Not an alternate remote API: the sole command/control authority remains the
paired Ed25519/TLS gateway. This process-health channel exists only for Tauri
and uses a random per-start bearer inherited through the child's environment.
"""
from __future__ import annotations

import hmac
import re

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

_TOKEN = re.compile(r"^[a-f0-9]{64}$")


def create_owner_health_app(token: str) -> FastAPI:
    if not isinstance(token, str) or _TOKEN.fullmatch(token) is None:
        raise ValueError("Missing or invalid per-launch owner health token")
    app = FastAPI(docs_url=None, redoc_url=None, openapi_url=None)

    @app.middleware("http")
    async def authorize(request: Request, call_next):
        host = request.client.host if request.client else ""
        if host not in ("127.0.0.1", "::1"):
            return JSONResponse({"error": "Forbidden"}, status_code=403,
                                headers={"Cache-Control": "no-store"})
        if request.method != "GET" or request.url.path != "/health" or request.url.query:
            return JSONResponse({"error": "Not found"}, status_code=404,
                                headers={"Cache-Control": "no-store"})
        provided = request.headers.get("authorization", "")
        if not provided.startswith("Bearer ") or not hmac.compare_digest(
                provided[7:], token):
            return JSONResponse({"error": "Unauthorized"}, status_code=401,
                                headers={"Cache-Control": "no-store"})
        return await call_next(request)

    @app.get("/health")
    async def health():
        # No secrets, peer identities, PII, media state or framework docs.
        return JSONResponse({"healthy": True}, headers={
            "Cache-Control": "no-store", "X-Content-Type-Options": "nosniff",
        })

    return app

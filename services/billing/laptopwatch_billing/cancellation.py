"""Bounded, fail-closed Paddle cancellation with idempotent status recovery."""
from __future__ import annotations

import json
import re
from urllib.request import Request, build_opener, HTTPSHandler, HTTPRedirectHandler

_SUBSCRIPTION = re.compile(r'^sub_[a-z0-9]{26}$')


class CancellationUnconfirmed(RuntimeError):
    """Paddle has not independently confirmed recurring billing is canceled."""


class _NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def cancel_subscription_immediately(api_key: str, subscription_id: str, *,
                                    sandbox: bool = False, opener=None) -> None:
    """Cancel immediately; if ambiguous, GET and verify authoritative status.

    A failed POST can have taken effect at Paddle. Reading authoritative state
    avoids permanent retries without trusting an HTTP error as success.
    All provider/transport errors remain unconfirmed and redact credentials.
    """
    if not isinstance(api_key, str) or len(api_key) < 20:
        raise CancellationUnconfirmed('Paddle cancellation is not configured')
    if not isinstance(subscription_id, str) or not _SUBSCRIPTION.fullmatch(subscription_id):
        raise CancellationUnconfirmed('Invalid subscription identifier')
    host = 'sandbox-api.paddle.com' if sandbox else 'api.paddle.com'
    url = f'https://{host}/subscriptions/{subscription_id}'
    headers = {'Authorization': 'Bearer ' + api_key,
               'Content-Type': 'application/json', 'Accept': 'application/json'}
    transport = opener or build_opener(HTTPSHandler(), _NoRedirect()).open

    def confirmed(request: Request) -> bool:
        with transport(request, timeout=8) as response:
            if response.status != 200:
                return False
            raw = response.read(16385)
        if len(raw) > 16384:
            return False
        data = json.loads(raw)
        payload = data.get('data') if isinstance(data, dict) else None
        return isinstance(payload, dict) and payload.get('id') == subscription_id \
            and payload.get('status') == 'canceled'

    post = Request(url + '/cancel', data=b'{"effective_from":"immediately"}',
                   headers=headers, method='POST')
    try:
        if confirmed(post):
            return
    except Exception:
        # Timed-out POST or HTTP 4xx can follow a successful prior cancellation.
        pass
    try:
        if confirmed(Request(url, headers=headers, method='GET')):
            return
    except Exception:
        pass
    raise CancellationUnconfirmed('Paddle cancellation unavailable or unconfirmed')

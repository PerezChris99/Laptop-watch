"""Bounded, fail-closed Paddle subscription cancellation on account closure.

Paddle is the authority for whether billing stopped. A scheduled cancellation,
network timeout or malformed response is never represented as success.
"""
from __future__ import annotations

import json
import re
from urllib.request import Request, urlopen

_SUBSCRIPTION = re.compile(r'^sub_[a-z0-9]{26}$')


class CancellationUnconfirmed(RuntimeError):
    """A subscription has not been independently confirmed canceled."""


def cancel_subscription_immediately(api_key: str, subscription_id: str, *,
                                    sandbox: bool = False, opener=None) -> None:
    """Request immediate cancellation and verify Paddle's returned state.

    Provider API errors are redacted to avoid leaking credentials and metadata.
    Caller must retry durably; a timeout is not cancellation confirmation.
    """
    if not isinstance(api_key, str) or len(api_key) < 20:
        raise CancellationUnconfirmed('Paddle cancellation is not configured')
    if not isinstance(subscription_id, str) or not _SUBSCRIPTION.fullmatch(subscription_id):
        raise CancellationUnconfirmed('Invalid subscription identifier')
    host = 'sandbox-api.paddle.com' if sandbox else 'api.paddle.com'
    request = Request(
        f'https://{host}/subscriptions/{subscription_id}/cancel',
        data=b'{"effective_from":"immediately"}',
        headers={'Authorization': 'Bearer ' + api_key,
                 'Content-Type': 'application/json', 'Accept': 'application/json'},
        method='POST',
    )
    try:
        transport = opener or urlopen
        with transport(request, timeout=8) as response:
            if response.status != 200:
                raise CancellationUnconfirmed('Provider did not accept cancellation')
            raw = response.read(16385)
        if len(raw) > 16384:
            raise CancellationUnconfirmed('Provider response exceeded safety limit')
        result = json.loads(raw)
        payload = result.get('data') if isinstance(result, dict) else None
        if not isinstance(payload, dict) or payload.get('id') != subscription_id or \
                payload.get('status') != 'canceled':
            raise CancellationUnconfirmed('Cancellation not confirmed by provider')
    except CancellationUnconfirmed:
        raise
    except Exception as exc:
        raise CancellationUnconfirmed('Paddle cancellation unavailable or unconfirmed') from exc

"""Operator-run, Paddle API-verified subscription reconciliation (dry-run default).

No checkout callback, locally supplied transaction or email can grant a license.
Use only for subscriptions previously bound through a signed webhook.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import time
from urllib.parse import urlparse, urlencode, parse_qs
from urllib.request import HTTPSHandler, HTTPRedirectHandler, Request, build_opener

from .app import Store, STATUS, parse_instant, event_micros

SUB_ID = re.compile(r'^sub_[a-z0-9]{26}$')
TXN_ID = re.compile(r'^txn_[a-z0-9]{26}$')


class ReconciliationError(ValueError):
    pass


class _NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class PaddleSnapshotClient:
    def __init__(self, api_key: str, *, sandbox: bool = False, opener=None):
        if not api_key or len(api_key) < 20:
            raise ReconciliationError('Merchant API key missing')
        self.base = 'https://' + ('sandbox-api.paddle.com' if sandbox else 'api.paddle.com')
        self.api_key = api_key
        self.opener = opener or build_opener(HTTPSHandler(), _NoRedirect())

    def _get(self, url: str) -> dict:
        target = urlparse(url)
        if (target.scheme != 'https' or target.netloc != self.base[8:] or
                target.fragment or target.username or target.password or target.port is not None):
            raise ReconciliationError('Untrusted Paddle pagination destination')
        if not (re.fullmatch(r'/subscriptions/sub_[a-z0-9]{26}', target.path)
                or target.path == '/transactions'):
            raise ReconciliationError('Unsupported provider resource')
        request = Request(url, headers={
            'Authorization': 'Bearer ' + self.api_key, 'Accept': 'application/json'})
        try:
            with self.opener.open(request, timeout=10) as response:
                if response.status != 200:
                    raise ReconciliationError('Provider did not confirm resource')
                raw = response.read(1_048_577)
                if len(raw) > 1_048_576:
                    raise ReconciliationError('Provider response too large')
        except OSError as exc:
            raise ReconciliationError('Paddle API unreachable') from exc
        data = json.loads(raw)
        if not isinstance(data, dict):
            raise ReconciliationError('Invalid Paddle API response')
        return data

    def subscription(self, sub_id: str) -> dict:
        if not SUB_ID.fullmatch(sub_id):
            raise ReconciliationError('Invalid subscription ID')
        data = self._get(self.base + '/subscriptions/' + sub_id).get('data')
        if not isinstance(data, dict) or data.get('id') != sub_id:
            raise ReconciliationError('Subscription mismatch')
        return data

    def transactions(self, sub_id: str) -> list[dict]:
        if not SUB_ID.fullmatch(sub_id):
            raise ReconciliationError('Invalid subscription ID')
        url = self.base + '/transactions?' + urlencode({
            'subscription_id': sub_id, 'status': 'completed', 'per_page': 30})
        items, seen = [], set()
        for _ in range(50):
            if url in seen:
                raise ReconciliationError('Pagination loop')
            seen.add(url)
            data = self._get(url)
            meta = data.get('meta')
            page = data.get('data')
            pagination = meta.get('pagination') if isinstance(meta, dict) else None
            if not isinstance(page, list) or not isinstance(pagination, dict):
                raise ReconciliationError('Incomplete provider page')
            items.extend(page)
            if len(items) > 1500:
                raise ReconciliationError('Transaction cap exceeded')
            if pagination.get('has_more') is False:
                return items
            if pagination.get('has_more') is not True:
                raise ReconciliationError('Missing pagination state')
            url = pagination.get('next')
            if not isinstance(url, str) or not url.startswith(self.base + '/transactions?'):
                raise ReconciliationError('Untrusted pagination link')
            query = parse_qs(urlparse(url).query)
            if query.get('subscription_id') != [sub_id] or query.get('status') != ['completed']:
                raise ReconciliationError('Pagination changed requested subscription')
        raise ReconciliationError('Provider pagination cap exceeded')


def reconcile(store: Store, provider: PaddleSnapshotClient, sub_id: str,
              prices: dict[str, tuple[str, int]], *, apply: bool = False,
              now: int | None = None) -> dict:
    if not SUB_ID.fullmatch(sub_id):
        raise ReconciliationError('Invalid subscription ID')
    now = int(time.time()) if now is None else now
    with store.lock:
        old = store.db.execute('SELECT * FROM subscriptions WHERE id=?', (sub_id,)).fetchone()
        if old is None:
            raise ReconciliationError('Unbound subscription cannot be imported')
        bound = dict(old)
    sub = provider.subscription(sub_id)
    if sub.get('customer_id') != bound['customer_id']:
        raise ReconciliationError('Customer differs from original checkout binding')
    if sub.get('status') not in STATUS:
        raise ReconciliationError('Unsupported subscription state')
    items = sub.get('items')
    if (not isinstance(items, list) or len(items) != 1 or
            not isinstance(items[0], dict) or not isinstance(items[0].get('price'), dict) or
            items[0]['price'].get('id') not in prices):
        raise ReconciliationError('Unexpected subscription price')
    price = items[0]['price']['id']
    period = sub.get('current_billing_period') or {}
    end = parse_instant(period['ends_at']) if period.get('ends_at') else 0
    updated_at = event_micros(sub['updated_at'])
    if updated_at < bound['event_time']:
        raise ReconciliationError('Provider snapshot older than latest signed webhook')
    payments = []
    if sub['status'] == 'active' and end > now:
        for tx in provider.transactions(sub_id):
            if (not isinstance(tx, dict) or tx.get('status') != 'completed'
                    or tx.get('subscription_id') != sub_id
                    or tx.get('customer_id') != bound['customer_id']
                    or not isinstance(tx.get('id'), str) or not TXN_ID.fullmatch(tx['id'])):
                raise ReconciliationError('Provider transaction identity mismatch')
            billing = tx.get('billing_period') or {}
            start = parse_instant(billing['starts_at']) if billing.get('starts_at') else 0
            paid_end = parse_instant(billing['ends_at']) if billing.get('ends_at') else 0
            if 0 < start < paid_end and abs(paid_end - end) <= 300:
                payments.append((tx['id'], start, paid_end))
    effective = sub['status'] if sub['status'] != 'active' or payments else 'past_due'
    result = {'subscription_id': sub_id, 'provider_status': sub['status'],
              'effective_status': effective, 'confirmed_payments': len(payments),
              'applied': False}
    if not apply:
        return result
    with store.tx() as db:
        existing = db.execute('SELECT * FROM subscriptions WHERE id=?', (sub_id,)).fetchone()
        if (existing is None or existing['customer_id'] != bound['customer_id']
                or existing['event_time'] > updated_at):
            raise ReconciliationError('Ledger changed during reconciliation')
        for transaction_id, start, finish in payments:
            db.execute('''INSERT OR IGNORE INTO paid_transactions
                (id,subscription_id,customer_id,recorded_at,billing_period_start,billing_period_end)
                VALUES (?,?,?,?,?,?)''',
                (transaction_id, sub_id, bound['customer_id'], now * 1_000_000, start, finish))
        db.execute('''UPDATE subscriptions
            SET status=?,price_id=?,period_end=?,event_time=? WHERE id=?''',
            (effective, price, end, updated_at, sub_id))
    result['applied'] = True
    return result


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--subscription-id', required=True)
    parser.add_argument('--apply', action='store_true', help='persist verified provider state; dry-run by default')
    args = parser.parse_args(argv)
    db = os.environ.get('LW_BILLING_DB')
    key = os.environ.get('LW_PADDLE_API_KEY')
    if not db or not key:
        parser.error('LW_BILLING_DB and LW_PADDLE_API_KEY are required')
    prices = {}
    for name, seats in [('PRO', 3), ('BUSINESS', 10)]:
        price = os.environ.get('LW_PADDLE_PRICE_' + name)
        if not price:
            parser.error('Missing merchant price ID: ' + name)
        prices[price] = (name.lower(), seats)
    store = Store(db)
    try:
        provider = PaddleSnapshotClient(
            key, sandbox=os.environ.get('LW_PADDLE_SANDBOX', 'false').lower() == 'true')
        print(json.dumps(reconcile(store, provider, args.subscription_id, prices,
                                   apply=args.apply), sort_keys=True))
    finally:
        store.db.close()


if __name__ == '__main__':
    main()

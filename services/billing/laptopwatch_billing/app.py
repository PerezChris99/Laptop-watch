"""Fail-closed Paddle Billing webhook and signed device entitlement service.

No client-side checkout event can grant rights. Only Paddle-signed subscription
webhooks associated with an operator-issued checkout ticket can update rights.
Operator provisioning is intentionally separate from public self-service auth.
"""
from __future__ import annotations

import asyncio
import base64
from contextlib import contextmanager
from datetime import datetime, timezone
from urllib.request import Request as UrlRequest, urlopen
from urllib.parse import urlparse
import hashlib
import hmac
import json
import os
from pathlib import Path
import re
import secrets
import sqlite3
import threading
import time
import uuid

from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey, Ed25519PublicKey
from cryptography.hazmat.primitives import serialization
from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import FileResponse, JSONResponse
from pydantic import BaseModel, ConfigDict, Field

from .cancellation import cancel_subscription_immediately

MAX_WEBHOOK = 262_144
WEBHOOK_SKEW = 300
LEASE_SECONDS = 3600
STATUS = frozenset({'active', 'trialing', 'past_due', 'paused', 'canceled'})
SUB_EVENTS = frozenset('subscription.' + s for s in (
    'created', 'updated', 'activated', 'resumed', 'paused', 'canceled', 'past_due', 'trialing', 'imported'))
_B64 = re.compile(r'^[A-Za-z0-9_-]+$')
_HEX = re.compile(r'^[0-9a-f]{64}$')


def enc(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b'=').decode('ascii')


def dec(s: str, n: int | None = None) -> bytes:
    if not isinstance(s, str) or len(s) > 2048 or not _B64.fullmatch(s):
        raise ValueError('invalid base64url')
    data = base64.urlsafe_b64decode(s + '=' * (-len(s) % 4))
    if enc(data) != s or (n is not None and len(data) != n):
        raise ValueError('invalid base64url length')
    return data


def json_bytes(value: dict) -> bytes:
    return json.dumps(value, sort_keys=True, separators=(',', ':'), ensure_ascii=True).encode('ascii')


def strict_uuid(value: str) -> str:
    value = str(uuid.UUID(str(value)))
    return value


def parse_instant(s: str) -> int:
    if not isinstance(s, str):
        raise ValueError('missing timestamp')
    dt = datetime.fromisoformat(s.replace('Z', '+00:00'))
    if dt.tzinfo is None:
        raise ValueError('timestamp must be timezone aware')
    return int(dt.timestamp())


def event_micros(s: str) -> int:
    dt = datetime.fromisoformat(s.replace('Z', '+00:00'))
    if dt.tzinfo is None:
        raise ValueError('timestamp must be timezone aware')
    return int(dt.timestamp() * 1_000_000)


def verify_paddle(raw: bytes, header: str, secret: str, now: int) -> None:
    if not secret or len(secret) < 16 or not isinstance(header, str) or len(header) > 512:
        raise ValueError('webhook not configured or signature missing')
    parts: dict[str, list[str]] = {}
    for item in header.split(';'):
        key, sep, val = item.strip().partition('=')
        if not sep or key not in {'ts', 'h1'}:
            raise ValueError('invalid Paddle-Signature')
        parts.setdefault(key, []).append(val)
    if len(parts.get('ts', [])) != 1 or not parts.get('h1'):
        raise ValueError('missing webhook signature fields')
    raw_ts = parts['ts'][0]
    if not raw_ts.isascii() or not raw_ts.isdecimal() or len(raw_ts) > 12:
        raise ValueError('invalid webhook timestamp')
    ts = int(raw_ts)
    if abs(now - ts) > WEBHOOK_SKEW:
        raise ValueError('webhook outside replay window')
    digest = hmac.new(secret.encode(), raw_ts.encode() + b':' + raw, hashlib.sha256).hexdigest()
    if not any(_HEX.fullmatch(candidate) and hmac.compare_digest(digest, candidate)
               for candidate in parts['h1']):
        raise ValueError('invalid webhook signature')


def ticket_for(account_id: str, price_id: str, secret: str, now: int) -> str:
    value = {'v': 1, 'account_id': strict_uuid(account_id), 'price_id': price_id,
             'iat': now, 'exp': now + 900, 'nonce': enc(secrets.token_bytes(16))}
    body = enc(json_bytes(value))
    signature = enc(hmac.new(secret.encode(), body.encode(), hashlib.sha256).digest())
    return body + '.' + signature


def verify_ticket(value: str, secret: str, price_id: str, event_time: int) -> str:
    try:
        body, mac = value.split('.')
        expected = hmac.new(secret.encode(), body.encode(), hashlib.sha256).digest()
        if not hmac.compare_digest(expected, dec(mac, 32)):
            raise ValueError('invalid checkout ticket')
        obj = json.loads(dec(body))
        if (obj.get('v') != 1 or obj['price_id'] != price_id
                or not isinstance(obj['iat'], int) or not isinstance(obj['exp'], int)
                or obj['iat'] > event_time + 300 or event_time > obj['exp']
                or obj['exp'] - obj['iat'] != 900):
            raise ValueError('checkout ticket does not match purchase')
        dec(obj['nonce'], 16)
        return strict_uuid(obj['account_id'])
    except (KeyError, TypeError, ValueError, AttributeError) as exc:
        raise ValueError('invalid or expired checkout ticket') from exc


class Store:
    def __init__(self, file: str):
        self.db = sqlite3.connect(file, check_same_thread=False, isolation_level=None, timeout=10)
        self.db.row_factory = sqlite3.Row
        self.lock = threading.RLock()
        self.db.execute('PRAGMA busy_timeout=10000')
        self.db.execute('PRAGMA journal_mode=WAL')
        self.db.executescript('''
          CREATE TABLE IF NOT EXISTS accounts(id TEXT PRIMARY KEY);
          CREATE TABLE IF NOT EXISTS closed_accounts(
            account_id TEXT PRIMARY KEY, requested_at INTEGER NOT NULL,
            FOREIGN KEY(account_id) REFERENCES accounts(id));
          CREATE TABLE IF NOT EXISTS devices(
            id TEXT PRIMARY KEY, account_id TEXT NOT NULL, public_key BLOB NOT NULL,
            revoked INTEGER NOT NULL DEFAULT 0, FOREIGN KEY(account_id) REFERENCES accounts(id));
          CREATE TABLE IF NOT EXISTS subscriptions(
            id TEXT PRIMARY KEY, account_id TEXT NOT NULL, customer_id TEXT NOT NULL,
            price_id TEXT NOT NULL, status TEXT NOT NULL, period_end INTEGER NOT NULL,
            event_time INTEGER NOT NULL, FOREIGN KEY(account_id) REFERENCES accounts(id));
          CREATE INDEX IF NOT EXISTS idx_sub_accounts ON subscriptions(account_id,status);
          CREATE TABLE IF NOT EXISTS webhook_events(id TEXT PRIMARY KEY, received_at INTEGER NOT NULL);
          CREATE TABLE IF NOT EXISTS paid_transactions(
            id TEXT PRIMARY KEY, subscription_id TEXT NOT NULL, customer_id TEXT NOT NULL,
            recorded_at INTEGER NOT NULL,
            billing_period_start INTEGER NOT NULL DEFAULT 0,
            billing_period_end INTEGER NOT NULL DEFAULT 0);
          CREATE INDEX IF NOT EXISTS idx_paid_subscription ON paid_transactions(subscription_id,customer_id);
          CREATE TABLE IF NOT EXISTS billing_holds(
            subscription_id TEXT PRIMARY KEY, reason TEXT NOT NULL);
          CREATE TABLE IF NOT EXISTS checkout_bindings(
            ticket_hash TEXT PRIMARY KEY, subscription_id TEXT NOT NULL);
          CREATE TABLE IF NOT EXISTS enrollment_invites(
            token_hash TEXT PRIMARY KEY, account_id TEXT NOT NULL,
            expires_at INTEGER NOT NULL,
            FOREIGN KEY(account_id) REFERENCES accounts(id));
          CREATE TABLE IF NOT EXISTS lease_nonces(
            device_id TEXT NOT NULL, nonce TEXT NOT NULL, created_at INTEGER NOT NULL,
            PRIMARY KEY(device_id,nonce));
          CREATE TABLE IF NOT EXISTS cancellation_jobs(
            subscription_id TEXT PRIMARY KEY, account_id TEXT NOT NULL,
            attempt_count INTEGER NOT NULL DEFAULT 0,
            attempted_at INTEGER NOT NULL DEFAULT 0,
            confirmed_at INTEGER NOT NULL DEFAULT 0);
          CREATE INDEX IF NOT EXISTS idx_cancel_pending
            ON cancellation_jobs(confirmed_at,attempted_at);
        ''')
        # Non-destructive migration: historical transactions without a verified
        # billing period do not grant a new license until reconciled with Paddle.
        existing_columns = {row[1] for row in self.db.execute('PRAGMA table_info(paid_transactions)')}
        for column in ('billing_period_start', 'billing_period_end'):
            if column not in existing_columns:
                self.db.execute(f'ALTER TABLE paid_transactions ADD COLUMN {column} INTEGER NOT NULL DEFAULT 0')
        self.db.execute('PRAGMA foreign_keys=ON')

    @contextmanager
    def tx(self):
        with self.lock:
            self.db.execute('BEGIN IMMEDIATE')
            try:
                yield self.db
                self.db.execute('COMMIT')
            except Exception:
                self.db.execute('ROLLBACK')
                raise

    def create_account(self, account_id: str):
        with self.tx() as db:
            db.execute('INSERT INTO accounts(id) VALUES (?)', (strict_uuid(account_id),))

    def is_closed(self, account_id: str) -> bool:
        with self.lock:
            return bool(self.db.execute('SELECT 1 FROM closed_accounts WHERE account_id=?',
                                        (strict_uuid(account_id),)).fetchone())

    def close_account(self, account_id: str, now: int) -> str:
        """Revoke all local entitlements. Merchant cancellation and statutory
        financial retention must be handled separately through Paddle."""
        account_id = strict_uuid(account_id)
        with self.tx() as db:
            if not db.execute('SELECT 1 FROM accounts WHERE id=?', (account_id,)).fetchone():
                raise ValueError('unknown account')
            if db.execute('SELECT 1 FROM closed_accounts WHERE account_id=?', (account_id,)).fetchone():
                return 'already_closed'
            db.execute('INSERT INTO closed_accounts(account_id,requested_at) VALUES (?,?)',
                       (account_id, now))
            db.execute('UPDATE devices SET revoked=1 WHERE account_id=?', (account_id,))
            db.execute('DELETE FROM enrollment_invites WHERE account_id=?', (account_id,))
            # Closure tombstone revokes access immediately even if Paddle is offline.
            # Persist every outstanding recurring charge cancellation for retry.
            db.execute("""INSERT OR IGNORE INTO cancellation_jobs(subscription_id,account_id)
                SELECT id,account_id FROM subscriptions
                WHERE account_id=? AND status!='canceled'""", (account_id,))
            return 'closed'

    def pending_cancellations(self, account_id: str | None = None, *, limit: int = 20) -> list[str]:
        with self.lock:
            if account_id is None:
                rows = self.db.execute("""SELECT subscription_id FROM cancellation_jobs
                    WHERE confirmed_at=0 ORDER BY attempted_at,subscription_id LIMIT ?""",
                    (limit,)).fetchall()
            else:
                rows = self.db.execute("""SELECT subscription_id FROM cancellation_jobs
                    WHERE confirmed_at=0 AND account_id=?
                    ORDER BY attempted_at,subscription_id LIMIT ?""",
                    (strict_uuid(account_id),limit)).fetchall()
        return [row['subscription_id'] for row in rows]

    def pending_cancellation_count(self, account_id: str | None = None) -> int:
        with self.lock:
            if account_id is None:
                row = self.db.execute('SELECT COUNT(*) FROM cancellation_jobs WHERE confirmed_at=0').fetchone()
            else:
                row = self.db.execute("""SELECT COUNT(*) FROM cancellation_jobs
                    WHERE confirmed_at=0 AND account_id=?""", (strict_uuid(account_id),)).fetchone()
        return int(row[0])

    def record_cancellation_attempt(self, subscription_id: str, confirmed: bool, now: int):
        with self.tx() as db:
            db.execute("""UPDATE cancellation_jobs SET attempt_count=attempt_count+1,
                attempted_at=?, confirmed_at=CASE WHEN ? THEN ? ELSE confirmed_at END
                WHERE subscription_id=? AND confirmed_at=0""",
                (now,int(confirmed),now,subscription_id))

    def create_invite(self, account_id: str, now: int) -> str:
        account_id = strict_uuid(account_id)
        code = enc(secrets.token_bytes(32))
        with self.tx() as db:
            if not db.execute('SELECT 1 FROM accounts WHERE id=?', (account_id,)).fetchone() or self.is_closed(account_id):
                raise ValueError('account not found or closed')
            db.execute('DELETE FROM enrollment_invites WHERE expires_at <= ?', (now,))
            db.execute('INSERT INTO enrollment_invites(token_hash,account_id,expires_at) VALUES (?,?,?)',
                       (hashlib.sha256(code.encode('ascii')).hexdigest(), account_id, now + 900))
        return code

    def redeem_invite(self, account_id: str, device_id: str, public_key: bytes,
                      token: str, timestamp: int, nonce: str, signature: bytes, now: int) -> None:
        account_id, device_id = strict_uuid(account_id), strict_uuid(device_id)
        if isinstance(timestamp, bool) or abs(now - timestamp) > 60:
            raise ValueError('stale enrollment proof')
        Ed25519PublicKey.from_public_bytes(public_key)
        dec(token, 32)
        dec(nonce, 16)
        digest = hashlib.sha256(token.encode('ascii')).hexdigest()
        msg = (f'LW-ENROLL-1\n{account_id}\n{device_id}\n{enc(public_key)}\n'
               f'{timestamp}\n{nonce}\n{digest}\n').encode('ascii')
        Ed25519PublicKey.from_public_bytes(public_key).verify(signature, msg)
        with self.tx() as db:
            valid = db.execute('SELECT 1 FROM enrollment_invites WHERE token_hash=? '
                               'AND account_id=? AND expires_at>?', (digest, account_id, now)).fetchone()
            if not valid or self.is_closed(account_id):
                raise ValueError('invalid, redeemed, closed or expired invite')
            db.execute('INSERT INTO devices(id,account_id,public_key) VALUES (?,?,?)',
                       (device_id, account_id, public_key))
            db.execute('DELETE FROM enrollment_invites WHERE token_hash=?', (digest,))

    def register_device(self, account_id: str, device_id: str, public_key: bytes):
        Ed25519PublicKey.from_public_bytes(public_key)
        with self.tx() as db:
            if self.is_closed(account_id):
                raise ValueError('closed account')
            db.execute('INSERT INTO devices(id,account_id,public_key) VALUES (?,?,?)'
                       (strict_uuid(device_id), strict_uuid(account_id), public_key))

    def revoke_device(self, account_id: str, device_id: str):
        with self.tx() as db:
            row = db.execute('UPDATE devices SET revoked=1 WHERE id=? AND account_id=?',
                             (strict_uuid(device_id), strict_uuid(account_id)))
            if row.rowcount != 1:
                raise ValueError('unknown device')

    def apply_paddle(self, event: dict, prices: dict[str, tuple[str, int]], ticket_secret: str) -> str:
        if not isinstance(event, dict):
            raise ValueError('invalid Paddle event')
        event_id = event.get('event_id')
        event_type = event.get('event_type')
        if not isinstance(event_id, str) or not re.fullmatch(r'evt_[a-z0-9]{26}', event_id):
            raise ValueError('invalid Paddle event ID')
        # Successful checkout screens and initial invoices are NOT payment proof.
        # Only a verified transaction.completed event may unlock a subscription.
        if event_type in {'transaction.completed', 'adjustment.created', 'adjustment.updated'}:
            data = event.get('data')
            if not isinstance(data, dict):
                raise ValueError('invalid payment/adjustment event')
            when = event_micros(event.get('occurred_at'))
            with self.tx() as db:
                if db.execute('SELECT 1 FROM webhook_events WHERE id=?', (event_id,)).fetchone():
                    return 'duplicate'
                if event_type == 'transaction.completed':
                    tx = data.get('id')
                    sub = data.get('subscription_id')
                    customer = data.get('customer_id')
                    if (data.get('status') != 'completed' or
                        not isinstance(tx, str) or not re.fullmatch(r'txn_[a-z0-9]{26}', tx)):
                        raise ValueError('invalid paid transaction')
                    period = data.get('billing_period')
                    if not isinstance(period, dict):
                        raise ValueError('transaction must contain a verifiable billing period')
                    period_start = parse_instant(period.get('starts_at'))
                    period_end = parse_instant(period.get('ends_at'))
                    if period_start >= period_end or period_end - period_start > 370 * 86400:
                        raise ValueError('invalid transaction billing period')
                    if (isinstance(sub, str) and re.fullmatch(r'sub_[a-z0-9]{26}', sub) and
                        isinstance(customer, str) and re.fullmatch(r'ctm_[a-z0-9]{26}', customer)):
                        prior = db.execute('SELECT customer_id FROM subscriptions WHERE id=?', (sub,)).fetchone()
                        if prior and prior['customer_id'] != customer:
                            raise ValueError('customer mismatch')
                        db.execute('''INSERT OR IGNORE INTO paid_transactions
                            (id, subscription_id, customer_id, recorded_at, billing_period_start, billing_period_end)
                            VALUES (?,?,?,?,?,?)''',
                            (tx, sub, customer, when, period_start, period_end))
                        result = 'payment-recorded'
                    else:
                        result = 'ignored-no-recurring-subscription'
                else:
                    action = data.get('action')
                    status = data.get('status')
                    sub = data.get('subscription_id')
                    customer = data.get('customer_id')
                    result = 'adjustment-reviewed'
                    if (isinstance(sub, str) and re.fullmatch(r'sub_[a-z0-9]{26}', sub)
                        and isinstance(customer, str) and re.fullmatch(r'ctm_[a-z0-9]{26}', customer)
                        and action in {'refund','chargeback','chargeback_warning'}
                        and status in {'approved', 'pending_approval'}):
                        prior = db.execute('SELECT customer_id FROM subscriptions WHERE id=?', (sub,)).fetchone()
                        if prior is not None and prior['customer_id'] != customer:
                            raise ValueError('adjustment customer mismatch')
                        # Conservative hold: never auto-restore rights on a
                        # reversal without verified operator reconciliation.
                        db.execute('INSERT OR IGNORE INTO billing_holds VALUES (?,?)', (sub,action))
                        result = 'entitlement-held'
                db.execute('INSERT INTO webhook_events(id,received_at) VALUES (?,?)',
                           (event_id, int(time.time())))
            return result
        if event_type not in SUB_EVENTS:
            return 'ignored'
        data = event.get('data')
        if not isinstance(data, dict):
            raise ValueError('missing subscription')
        sub_id = data.get('id')
        customer_id = data.get('customer_id')
        status = data.get('status')
        if not isinstance(sub_id, str) or not re.fullmatch(r'sub_[a-z0-9]{26}', sub_id):
            raise ValueError('invalid subscription ID')
        if not isinstance(customer_id, str) or not re.fullmatch(r'ctm_[a-z0-9]{26}', customer_id):
            raise ValueError('invalid customer ID')
        if status not in STATUS:
            raise ValueError('unsupported subscription status')
        when = event_micros(event.get('occurred_at'))
        items = data.get('items')
        if not isinstance(items, list):
            raise ValueError('missing subscription items')
        known = [i.get('price', {}).get('id') for i in items if isinstance(i, dict)
                 and isinstance(i.get('price'), dict)
                 and i['price'].get('id') in prices]
        if len(known) != 1:
            raise ValueError('subscription must contain exactly one licensed recurring price')
        price_id = known[0]
        period = data.get('current_billing_period') or {}
        period_end = parse_instant(period['ends_at']) if period.get('ends_at') else 0
        with self.tx() as db:
            if db.execute('SELECT 1 FROM webhook_events WHERE id=?', (event_id,)).fetchone():
                return 'duplicate'
            existing = db.execute('SELECT * FROM subscriptions WHERE id=?', (sub_id,)).fetchone()
            if existing and (when < existing['event_time'] or
              (when == existing['event_time'] and (existing['status'] in {'canceled','paused','past_due'}
               or status in {'active','trialing'}))):
                db.execute('INSERT INTO webhook_events(id,received_at) VALUES (?,?)', (event_id, int(time.time())))
                return 'stale'
            if existing:
                if existing['customer_id'] != customer_id:
                    raise ValueError('customer identity changed')
                account_id = existing['account_id']
            else:
                custom_data = data.get('custom_data') or {}
                if not isinstance(custom_data, dict):
                    raise ValueError('missing checkout linkage')
                checkout_ticket = custom_data.get('laptopwatch_checkout_ticket', '')
                account_id = verify_ticket(checkout_ticket,
                                           ticket_secret, price_id, when // 1_000_000)
                token_hash = hashlib.sha256(checkout_ticket.encode('ascii')).hexdigest()
                linked = db.execute('SELECT subscription_id FROM checkout_bindings WHERE ticket_hash=?',
                                    (token_hash,)).fetchone()
                if linked and linked['subscription_id'] != sub_id:
                    raise ValueError('checkout ticket already bound to another subscription')
                if not linked:
                    db.execute('INSERT INTO checkout_bindings(ticket_hash,subscription_id) VALUES (?,?)',
                               (token_hash, sub_id))
                if not db.execute('SELECT 1 FROM accounts WHERE id=?', (account_id,)).fetchone():
                    raise ValueError('unknown account')
            db.execute('''INSERT INTO subscriptions(id,account_id,customer_id,price_id,status,period_end,event_time)
                VALUES (?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET
                price_id=excluded.price_id,status=excluded.status,
                period_end=excluded.period_end,event_time=excluded.event_time''',
                (sub_id, account_id, customer_id, price_id, status, period_end, when))
            if status == 'canceled':
                db.execute("""UPDATE cancellation_jobs SET confirmed_at=?
                    WHERE subscription_id=? AND confirmed_at=0""", (int(time.time()),sub_id))
            elif db.execute('SELECT 1 FROM closed_accounts WHERE account_id=?',
                            (account_id,)).fetchone():
                # Late payment/subscription webhook after closure: enqueue too.
                db.execute("""INSERT OR IGNORE INTO cancellation_jobs(subscription_id,account_id)
                    VALUES (?,?)""", (sub_id,account_id))
            db.execute('INSERT INTO webhook_events(id,received_at) VALUES (?,?)', (event_id, int(time.time())))
        return 'processed'

    def entitlement(self, account_id: str, device_id: str, now: int, prices: dict[str, tuple[str, int]]):
        with self.lock:
            device = self.db.execute('SELECT * FROM devices WHERE id=? AND account_id=? AND revoked=0',
                                     (device_id, account_id)).fetchone()
            if device is None or self.is_closed(account_id):
                raise ValueError('device unrecognized, revoked or account closed')
            subscriptions = self.db.execute('''SELECT * FROM subscriptions WHERE account_id=? AND
                status IN ('active','trialing') AND period_end>?
                AND EXISTS (SELECT 1 FROM paid_transactions p WHERE p.subscription_id=subscriptions.id
                            AND p.customer_id=subscriptions.customer_id
                            AND p.billing_period_end >= subscriptions.period_end - 300
                            AND p.billing_period_end <= subscriptions.period_end + 300
                            AND p.billing_period_start < subscriptions.period_end)
                AND NOT EXISTS (SELECT 1 FROM billing_holds h WHERE h.subscription_id=subscriptions.id)
                ORDER BY period_end DESC''',
                (account_id, now)).fetchall()
            for s in subscriptions:
                plan, seats = prices[s['price_id']]
                count = self.db.execute('SELECT COUNT(*) FROM devices WHERE account_id=? AND revoked=0',
                                        (account_id,)).fetchone()[0]
                if count <= seats:
                    return device['public_key'], {'plan': plan, 'max_devices': seats,
                        'subscription_id': s['id'], 'expires_at': min(s['period_end'], now + LEASE_SECONDS)}
        raise ValueError('no active paid entitlement or device limit exceeded')

    def portal_subscription(self, account_id: str, device_id: str):
        with self.lock:
            row = self.db.execute('SELECT 1 FROM devices WHERE id=? AND account_id=? AND revoked=0',
                                  (device_id, account_id)).fetchone()
            if row is None:
                raise ValueError('unknown device')
            sub = self.db.execute('SELECT customer_id,id FROM subscriptions WHERE account_id=? '
                                  'ORDER BY event_time DESC LIMIT 1', (account_id,)).fetchone()
            if sub is None:
                raise ValueError('no subscription')
            return sub['customer_id'], sub['id']

    def device_public_key(self, account_id: str, device_id: str) -> bytes:
        with self.lock:
            row = self.db.execute('SELECT public_key FROM devices WHERE id=? AND account_id=? '
                                  'AND revoked=0', (device_id, account_id)).fetchone()
            if row is None:
                raise ValueError('unknown or revoked device')
            return row[0]

    def claim_nonce(self, device_id: str, nonce: str, now: int):
        with self.tx() as db:
            db.execute('DELETE FROM lease_nonces WHERE created_at<?', (now-180,))
            db.execute('INSERT INTO lease_nonces(device_id,nonce,created_at) VALUES (?,?,?)',
                       (device_id, nonce, now))


class DeviceInput(BaseModel):
    model_config = ConfigDict(extra='forbid')
    account_id: str
    device_id: str
    public_key: str


class InviteInput(BaseModel):
    model_config = ConfigDict(extra='forbid')
    account_id: str


class EnrollInput(BaseModel):
    model_config = ConfigDict(extra='forbid')
    account_id: str
    device_id: str
    public_key: str
    invite_code: str
    timestamp: int
    nonce: str
    signature: str


class RevokeInput(BaseModel):
    model_config = ConfigDict(extra='forbid')
    account_id: str
    device_id: str


class TicketInput(BaseModel):
    model_config = ConfigDict(extra='forbid')
    account_id: str
    price_id: str


class LeaseInput(BaseModel):
    model_config = ConfigDict(extra='forbid')
    account_id: str
    device_id: str
    timestamp: int
    nonce: str
    signature: str


class CloseAccountInput(LeaseInput):
    confirm_account_id: str


class CheckoutValidateInput(BaseModel):
    model_config = ConfigDict(extra='forbid')
    price_id: str
    ticket: str


class CheckoutInput(LeaseInput):
    price_id: str


def paddle_portal_url(api_key: str, customer_id: str, subscription_id: str,
                      *, sandbox: bool = False) -> str:
    if not api_key:
        raise ValueError('Paddle API not configured')
    host = 'sandbox-api.paddle.com' if sandbox else 'api.paddle.com'
    body = json_bytes({'subscription_ids': [subscription_id]})
    req = UrlRequest(f'https://{host}/customers/{customer_id}/portal-sessions', data=body,
        headers={'Authorization': 'Bearer ' + api_key, 'Content-Type': 'application/json'},
        method='POST')
    # Provider-hosted session: never log or persist the returned bearer URL.
    with urlopen(req, timeout=8) as resp:
        if resp.status != 201:
            raise ValueError('Paddle portal request rejected')
        result = json.loads(resp.read(32768))
    url = result['data']['urls']['general']['overview']
    parsed = urlparse(url)
    expected = 'sandbox-customer-portal.paddle.com' if sandbox else 'customer-portal.paddle.com'
    if parsed.scheme != 'https' or parsed.hostname != expected or not parsed.query:
        raise ValueError('Unexpected Paddle portal URL')
    return url


def create_app(*, db_path: str, webhook_secret: str, checkout_secret: str,
               operator_secret: str, signing_key: Ed25519PrivateKey,
               prices: dict[str, tuple[str, int]], clock=None, portal_provider=None,
               paddle_client_token: str | None = None, sandbox: bool = False,
               cancellation_provider=None) -> FastAPI:
    if not prices or len(set(prices)) != len(prices) or any(not k.startswith('pri_') or
       plan not in {'pro', 'business'} or seats < 1 or seats > 100 for k,(plan,seats) in prices.items()):
        raise ValueError('merchant price IDs and enforced device quotas are required')
    if len(checkout_secret) < 32 or len(webhook_secret) < 16 or len(operator_secret) < 32:
        raise ValueError('all service signing/auth secrets must be provisioned')
    now = clock or (lambda: int(time.time()))
    store = Store(db_path)
    app = FastAPI(title='LaptopWatch Billing (operator-provisioned)', docs_url=None, redoc_url=None, openapi_url=None)
    app.state.store = store
    app.state.verify_public_key = enc(signing_key.public_key().public_bytes(
        encoding=serialization.Encoding.Raw, format=serialization.PublicFormat.Raw))

    async def reconcile_cancellations(account_id: str | None = None) -> dict:
        # Provider calls are bounded and off the event loop. Failed calls
        # remain in the durable retry queue, not marked as successful.
        if cancellation_provider is not None:
            for subscription_id in store.pending_cancellations(account_id, limit=20):
                try:
                    await asyncio.to_thread(cancellation_provider, subscription_id)
                except Exception:
                    store.record_cancellation_attempt(subscription_id, False, now())
                else:
                    store.record_cancellation_attempt(subscription_id, True, now())
        pending = store.pending_cancellation_count(account_id)
        return {'provider_cancellation_required': pending > 0,
                'cancellation_pending_count': pending}

    async def cancellation_retry_loop():
        while True:
            await reconcile_cancellations()
            await asyncio.sleep(300)

    @app.on_event('startup')
    async def start_cancellation_retries():
        if cancellation_provider is not None:
            app.state.cancel_task = asyncio.create_task(cancellation_retry_loop())

    @app.on_event('shutdown')
    async def stop_cancellation_retries():
        task = getattr(app.state, 'cancel_task', None)
        if task is not None:
            task.cancel()
            try:
                await task
            except asyncio.CancelledError:
                pass

    def admin(request: Request):
        bearer = request.headers.get('Authorization', '')
        if not bearer.startswith('Bearer ') or not hmac.compare_digest(bearer[7:], operator_secret):
            raise HTTPException(401, 'Unauthorized')

    # Public browser page: never receives a raw card, merchant API key or
    # privileged operator bearer. Checkout ticket arrives only in URL fragment.
    public = Path(__file__).resolve().parent / 'public'
    page_headers = {
        'Cache-Control': 'no-store', 'Referrer-Policy': 'no-referrer',
        'X-Content-Type-Options': 'nosniff', 'X-Frame-Options': 'DENY',
        'Content-Security-Policy': (
            "default-src 'none'; script-src 'self' https://cdn.paddle.com; "
            "connect-src 'self' https://*.paddle.com; frame-src https://*.paddle.com; "
            "img-src 'self' data: https://*.paddle.com; "
            "style-src 'self' 'unsafe-inline' https://*.paddle.com; "
            "font-src 'self' https://*.paddle.com; form-action https://*.paddle.com; "
            "frame-ancestors 'none'; base-uri 'none'; upgrade-insecure-requests")}

    @app.get('/checkout')
    async def checkout_page():
        return FileResponse(public / 'checkout.html', media_type='text/html', headers=page_headers)

    @app.get('/checkout.js')
    async def checkout_script():
        return FileResponse(public / 'checkout.js', media_type='application/javascript', headers=page_headers)

    @app.get('/v1/billing/public-config')
    async def checkout_public_config():
        if (not paddle_client_token or
            not paddle_client_token.startswith('test_' if sandbox else 'live_')):
            raise HTTPException(503, 'Merchant checkout unavailable')
        return JSONResponse({'client_token': paddle_client_token,
                             'environment': 'sandbox' if sandbox else 'production',
                             'prices': sorted(prices)}, headers={'Cache-Control':'no-store'})

    @app.post('/v1/admin/accounts')
    async def account(request: Request, data: dict):
        admin(request)
        try:
            account_id = strict_uuid(data['account_id'])
            store.create_account(account_id)
            return {'account_id': account_id}
        except (KeyError, ValueError, sqlite3.IntegrityError):
            raise HTTPException(400, 'Invalid or existing account')

    @app.post('/v1/admin/accounts/close')
    async def operator_close_account(request: Request, data: InviteInput):
        admin(request)
        try:
            result = store.close_account(data.account_id, now())
            return {'result': result, **await reconcile_cancellations(data.account_id)}
        except ValueError:
            raise HTTPException(404, 'Account unavailable')

    @app.post('/v1/accounts/close')
    async def owner_close_account(data: CloseAccountInput):
        try:
            account_id, device_id = strict_uuid(data.account_id), strict_uuid(data.device_id)
            if (data.confirm_account_id != account_id or isinstance(data.timestamp, bool)
                or abs(now() - data.timestamp) > 60):
                raise ValueError('stale or unconfirmed close request')
            dec(data.nonce, 16)
            pub = store.device_public_key(account_id, device_id)
            challenge = (f'LW-ACCOUNT-CLOSE-1\n{account_id}\n{device_id}\n'
                         f'{data.timestamp}\n{data.nonce}\n').encode('ascii')
            Ed25519PublicKey.from_public_bytes(pub).verify(dec(data.signature, 64), challenge)
            store.claim_nonce(device_id, data.nonce, now())
            result = store.close_account(account_id, now())
            return {'result': result, **await reconcile_cancellations(account_id)}
        except Exception:
            raise HTTPException(403, 'Account closure rejected')

    @app.get('/v1/admin/closures/pending')
    async def pending_closures(request: Request):
        admin(request)
        return {'pending_cancellations': store.pending_cancellation_count()}

    @app.post('/v1/admin/closures/retry')
    async def retry_closures(request: Request, data: InviteInput):
        admin(request)
        return await reconcile_cancellations(data.account_id)

    @app.post('/v1/admin/invitations')
    async def create_invitation(request: Request, data: InviteInput):
        admin(request)
        try:
            code = store.create_invite(data.account_id, now())
            # Only the authenticated operator receives the one-time token.
            # No tokens in URLs, logs, metrics or customer-facing responses.
            return {'invite_code': code, 'expires_in_seconds': 900}
        except ValueError:
            raise HTTPException(404, 'Account unavailable')

    @app.post('/v1/accounts/enroll')
    async def enroll_device(data: EnrollInput):
        # Operator-assisted onboarding: proof of possession of a fresh private
        # key AND a one-use invite issued out of band to the intended customer.
        try:
            store.redeem_invite(data.account_id, data.device_id,
                                dec(data.public_key, 32), data.invite_code,
                                data.timestamp, data.nonce, dec(data.signature, 64), now())
            return {'enrolled': True}
        except Exception:
            raise HTTPException(403, 'Enrollment rejected')

    @app.post('/v1/admin/devices')
    async def device(request: Request, data: DeviceInput):
        admin(request)
        try:
            store.register_device(data.account_id, data.device_id, dec(data.public_key, 32))
            return {'registered': True}
        except (ValueError, sqlite3.IntegrityError):
            raise HTTPException(400, 'Invalid or existing device')

    @app.post('/v1/admin/devices/revoke')
    async def revoke(request: Request, data: RevokeInput):
        admin(request)
        try:
            store.revoke_device(data.account_id, data.device_id)
            return {'revoked': True}
        except ValueError:
            raise HTTPException(404, 'Device not found')

    @app.post('/v1/admin/checkout-ticket')
    async def checkout_ticket(request: Request, data: TicketInput):
        admin(request)
        if data.price_id not in prices:
            raise HTTPException(400, 'Unknown merchant price')
        try:
            account_id = strict_uuid(data.account_id)
        except ValueError:
            raise HTTPException(400, 'Invalid account ID')
        with store.lock:
            if not store.db.execute('SELECT 1 FROM accounts WHERE id=?', (account_id,)).fetchone() or store.is_closed(account_id):
                raise HTTPException(404, 'Account not found')
        return {'price_id': data.price_id,
                'custom_data': {'laptopwatch_checkout_ticket': ticket_for(account_id, data.price_id, checkout_secret, now())},
                'expires_in_seconds': 900}

    @app.post('/v1/billing/checkout-validation')
    async def validate_checkout(data: CheckoutValidateInput):
        # Refuse checkout before payment if this price/account authorization is
        # expired, altered or already bound to another subscription.
        try:
            if data.price_id not in prices:
                raise ValueError('unknown price')
            account_id = verify_ticket(data.ticket, checkout_secret, data.price_id, now())
            with store.lock:
                if not store.db.execute('SELECT 1 FROM accounts WHERE id=?', (account_id,)).fetchone() or store.is_closed(account_id):
                    raise ValueError('unknown account')
                bound = store.db.execute('SELECT 1 FROM checkout_bindings WHERE ticket_hash=?',
                      (hashlib.sha256(data.ticket.encode('ascii')).hexdigest(),)).fetchone()
            if bound:
                raise ValueError('already used checkout authorization')
            return JSONResponse({'valid': True}, headers={'Cache-Control': 'no-store'})
        except (ValueError, TypeError, KeyError):
            raise HTTPException(403, 'Checkout invitation invalid or expired')

    @app.post('/v1/billing/checkout-ticket')
    async def device_checkout_ticket(data: CheckoutInput):
        # This is customer-initiated purchase authorization, NOT permission
        # to grant an entitlement. Payment still requires signed Paddle events.
        try:
            account_id, device_id = strict_uuid(data.account_id), strict_uuid(data.device_id)
            if data.price_id not in prices or isinstance(data.timestamp, bool) or abs(now() - data.timestamp) > 60:
                raise ValueError('invalid checkout request')
            dec(data.nonce, 16)
            pub = store.device_public_key(account_id, device_id)
            challenge = (f'LW-CHECKOUT-1\n{account_id}\n{device_id}\n{data.price_id}\n'
                         f'{data.timestamp}\n{data.nonce}\n').encode()
            Ed25519PublicKey.from_public_bytes(pub).verify(dec(data.signature, 64), challenge)
            store.claim_nonce(device_id, data.nonce, now())
        except Exception as exc:
            raise HTTPException(403, 'Checkout authorization rejected') from exc
        return {'price_id': data.price_id,
                'custom_data': {'laptopwatch_checkout_ticket': ticket_for(account_id, data.price_id, checkout_secret, now())},
                'expires_in_seconds': 900}

    @app.post('/webhooks/paddle')
    async def paddle_webhook(request: Request):
        if request.headers.get('content-length', '').isdecimal() and int(request.headers['content-length']) > MAX_WEBHOOK:
            raise HTTPException(413, 'Too large')
        raw = await request.body()
        if len(raw) > MAX_WEBHOOK:
            raise HTTPException(413, 'Too large')
        try:
            verify_paddle(raw, request.headers.get('Paddle-Signature', ''), webhook_secret, now())
        except ValueError:
            raise HTTPException(401, 'Invalid webhook')
        try:
            result = store.apply_paddle(json.loads(raw), prices, checkout_secret)
        except (ValueError, KeyError, TypeError, json.JSONDecodeError, sqlite3.IntegrityError):
            raise HTTPException(422, 'Unsupported or unlinked subscription')
        return {'result': result}

    @app.post('/v1/billing/portal')
    async def customer_portal(data: LeaseInput):
        # Same wire request as lease, but a distinct signed domain prevents
        # swapping a lease proof into an authenticated billing action.
        try:
            account_id, device_id = strict_uuid(data.account_id), strict_uuid(data.device_id)
            if isinstance(data.timestamp, bool) or abs(now() - data.timestamp) > 60:
                raise ValueError('expired billing request')
            dec(data.nonce, 16)
            pub = store.device_public_key(account_id, device_id)
            msg = f'LW-PORTAL-1\n{account_id}\n{device_id}\n{data.timestamp}\n{data.nonce}\n'.encode()
            Ed25519PublicKey.from_public_bytes(pub).verify(dec(data.signature, 64), msg)
            store.claim_nonce(device_id, data.nonce, now())
            customer_id, subscription_id = store.portal_subscription(account_id, device_id)
        except Exception as exc:
            raise HTTPException(403, 'Billing proof rejected') from exc
        if portal_provider is None:
            raise HTTPException(503, 'Customer portal not configured')
        try:
            url = portal_provider(customer_id, subscription_id)
            if not isinstance(url, str) or not url.startswith('https://'):
                raise ValueError('invalid portal response')
            return {'url': url, 'expires': 'provider-managed; do not cache'}
        except Exception as exc:
            raise HTTPException(503, 'Customer portal unavailable') from exc

    @app.post('/v1/licenses/lease')
    async def lease(data: LeaseInput):
        try:
            account_id, device_id = strict_uuid(data.account_id), strict_uuid(data.device_id)
            timestamp = data.timestamp
            if isinstance(timestamp, bool) or abs(now() - timestamp) > 60:
                raise ValueError('stale request')
            dec(data.nonce, 16)
            signature = dec(data.signature, 64)
            pubkey, entitlement = store.entitlement(account_id, device_id, now(), prices)
            challenge = f'LW-LICENSE-1\n{account_id}\n{device_id}\n{timestamp}\n{data.nonce}\n'.encode()
            Ed25519PublicKey.from_public_bytes(pubkey).verify(signature, challenge)
            store.claim_nonce(device_id, data.nonce, now())
            payload = {'v': 1, 'issuer': 'LaptopWatch', 'account_id': account_id,
                       'device_id': device_id, 'issued_at': now(), **entitlement}
            body = enc(json_bytes(payload))
            return {'license': body + '.' + enc(signing_key.sign(body.encode())),
                    'format': 'LW-LICENSE-1', 'public_key': app.state.verify_public_key}
        except Exception as exc:
            # InvalidSignature and parsing errors fail closed, without leaking identities.
            raise HTTPException(403, 'Entitlement unavailable or proof rejected') from exc

    return app


def verify_license(token: str, public_key: bytes, account_id: str, device_id: str, now: int) -> dict:
    """Offline verification contract for native client implementation, no network required."""
    try:
        body, sig = token.split('.')
        Ed25519PublicKey.from_public_bytes(public_key).verify(dec(sig, 64), body.encode('ascii'))
        payload = json.loads(dec(body))
        if (payload.get('v') != 1 or payload.get('issuer') != 'LaptopWatch'
                or payload.get('account_id') != strict_uuid(account_id)
                or payload.get('device_id') != strict_uuid(device_id)
                or payload.get('plan') not in {'pro', 'business'}
                or not isinstance(payload.get('issued_at'), int)
                or not isinstance(payload.get('expires_at'), int)
                or payload['issued_at'] > now + 60 or not now < payload['expires_at']
                or payload['expires_at'] - payload['issued_at'] > LEASE_SECONDS
                or not isinstance(payload.get('max_devices'), int)
                or payload['max_devices'] < 1):
            raise ValueError('license claims rejected')
        return payload
    except Exception as exc:
        raise ValueError('license invalid or expired') from exc


def from_env():
    def need(name):
        value = os.environ.get(name, '')
        if not value:
            raise RuntimeError(f'{name} must be configured')
        return value
    key = serialization.load_pem_private_key(Path(need('LW_LICENSE_SIGNING_KEY_FILE')).read_bytes(), password=None)
    if not isinstance(key, Ed25519PrivateKey):
        raise RuntimeError('license issuer requires an Ed25519 signing key')
    prices = {}
    for name, seats in (('PRO', 3), ('BUSINESS', 10)):
        price_id = need('LW_PADDLE_PRICE_' + name)
        if price_id in prices:
            raise RuntimeError('merchant price IDs must be unique')
        prices[price_id] = (name.lower(), seats)
    return create_app(db_path=need('LW_BILLING_DB'), webhook_secret=need('LW_PADDLE_WEBHOOK_SECRET'),
        checkout_secret=need('LW_CHECKOUT_TICKET_SECRET'), operator_secret=need('LW_BILLING_OPERATOR_SECRET'),
        signing_key=key, prices=prices,
        paddle_client_token=os.environ.get('LW_PADDLE_CLIENT_TOKEN') or None,
        sandbox=os.environ.get('LW_PADDLE_SANDBOX','false').lower() == 'true',
        portal_provider=(lambda customer, subscription: paddle_portal_url(
            need('LW_PADDLE_API_KEY'), customer, subscription,
            sandbox=os.environ.get('LW_PADDLE_SANDBOX', 'false').lower() == 'true'))
            if os.environ.get('LW_PADDLE_API_KEY') else None,
        cancellation_provider=(lambda subscription: cancel_subscription_immediately(
            need('LW_PADDLE_API_KEY'), subscription,
            sandbox=os.environ.get('LW_PADDLE_SANDBOX', 'false').lower() == 'true'))
            if os.environ.get('LW_PADDLE_API_KEY') else None)


def main():
    import uvicorn
    uvicorn.run(from_env(), host='127.0.0.1', port=int(os.environ.get('LW_BILLING_PORT', '8899')),
                proxy_headers=False)


if __name__ == '__main__':
    main()

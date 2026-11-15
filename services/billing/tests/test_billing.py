"""No merchant credentials needed: realistic signed webhook and entitlement contracts."""
from datetime import datetime, timezone
import hashlib
import hmac
import json
import secrets
import time
import uuid

from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from cryptography.hazmat.primitives import serialization
from fastapi.testclient import TestClient
import pytest

from laptopwatch_billing.app import create_app, dec, enc, verify_license, verify_paddle

CLOCK = 1784200000
PRICE_PRO = 'pri_' + 'p' * 26
PRICE_BUSINESS = 'pri_' + 'b' * 26
SUB = 'sub_' + 's' * 26
CUSTOMER = 'ctm_' + 'c' * 26


def test_webhook_verifier_tampering_and_replay():
    body = b'{"a":1}'
    key = 'a' * 40
    signature = hmac.new(key.encode(), str(CLOCK).encode() + b':' + body, hashlib.sha256).hexdigest()
    header = f'ts={CLOCK};h1={signature}'
    verify_paddle(body, header, key, CLOCK)
    with pytest.raises(ValueError):
        verify_paddle(body + b' ', header, key, CLOCK)
    with pytest.raises(ValueError):
        verify_paddle(body, header, key, CLOCK + 301)
    with pytest.raises(ValueError):
        verify_paddle(body, 'ts=nan;h1=' + signature, key, CLOCK)


@pytest.fixture
def client(tmp_path):
    signing = Ed25519PrivateKey.generate()
    app = create_app(
        db_path=str(tmp_path / 'billing.sqlite'), webhook_secret='w' * 40,
        checkout_secret='c' * 40, operator_secret='o' * 40, signing_key=signing,
        prices={PRICE_PRO: ('pro', 3), PRICE_BUSINESS: ('business', 10)},
        clock=lambda: CLOCK)
    account_id = str(uuid.uuid4())
    device_id = str(uuid.uuid4())
    device_key = Ed25519PrivateKey.generate()
    with TestClient(app) as api:
        yield api, app, account_id, device_id, device_key, signing


def admin(api, path, payload):
    return api.post(path, json=payload, headers={'Authorization': 'Bearer ' + 'o' * 40})


def auth_setup(context):
    api, app, account, device, key, signing = context
    assert admin(api, '/v1/admin/accounts', {'account_id': account}).status_code == 200
    raw_public = key.public_key().public_bytes(
        encoding=serialization.Encoding.Raw, format=serialization.PublicFormat.Raw)
    assert admin(api, '/v1/admin/devices', {
        'account_id': account, 'device_id': device, 'public_key': enc(raw_public)}).status_code == 200
    return admin(api, '/v1/admin/checkout-ticket', {'account_id': account, 'price_id': PRICE_PRO}).json()['custom_data']


def webhook(api, data, event_type='subscription.created', event_time=CLOCK, event_id=None):
    ts = datetime.fromtimestamp(event_time, timezone.utc).isoformat().replace('+00:00', 'Z')
    body = json.dumps({'event_id': event_id or 'evt_' + secrets.token_hex(13),
                       'event_type': event_type, 'occurred_at': ts, 'data': data}, separators=(',', ':')).encode()
    signature = hmac.new(('w'*40).encode(), str(CLOCK).encode() + b':' + body, hashlib.sha256).hexdigest()
    return api.post('/webhooks/paddle', content=body,
                    headers={'Paddle-Signature': f'ts={CLOCK};h1={signature}',
                             'Content-Type': 'application/json'})


def subscription(custom, status='active', price=PRICE_PRO, end=CLOCK+7200):
    return {'id': SUB, 'customer_id': CUSTOMER, 'status': status,
            'items': [{'price': {'id': price}}],
            'current_billing_period': {'ends_at': datetime.fromtimestamp(end, timezone.utc).isoformat()},
            'custom_data': custom}


def lease(api, account, device, key, *, nonce=None):
    nonce = nonce or enc(secrets.token_bytes(16))
    msg = f'LW-LICENSE-1\n{account}\n{device}\n{CLOCK}\n{nonce}\n'.encode()
    return api.post('/v1/licenses/lease', json={'account_id': account, 'device_id': device,
                                                'timestamp': CLOCK, 'nonce': nonce,
                                                'signature': enc(key.sign(msg))})


def test_no_anonymous_provisioning_or_checkout(client):
    api, app, account, device, *_ = client
    assert api.post('/v1/admin/accounts', json={'account_id': account}).status_code == 401
    assert api.post('/v1/admin/checkout-ticket', json={'account_id': account, 'price_id': PRICE_PRO}).status_code == 401
    assert lease(api, account, device, Ed25519PrivateKey.generate()).status_code == 403


def test_licensing_requires_signed_paddle_and_device_possession(client):
    api, app, account, device, key, signing = client
    custom = auth_setup(client)
    data = subscription(custom)
    raw = json.dumps(data).encode()
    assert api.post('/webhooks/paddle', content=raw).status_code == 401
    assert lease(api, account, device, key).status_code == 403
    assert webhook(api, data).json()['result'] == 'processed'
    wrong = Ed25519PrivateKey.generate()
    assert lease(api, account, device, wrong).status_code == 403
    nonce = enc(secrets.token_bytes(16))
    response = lease(api, account, device, key, nonce=nonce)
    assert response.status_code == 200, response.text
    assert lease(api, account, device, key, nonce=nonce).status_code == 403
    license_data = response.json()
    public_key = signing.public_key().public_bytes(
        encoding=serialization.Encoding.Raw, format=serialization.PublicFormat.Raw)
    claims = verify_license(license_data['license'], public_key, account, device, CLOCK)
    assert claims['plan'] == 'pro'
    assert claims['expires_at'] == CLOCK + 3600
    with pytest.raises(ValueError):
        verify_license(license_data['license'], public_key, account, str(uuid.uuid4()), CLOCK)
    with pytest.raises(ValueError):
        verify_license(license_data['license'], public_key, account, device, CLOCK + 3601)


def test_checkout_ticket_cannot_forge_account_or_price(client):
    api, app, account, device, key, signing = client
    custom = auth_setup(client)
    tampered = dict(custom)
    tampered['laptopwatch_checkout_ticket'] += 'a'
    assert webhook(api, subscription(tampered)).status_code == 422
    assert webhook(api, subscription(custom, price=PRICE_BUSINESS)).status_code == 422
    assert webhook(api, subscription(custom)).status_code == 200


def test_late_and_duplicate_events_cannot_reactivate_subscription(client):
    api, app, account, device, key, signing = client
    custom = auth_setup(client)
    first_id = 'evt_' + '1'*26
    assert webhook(api, subscription(custom), event_id=first_id).status_code == 200
    assert webhook(api, subscription(custom), event_id=first_id).json()['result'] == 'duplicate'
    assert webhook(api, subscription({}, status='canceled'), event_type='subscription.canceled',
                   event_time=CLOCK + 2).status_code == 200
    assert lease(api, account, device, key).status_code == 403
    assert webhook(api, subscription({}, status='active'), event_type='subscription.updated',
                   event_time=CLOCK + 1).json()['result'] == 'stale'
    assert lease(api, account, device, key).status_code == 403


def test_revocation_and_past_due_deny(client):
    api, app, account, device, key, signing = client
    custom = auth_setup(client)
    assert webhook(api, subscription(custom)).status_code == 200
    assert webhook(api, subscription({}, status='past_due'), event_type='subscription.past_due',
                   event_time=CLOCK+2).status_code == 200
    assert lease(api, account, device, key).status_code == 403
    assert webhook(api, subscription({}, status='active'), event_type='subscription.updated',
                   event_time=CLOCK+3).status_code == 200
    assert admin(api, '/v1/admin/devices/revoke', {'account_id': account, 'device_id': device}).status_code == 200
    assert lease(api, account, device, key).status_code == 403


def test_seat_limits_and_missing_period(client):
    api, app, account, device, key, signing = client
    custom = auth_setup(client)
    assert webhook(api, subscription(custom, end=CLOCK-1)).status_code == 200
    assert lease(api, account, device, key).status_code == 403


def test_unknown_event_ignored_no_entitlement(client):
    api, app, account, device, key, signing = client
    custom = auth_setup(client)
    assert webhook(api, subscription(custom), event_type='transaction.completed').json()['result'] == 'ignored'
    assert lease(api, account, device, key).status_code == 403


def test_device_caps_fail_closed(client):
    api, app, account, device, key, signing = client
    custom = auth_setup(client)
    for i in range(3):
        dev = str(uuid.uuid4())
        pkey = Ed25519PrivateKey.generate().public_key().public_bytes(
            serialization.Encoding.Raw, serialization.PublicFormat.Raw)
        assert admin(api, '/v1/admin/devices', {'account_id': account, 'device_id': dev,
                                                'public_key': enc(pkey)}).status_code == 200
    assert webhook(api, subscription(custom)).status_code == 200
    assert lease(api, account, device, key).status_code == 403


def test_customer_portal_requires_distinct_signed_device_proof(tmp_path):
    signing = Ed25519PrivateKey.generate()
    seen = []
    app = create_app(db_path=str(tmp_path/'billing.db'), webhook_secret='w'*40,
        checkout_secret='c'*40, operator_secret='o'*40, signing_key=signing,
        prices={PRICE_PRO: ('pro', 3)}, clock=lambda: CLOCK,
        portal_provider=lambda customer, sub: seen.append((customer, sub)) or
            'https://customer-portal.paddle.com/cpl_x?token=example')
    account, device, key = str(uuid.uuid4()), str(uuid.uuid4()), Ed25519PrivateKey.generate()
    with TestClient(app) as api:
        assert admin(api, '/v1/admin/accounts', {'account_id': account}).status_code == 200
        pub = key.public_key().public_bytes(serialization.Encoding.Raw, serialization.PublicFormat.Raw)
        assert admin(api, '/v1/admin/devices', {'account_id': account, 'device_id': device,
                                                'public_key': enc(pub)}).status_code == 200
        ticket = admin(api, '/v1/admin/checkout-ticket', {'account_id': account,
                                                          'price_id': PRICE_PRO}).json()['custom_data']
        assert webhook(api, subscription(ticket)).status_code == 200
        nonce = enc(secrets.token_bytes(16))
        msg = f'LW-PORTAL-1\n{account}\n{device}\n{CLOCK}\n{nonce}\n'.encode()
        payload = {'account_id': account, 'device_id': device, 'timestamp': CLOCK,
                   'nonce': nonce, 'signature': enc(key.sign(msg))}
        resp = api.post('/v1/billing/portal', json=payload)
        assert resp.status_code == 200, resp.text
        assert resp.json()['url'].startswith('https://customer-portal.paddle.com/')
        assert seen == [(CUSTOMER, SUB)]
        assert api.post('/v1/billing/portal', json=payload).status_code == 403
        assert lease(api, account, device, key).status_code == 200


def test_cancellation_wins_equal_second_timestamp(client):
    api, app, account, device, key, signing = client
    ticket = auth_setup(client)
    assert webhook(api, subscription(ticket)).status_code == 200
    # Same second: old 1s-granularity logic incorrectly ignored cancellation.
    assert webhook(api, subscription({}, status='canceled'),
                   event_type='subscription.canceled').status_code == 200
    assert lease(api, account, device, key).status_code == 403

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


def auth_setup(context, *, paid=True):
    api, app, account, device, key, signing = context
    assert admin(api, '/v1/admin/accounts', {'account_id': account}).status_code == 200
    raw_public = key.public_key().public_bytes(
        encoding=serialization.Encoding.Raw, format=serialization.PublicFormat.Raw)
    assert admin(api, '/v1/admin/devices', {
        'account_id': account, 'device_id': device, 'public_key': enc(raw_public)}).status_code == 200
    if paid:
        assert completed_transaction(api).json()['result'] == 'payment-recorded'
    return admin(api, '/v1/admin/checkout-ticket', {'account_id': account, 'price_id': PRICE_PRO}).json()['custom_data']


def webhook(api, data, event_type='subscription.created', event_time=CLOCK, event_id=None):
    ts = datetime.fromtimestamp(event_time, timezone.utc).isoformat().replace('+00:00', 'Z')
    body = json.dumps({'event_id': event_id or 'evt_' + secrets.token_hex(13),
                       'event_type': event_type, 'occurred_at': ts, 'data': data}, separators=(',', ':')).encode()
    signature = hmac.new(('w'*40).encode(), str(CLOCK).encode() + b':' + body, hashlib.sha256).hexdigest()
    return api.post('/webhooks/paddle', content=body,
                    headers={'Paddle-Signature': f'ts={CLOCK};h1={signature}',
                             'Content-Type': 'application/json'})


def completed_transaction(api, *, start=CLOCK-3600, end=CLOCK+7200, tx='t'*26):
    return webhook(api, {'id': 'txn_' + tx, 'subscription_id': SUB,
                         'customer_id': CUSTOMER, 'status': 'completed',
                         'billing_period': {
                            'starts_at': datetime.fromtimestamp(start, timezone.utc).isoformat(),
                            'ends_at': datetime.fromtimestamp(end, timezone.utc).isoformat()}},
                   event_type='transaction.completed')


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
    assert webhook(api, subscription(custom), event_type='transaction.created').json()['result'] == 'ignored'
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
        assert completed_transaction(api).status_code == 200
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


def test_active_subscription_without_completed_payment_is_not_entitled(client):
    api, app, account, device, key, signing = client
    custom = auth_setup(client, paid=False)
    assert webhook(api, subscription(custom)).status_code == 200
    assert lease(api, account, device, key).status_code == 403
    assert completed_transaction(api).json()['result'] == 'payment-recorded'
    assert lease(api, account, device, key).status_code == 200


def test_refund_or_chargeback_blocks_entitlement(client):
    api, app, account, device, key, signing = client
    custom = auth_setup(client)
    assert webhook(api, subscription(custom)).status_code == 200
    adjustment = {'id': 'adj_'+'a'*26, 'subscription_id': SUB,
                  'customer_id': CUSTOMER, 'action': 'refund', 'status': 'approved'}
    assert webhook(api, adjustment, event_type='adjustment.created').json()['result'] == 'entitlement-held'
    assert lease(api, account, device, key).status_code == 403
    # Out-of-order active update must NOT override a financial adjustment hold.
    assert webhook(api, subscription({}, status='active'),
                   event_type='subscription.updated', event_time=CLOCK+10).status_code == 200
    assert lease(api, account, device, key).status_code == 403


def test_signed_device_checkout_ticket_mints_price_bound_authorization(client):
    api, app, account, device, key, signing = client
    auth_setup(client, paid=False)
    nonce = enc(secrets.token_bytes(16))
    def payload(signer, price=PRICE_PRO, nonce=nonce):
        challenge = (f'LW-CHECKOUT-1\n{account}\n{device}\n{price}\n'
                     f'{CLOCK}\n{nonce}\n').encode()
        return {'account_id': account, 'device_id': device, 'price_id': price,
                'timestamp': CLOCK, 'nonce': nonce, 'signature': enc(signer.sign(challenge))}
    endpoint = '/v1/billing/checkout-ticket'
    assert api.post(endpoint, json=payload(Ed25519PrivateKey.generate())).status_code == 403
    good = api.post(endpoint, json=payload(key))
    assert good.status_code == 200, good.text
    assert good.json()['price_id'] == PRICE_PRO
    assert api.post(endpoint, json=payload(key)).status_code == 403
    tampered = payload(key, PRICE_PRO, enc(secrets.token_bytes(16)))
    tampered['price_id'] = PRICE_BUSINESS
    assert api.post(endpoint, json=tampered).status_code == 403
    assert webhook(api, subscription(good.json()['custom_data'])).status_code == 200
    assert lease(api, account, device, key).status_code == 403
    assert completed_transaction(api).status_code == 200
    assert lease(api, account, device, key).status_code == 200


def test_subscription_renewal_requires_payment_for_that_period(client):
    api, app, account, device, key, signing = client
    custom = auth_setup(client)
    assert webhook(api, subscription(custom)).status_code == 200
    assert lease(api, account, device, key).status_code == 200
    renewal_end = CLOCK + 35 * 86400
    # Merchant reports new period before the corresponding payment completes.
    assert webhook(api, subscription({}, end=renewal_end),
                   event_type='subscription.updated', event_time=CLOCK+10).status_code == 200
    assert lease(api, account, device, key).status_code == 403
    assert completed_transaction(api, start=CLOCK+7200, end=renewal_end,
                                 tx='r'*26).status_code == 200
    assert lease(api, account, device, key).status_code == 200


def test_payment_period_cannot_be_missing_or_forged(client):
    api, app, account, device, key, signing = client
    ticket = auth_setup(client, paid=False)
    assert webhook(api, subscription(ticket)).status_code == 200
    assert webhook(api, {'id': 'txn_' + 'z'*26, 'subscription_id': SUB,
                        'customer_id': CUSTOMER, 'status': 'completed'},
                   event_type='transaction.completed').status_code == 422
    assert lease(api, account, device, key).status_code == 403
    assert completed_transaction(api, end=CLOCK + 40*86400, tx='y'*26).status_code == 200
    assert lease(api, account, device, key).status_code == 403
    assert completed_transaction(api).status_code == 200
    assert lease(api, account, device, key).status_code == 200


def test_operator_invite_requires_key_proof_and_is_single_use(client):
    api, app, account, device, key, signing = client
    assert admin(api, '/v1/admin/accounts', {'account_id':account}).status_code == 200
    assert api.post('/v1/admin/invitations', json={'account_id': account}).status_code == 401
    issue = admin(api, '/v1/admin/invitations', {'account_id':account})
    assert issue.status_code == 200
    invite = issue.json()['invite_code']
    pub = key.public_key().public_bytes(serialization.Encoding.Raw,
                                        serialization.PublicFormat.Raw)
    nonce = enc(secrets.token_bytes(16))
    digest = hashlib.sha256(invite.encode('ascii')).hexdigest()
    msg = (f'LW-ENROLL-1\n{account}\n{device}\n{enc(pub)}\n'
           f'{CLOCK}\n{nonce}\n{digest}\n').encode()
    payload = {'account_id':account, 'device_id':device, 'public_key':enc(pub),
               'invite_code':invite, 'timestamp':CLOCK, 'nonce':nonce,
               'signature':enc(key.sign(msg))}
    wrong = dict(payload)
    wrong['signature'] = enc(Ed25519PrivateKey.generate().sign(msg))
    assert api.post('/v1/accounts/enroll', json=wrong).status_code == 403
    assert api.post('/v1/accounts/enroll', json=payload).json() == {'enrolled': True}
    assert api.post('/v1/accounts/enroll', json=payload).status_code == 403
    assert api.post('/v1/billing/checkout-ticket', json={'account_id':account,
           'device_id':device, 'price_id':PRICE_PRO,
           'timestamp':CLOCK, 'nonce':nonce, 'signature':enc(key.sign(b'invalid'))}).status_code == 403


def test_invitation_cannot_enroll_other_account_and_expires(client):
    api, app, account, device, key, signing = client
    assert admin(api, '/v1/admin/accounts', {'account_id':account}).status_code == 200
    invite = admin(api, '/v1/admin/invitations', {'account_id':account}).json()['invite_code']
    pub = key.public_key().public_bytes(serialization.Encoding.Raw,
                                        serialization.PublicFormat.Raw)
    nonce=enc(secrets.token_bytes(16))
    other=str(uuid.uuid4())
    digest=hashlib.sha256(invite.encode()).hexdigest()
    msg=f'LW-ENROLL-1\n{other}\n{device}\n{enc(pub)}\n{CLOCK}\n{nonce}\n{digest}\n'.encode()
    payload={'account_id':other,'device_id':device,'public_key':enc(pub),'invite_code':invite,
             'timestamp':CLOCK,'nonce':nonce,'signature':enc(key.sign(msg))}
    assert api.post('/v1/accounts/enroll', json=payload).status_code == 403
    app.state.store.db.execute('UPDATE enrollment_invites SET expires_at=?', (CLOCK-1,))
    msg=f'LW-ENROLL-1\n{account}\n{device}\n{enc(pub)}\n{CLOCK}\n{nonce}\n{digest}\n'.encode()
    payload.update(account_id=account, signature=enc(key.sign(msg)))
    assert api.post('/v1/accounts/enroll', json=payload).status_code == 403


def test_checkout_page_cannot_publish_merchant_secrets(client):
    api, *rest = client
    html = api.get('/checkout')
    assert html.status_code == 200
    assert 'Content-Security-Policy' in html.headers
    assert html.headers['Referrer-Policy'] == 'no-referrer'
    assert api.get('/checkout.js').status_code == 200
    assert api.get('/v1/billing/public-config').status_code == 503


def test_public_config_exposes_only_public_client_token(tmp_path):
    app = create_app(db_path=str(tmp_path/'merchant.db'), webhook_secret='w'*40,
        checkout_secret='c'*40, operator_secret='o'*40,
        signing_key=Ed25519PrivateKey.generate(), prices={PRICE_PRO:('pro',3)},
        paddle_client_token='test_validpublictoken', sandbox=True)
    with TestClient(app) as api:
        response=api.get('/v1/billing/public-config')
        assert response.status_code == 200
        assert response.json() == {'client_token':'test_validpublictoken',
                                   'environment':'sandbox','prices':[PRICE_PRO]}
        assert 'secret' not in response.text.lower()


def test_checkout_ticket_requires_live_validation_and_single_subscription_binding(client):
    api, app, account, device, key, signing = client
    custom = auth_setup(client, paid=False)
    ticket = custom['laptopwatch_checkout_ticket']
    valid = api.post('/v1/billing/checkout-validation', json={'price_id':PRICE_PRO,'ticket':ticket})
    assert valid.status_code == 200
    assert api.post('/v1/billing/checkout-validation', json={'price_id':PRICE_BUSINESS,
               'ticket':ticket}).status_code == 403
    assert webhook(api, subscription(custom)).status_code == 200
    assert api.post('/v1/billing/checkout-validation', json={'price_id':PRICE_PRO,
               'ticket':ticket}).status_code == 403
    other = subscription(custom)
    other['id'] = 'sub_'+'o'*26
    assert webhook(api, other).status_code == 422

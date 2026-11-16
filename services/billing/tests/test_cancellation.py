"""Provider cancellation contract and durable closure retry tests."""
import json
import uuid
import pytest
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from fastapi.testclient import TestClient
from laptopwatch_billing.app import create_app
from laptopwatch_billing.cancellation import CancellationUnconfirmed, cancel_subscription_immediately

SUB = 'sub_' + 'a' * 26
CUSTOMER = 'ctm_' + 'b' * 26
PRICE = 'pri_' + 'c' * 26
KEY = 'pdl_test_' + 'x' * 40
NOW = 1784210000


class Response:
    status = 200
    def __init__(self, value):
        self.value = value
    def __enter__(self):
        return self
    def __exit__(self, *_args):
        return False
    def read(self, size):
        return self.value[:size]


def transport(payload, calls):
    def opener(request, timeout):
        calls.append((request, timeout))
        return Response(json.dumps(payload).encode())
    return opener


def test_cancel_paddle_uses_immediate_endpoint_and_checks_exact_result():
    calls = []
    cancel_subscription_immediately(KEY, SUB, sandbox=True,
        opener=transport({'data': {'id': SUB, 'status': 'canceled'}}, calls))
    request, timeout = calls.pop()
    assert timeout == 8 and request.get_method() == 'POST'
    assert request.full_url == f'https://sandbox-api.paddle.com/subscriptions/{SUB}/cancel'
    assert json.loads(request.data)['effective_from'] == 'immediately'
    assert request.get_header('Authorization') == 'Bearer ' + KEY


@pytest.mark.parametrize('body', [
    {'data': {'id': SUB, 'status': 'active'}},
    {'data': {'id': 'sub_' + 'd'*26, 'status': 'canceled'}},
    {'data': None},
    {},
])
def test_provider_not_cancelled_or_wrong_identity_is_never_success(body):
    with pytest.raises(CancellationUnconfirmed):
        cancel_subscription_immediately(KEY, SUB, opener=transport(body, []))


def test_invalid_inputs_and_transport_failure_do_not_mark_cancellation():
    for key, sub in [('', SUB), (KEY, 'malformed')]:
        with pytest.raises(CancellationUnconfirmed):
            cancel_subscription_immediately(key, sub)
    def offline(*_args, **_kwargs):
        raise OSError('network unavailable')
    with pytest.raises(CancellationUnconfirmed):
        cancel_subscription_immediately(KEY, SUB, opener=offline)


def app_for(tmp_path, provider=None):
    app = create_app(db_path=str(tmp_path / 'billing.db'), webhook_secret='w'*40,
        checkout_secret='c'*40, operator_secret='o'*40,
        signing_key=Ed25519PrivateKey.generate(), prices={PRICE: ('pro',3)},
        clock=lambda: NOW, cancellation_provider=provider)
    account = str(uuid.uuid4())
    app.state.store.create_account(account)
    with app.state.store.tx() as db:
        db.execute('INSERT INTO subscriptions VALUES (?,?,?,?,?,?,?)',
                   (SUB, account, CUSTOMER, PRICE, 'active', NOW+3600, NOW*1_000_000))
    return app, account


def headers():
    return {'Authorization': 'Bearer ' + 'o'*40}


def test_closure_queues_provider_failure_then_operator_retries(tmp_path):
    calls = []
    def provider(subscription):
        calls.append(subscription)
        if len(calls) == 1:
            raise OSError('provider offline')
    app, account = app_for(tmp_path, provider)
    with TestClient(app) as api:
        response = api.post('/v1/admin/accounts/close',
                            json={'account_id': account}, headers=headers())
        assert response.status_code == 200
        assert response.json()['result'] == 'closed'
        assert response.json()['provider_cancellation_required'] is True
        assert response.json()['cancellation_pending_count'] == 1
        assert api.get('/v1/admin/closures/pending').status_code == 401
        assert api.get('/v1/admin/closures/pending', headers=headers()).json() == {
            'pending_cancellations': 1}
        retry = api.post('/v1/admin/closures/retry', json={'account_id': account},
                         headers=headers())
        assert retry.status_code == 200
        assert retry.json()['provider_cancellation_required'] is False
        assert calls == [SUB, SUB]
        assert app.state.store.pending_cancellation_count(account) == 0
    app.state.store.db.close()


def test_without_provider_pending_job_is_not_lost(tmp_path):
    app, account = app_for(tmp_path)
    with TestClient(app) as api:
        response = api.post('/v1/admin/accounts/close',
                            json={'account_id': account}, headers=headers())
        assert response.status_code == 200
        assert response.json()['cancellation_pending_count'] == 1
        assert app.state.store.pending_cancellations(account) == [SUB]
        assert app.state.store.close_account(account, NOW) == 'already_closed'
        assert app.state.store.pending_cancellations(account) == [SUB]
    app.state.store.db.close()


def test_ambiguous_post_is_recovered_by_authoritative_provider_get():
    calls = []
    def opener(request, timeout):
        calls.append((request.get_method(), request.full_url))
        if request.get_method() == 'POST':
            raise OSError('The provider may have applied cancellation before timeout')
        return Response(json.dumps({'data': {'id': SUB, 'status': 'canceled'}}).encode())
    cancel_subscription_immediately(KEY, SUB, opener=opener)
    assert calls == [
        ('POST', f'https://api.paddle.com/subscriptions/{SUB}/cancel'),
        ('GET', f'https://api.paddle.com/subscriptions/{SUB}'),
    ]

"""Cross-component contract and fail-closed tests for desktop commercial leases."""
from __future__ import annotations
import json
import secrets
import uuid
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from cryptography.hazmat.primitives import serialization
import pytest
from laptopwatch.licensing import (BillingClient, PaidLicense, LicenseUnavailable,
                                    b64, verify_license, validate_service_url)
CLOCK = 1784200000

def sample(tmp_path):
    signer = Ed25519PrivateKey.generate()
    issuer = Ed25519PrivateKey.generate()
    account, device = str(uuid.uuid4()), str(uuid.uuid4())
    pub = b64(issuer.public_key().public_bytes(serialization.Encoding.Raw,
                                               serialization.PublicFormat.Raw))
    claims = {'v':1, 'issuer':'LaptopWatch', 'account_id':account, 'device_id':device,
              'issued_at':CLOCK, 'expires_at':CLOCK+3500, 'plan':'pro',
              'subscription_id':'sub_'+'a'*26, 'max_devices':3}
    body = b64(json.dumps(claims, separators=(',', ':')).encode())
    token = body+'.'+b64(issuer.sign(body.encode()))
    client = BillingClient('https://billing.example.com', account, device,
                           signer, clock=lambda:CLOCK)
    cache = PaidLicense(tmp_path, account, device, pub)
    return client, cache, token

def test_signed_lease_is_short_lived_device_bound_and_pinned(tmp_path):
    client, cache, token = sample(tmp_path)
    assert cache.current(CLOCK) is None
    assert cache.allow('local', now=CLOCK)
    assert not cache.allow('remote_location', now=CLOCK)
    assert cache.store(token, now=CLOCK)['plan']=='pro'
    assert cache.allow('remote_location', now=CLOCK)
    assert not cache.allow('remote_location', now=CLOCK+3500)
    assert not cache.allow('unknown', now=CLOCK)
    with pytest.raises(LicenseUnavailable):
        verify_license(token, cache.trusted_issuer, cache.account_id,
                       str(uuid.uuid4()), now=CLOCK)
    with pytest.raises(LicenseUnavailable):
        verify_license(token, b64(secrets.token_bytes(32)),
                       cache.account_id, cache.device_id, now=CLOCK)

def test_device_proofs_checkout_and_portal(tmp_path):
    client, cache, token = sample(tmp_path)
    paths=[]
    def fake(path, payload):
        paths.append(path)
        if path == '/v1/billing/checkout-ticket':
            return {'price_id':payload['price_id'],
                    'custom_data':{'laptopwatch_checkout_ticket':'dummy'}}
        if path == '/v1/billing/portal':
            return {'url':'https://customer-portal.paddle.com/cpl_x?token=secret'}
        if path == '/v1/licenses/lease':
            return {'license':token, 'public_key':b64(secrets.token_bytes(32))}
        return {'enrolled':True}
    client._post = fake
    assert client.checkout('pri_'+'a'*26)['price_id']=='pri_'+'a'*26
    assert client.portal().startswith('https://customer-portal.paddle.com/')
    assert client.lease(cache)['plan']=='pro'
    client.enroll(b64(secrets.token_bytes(32)))
    assert paths == ['/v1/billing/checkout-ticket','/v1/billing/portal',
                     '/v1/licenses/lease','/v1/accounts/enroll']

def test_bad_urls_and_missing_issuer_rejected(tmp_path):
    client, cache, token=sample(tmp_path)
    for url in ('http://merchant.example.com', 'https://name:pass@example.com',
                'https://billing.example.com:8443', 'https://127.0.0.1',
                'https://billing.example.invalid', 'https://billing.example.com/risky',
                'https://billing.example.com?token=secret'):
        with pytest.raises(LicenseUnavailable):
            validate_service_url(url)
    with pytest.raises(LicenseUnavailable):
        client.checkout('not-an-approved-price')
    assert not PaidLicense(tmp_path, cache.account_id, cache.device_id, '').allow('remote_incidents')

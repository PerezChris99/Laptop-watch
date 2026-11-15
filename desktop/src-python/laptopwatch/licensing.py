"""Desktop verification and use of operator-approved LaptopWatch commercial licenses.

The merchant-supplied response public_key is NOT trusted. Paid privileges are
never granted unless a previously pinned issuer key verifies the signed lease.
Free/local security controls work independently of the payment service.
"""
from __future__ import annotations

import base64
import json
import os
from pathlib import Path
import re
import secrets
import time
from urllib.parse import urlparse
from urllib.request import Request, HTTPSHandler, HTTPRedirectHandler, build_opener
from hashlib import sha256
from uuid import UUID

from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey, Ed25519PublicKey
from cryptography.hazmat.primitives import serialization

# Release build injects reviewed, pinned Ed25519 issuer public key. No remote
# URL or network response may set or replace this key. Default fails closed.
try:
    from .license_trust import TRUSTED_ISSUER_PUBLIC_KEY_B64URL
except ImportError:
    TRUSTED_ISSUER_PUBLIC_KEY_B64URL = ''

_B64 = re.compile(r'^[A-Za-z0-9_-]+$')


class LicenseUnavailable(ValueError):
    """License missing, expired, or cryptographically untrusted."""


def b64(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).decode('ascii').rstrip('=')


def raw64(value: str, expected: int | None = None) -> bytes:
    if not isinstance(value, str) or len(value) > 4096 or not _B64.fullmatch(value):
        raise LicenseUnavailable('invalid encoding')
    try:
        data = base64.urlsafe_b64decode(value + '=' * (-len(value) % 4))
    except (ValueError, base64.binascii.Error) as exc:
        raise LicenseUnavailable('invalid encoding') from exc
    if b64(data) != value or (expected is not None and len(data) != expected):
        raise LicenseUnavailable('invalid encoded length')
    return data


def _uuid(value: str) -> str:
    try:
        normalized = str(UUID(value))
        if value != normalized:
            raise ValueError()
        return normalized
    except (TypeError, ValueError, AttributeError) as exc:
        raise LicenseUnavailable('invalid device or account ID') from exc


def verify_license(token: str, trusted_issuer: str, account_id: str,
                   device_id: str, *, now: int | None = None) -> dict:
    """Offline, bounded lease verified against a separately pinned issuer."""
    now = int(time.time()) if now is None else now
    try:
        body, signature = token.split('.')
        issuer = Ed25519PublicKey.from_public_bytes(raw64(trusted_issuer, 32))
        issuer.verify(raw64(signature, 64), body.encode('ascii'))
        payload = json.loads(raw64(body))
        if (not isinstance(payload, dict) or payload.get('v') != 1
            or payload.get('issuer') != 'LaptopWatch'
            or payload.get('account_id') != _uuid(account_id)
            or payload.get('device_id') != _uuid(device_id)
            or payload.get('plan') not in {'pro', 'business'}
            or type(payload.get('issued_at')) is not int
            or type(payload.get('expires_at')) is not int
            or type(payload.get('max_devices')) is not int
            or payload['max_devices'] not in range(1, 101)
            or payload['issued_at'] > now + 60
            or payload['expires_at'] <= now
            or payload['expires_at'] <= payload['issued_at']
            or payload['expires_at'] - payload['issued_at'] > 3600
            or not isinstance(payload.get('subscription_id'), str)
            or not re.fullmatch(r'sub_[a-z0-9]{26}', payload['subscription_id'])):
            raise LicenseUnavailable('invalid lease claims')
        return payload
    except Exception as exc:
        raise LicenseUnavailable('license absent, untrusted or expired') from exc


def validate_service_url(value: str) -> str:
    if not isinstance(value, str) or len(value) > 256:
        raise LicenseUnavailable('invalid billing URL')
    url = urlparse(value)
    host = url.hostname or ''
    if (url.scheme != 'https' or url.username or url.password or
        url.port not in (None, 443) or url.path not in ('', '/') or
        url.query or url.fragment or not host or '.' not in host or
        host.endswith('.invalid') or re.fullmatch(r'[0-9.]+', host)):
        raise LicenseUnavailable('billing requires trusted HTTPS DNS')
    return 'https://' + host.lower()


class _NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class PaidLicense:
    """Atomic storage for non-secret device-specific signed leases."""
    def __init__(self, state_dir: Path, account_id: str, device_id: str,
                 trusted_issuer: str = TRUSTED_ISSUER_PUBLIC_KEY_B64URL):
        self.path = Path(state_dir) / 'license-lease.json'
        self.account_id, self.device_id = _uuid(account_id), _uuid(device_id)
        self.trusted_issuer = trusted_issuer

    def current(self, now: int | None = None) -> dict | None:
        try:
            if self.path.stat().st_size > 8192:
                return None
            token = json.loads(self.path.read_text(encoding='utf-8'))['license']
            return verify_license(token, self.trusted_issuer, self.account_id,
                                  self.device_id, now=now)
        except (OSError, ValueError, KeyError, TypeError):
            return None

    def signed_token(self, now: int | None = None) -> str | None:
        try:
            if self.current(now=now) is None:
                return None
            token=json.loads(self.path.read_text(encoding='utf-8'))['license']
            return token if isinstance(token,str) else None
        except (OSError, ValueError, KeyError, TypeError):
            return None

    def allow(self, feature: str, now: int | None = None) -> bool:
        if feature == 'local':
            return True
        if feature not in {'remote_location', 'remote_incidents', 'wan_media'}:
            return False
        return self.current(now=now) is not None

    def store(self, token: str, *, now: int | None = None) -> dict:
        claims = verify_license(token, self.trusted_issuer, self.account_id,
                                self.device_id, now=now)
        self.path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
        temp = self.path.with_name('.license-lease-' + secrets.token_hex(8) + '.tmp')
        fd = os.open(temp, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        try:
            with os.fdopen(fd, 'w', encoding='utf-8') as file:
                json.dump({'license': token}, file)
                file.flush()
                os.fsync(file.fileno())
            os.replace(temp, self.path)
            if os.name == 'posix':
                os.chmod(self.path, 0o600)
        finally:
            temp.unlink(missing_ok=True)
        return claims


class BillingClient:
    """Signed requests over normal CA HTTPS. Never follow redirects."""
    def __init__(self, base_url: str, account_id: str, device_id: str,
                 signing_key: Ed25519PrivateKey, *, opener=None, clock=None):
        self.base_url = validate_service_url(base_url)
        self.account_id, self.device_id = _uuid(account_id), _uuid(device_id)
        self.signing_key = signing_key
        self.clock = clock or (lambda: int(time.time()))
        self.opener = opener or build_opener(HTTPSHandler(), _NoRedirect())

    def _post(self, path: str, payload: dict) -> dict:
        if path not in {'/v1/accounts/enroll', '/v1/billing/checkout-ticket',
                        '/v1/billing/portal', '/v1/licenses/lease'}:
            raise LicenseUnavailable('unsupported billing request')
        body = json.dumps(payload, separators=(',', ':'), ensure_ascii=True).encode('ascii')
        if len(body) > 4096:
            raise LicenseUnavailable('billing request too large')
        req = Request(self.base_url + path, data=body, method='POST',
                      headers={'Content-Type': 'application/json', 'Accept': 'application/json'})
        with self.opener.open(req, timeout=8) as response:
            if response.status != 200:
                raise LicenseUnavailable('billing request rejected')
            data = response.read(65537)
        if len(data) > 65536:
            raise LicenseUnavailable('oversized billing response')
        result = json.loads(data)
        if not isinstance(result, dict):
            raise LicenseUnavailable('invalid billing response')
        return result

    def _proof(self, operation: str, *, price_id: str | None = None) -> dict:
        if operation not in {'LICENSE', 'PORTAL', 'CHECKOUT'}:
            raise LicenseUnavailable('invalid billing operation')
        now = int(self.clock())
        nonce = b64(secrets.token_bytes(16))
        message = f'LW-{operation}-1\n{self.account_id}\n{self.device_id}\n'
        if operation == 'CHECKOUT':
            if not price_id or not re.fullmatch(r'pri_[a-z0-9]{26}', price_id):
                raise LicenseUnavailable('invalid merchant price')
            message += price_id + '\n'
        message += f'{now}\n{nonce}\n'
        result = {'account_id': self.account_id, 'device_id': self.device_id,
                  'timestamp': now, 'nonce': nonce,
                  'signature': b64(self.signing_key.sign(message.encode('ascii')))}
        if operation == 'CHECKOUT':
            result['price_id'] = price_id
        return result

    def enroll(self, invite_code: str) -> None:
        nonce = b64(secrets.token_bytes(16))
        now = int(self.clock())
        public = b64(self.signing_key.public_key().public_bytes(
            serialization.Encoding.Raw, serialization.PublicFormat.Raw))
        digest = sha256(invite_code.encode('ascii')).hexdigest()
        message = (f'LW-ENROLL-1\n{self.account_id}\n{self.device_id}\n{public}\n'
                   f'{now}\n{nonce}\n{digest}\n').encode('ascii')
        result = self._post('/v1/accounts/enroll', {
            'account_id': self.account_id, 'device_id': self.device_id,
            'public_key': public, 'invite_code': invite_code,
            'timestamp': now, 'nonce': nonce,
            'signature': b64(self.signing_key.sign(message))})
        if result.get('enrolled') is not True:
            raise LicenseUnavailable('enrollment failed')

    def checkout(self, price_id: str) -> dict:
        response = self._post('/v1/billing/checkout-ticket',
                              self._proof('CHECKOUT', price_id=price_id))
        custom = response.get('custom_data')
        if (response.get('price_id') != price_id or not isinstance(custom, dict)
            or not isinstance(custom.get('laptopwatch_checkout_ticket'), str)):
            raise LicenseUnavailable('invalid checkout authorization')
        # Checkout authorization is not payment evidence.
        return {'price_id': price_id, 'custom_data': custom}

    def lease(self, cache: PaidLicense) -> dict:
        response = self._post('/v1/licenses/lease', self._proof('LICENSE'))
        if not isinstance(response.get('license'), str):
            raise LicenseUnavailable('billing service issued no license')
        # Ignore response.public_key; trust only embedded issuer.
        return cache.store(response['license'], now=int(self.clock()))

    def portal(self) -> str:
        result = self._post('/v1/billing/portal', self._proof('PORTAL'))
        value = result.get('url')
        parsed = urlparse(value) if isinstance(value, str) else None
        if (not parsed or parsed.scheme != 'https'
            or parsed.hostname not in {'customer-portal.paddle.com',
                                       'sandbox-customer-portal.paddle.com'}
            or not parsed.query):
            raise LicenseUnavailable('invalid customer portal response')
        return value


class LicenseGate:
    """Recheck signed leases at each premium operation and refresh near expiry."""
    def __init__(self, cache: PaidLicense, client: BillingClient, *, clock=None):
        self.cache, self.client = cache, client
        self.clock = clock or (lambda: int(time.time()))
        self.last_attempt = 0

    def allowed(self, feature: str) -> bool:
        if feature == 'local':
            return True
        if feature not in {'remote_location', 'remote_incidents', 'wan_media'}:
            return False
        now = int(self.clock())
        claims = self.cache.current(now=now)
        if claims is not None and claims['expires_at'] > now + 120:
            return True
        if now - self.last_attempt < 180:
            return claims is not None
        self.last_attempt = now
        try:
            self.client.lease(self.cache)
        except Exception:
            return claims is not None
        return self.cache.allow(feature, now=now)

    def wrap_location(self, sender):
        def deliver(event_id, fix):
            return self.allowed('remote_location') and sender(event_id, fix)
        return deliver

    def wrap_incidents(self, sender):
        class Guarded:
            def send(self_inner, event):
                return self.allowed('remote_incidents') and sender.send(event)
        return Guarded()

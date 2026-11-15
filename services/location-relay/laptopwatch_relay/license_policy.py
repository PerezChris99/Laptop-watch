"""Issuer-pinned Ed25519 paid entitlements at the optional hosted relay boundary."""
from __future__ import annotations
import base64, json, re, time, uuid
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PublicKey
B64=re.compile(r'^[A-Za-z0-9_-]+$')
SUB=re.compile(r'^sub_[a-z0-9]{26}$')
def decode(value: str, max_bytes: int) -> bytes:
    if not isinstance(value,str) or not B64.fullmatch(value) or len(value)>max_bytes*2:
        raise ValueError('Invalid license encoding')
    raw=base64.urlsafe_b64decode(value+'='*(-len(value)%4))
    if len(raw)>max_bytes or base64.urlsafe_b64encode(raw).decode().rstrip('=')!=value:
        raise ValueError('Invalid license length')
    return raw

class RelayLicensePolicy:
    """Configured only with an independently approved issuer and single owner account."""
    def __init__(self, pinned_issuer_b64: str, account_id: str, *, clock=None):
        key=decode(pinned_issuer_b64,32)
        if len(key)!=32: raise ValueError('Issuer key must be 32 bytes')
        self.issuer=Ed25519PublicKey.from_public_bytes(key)
        self.account_id=str(uuid.UUID(account_id))
        self.clock=clock or (lambda:int(time.time()))
    def allows(self, token: str|None, device_id: str)->bool:
        try:
            if not isinstance(token,str) or len(token)>8192 or token.count('.')!=1:return False
            body,sig=token.split('.')
            raw=decode(body,4096)
            signature=decode(sig,64)
            if len(signature)!=64:return False
            self.issuer.verify(signature,body.encode('ascii'))
            c=json.loads(raw)
            now=int(self.clock())
            return bool(isinstance(c,dict) and c.get('v')==1 and c.get('issuer')=='LaptopWatch' and
                c.get('account_id')==self.account_id and
                c.get('device_id')==str(uuid.UUID(device_id)) and
                c.get('plan') in ('pro','business') and
                isinstance(c.get('subscription_id'),str) and SUB.fullmatch(c['subscription_id']) and
                type(c.get('issued_at')) is int and type(c.get('expires_at')) is int and
                type(c.get('max_devices')) is int and 1<=c['max_devices']<=100 and
                c['issued_at']<=now+60 and now<c['expires_at']<=c['issued_at']+3600)
        except Exception:
            return False

import secrets
import uuid
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from cryptography.hazmat.primitives.serialization import Encoding, PublicFormat
from fastapi.testclient import TestClient
from laptopwatch_billing.app import create_app, enc

CLOCK=1784210000
PRICE='pri_'+'a'*26

def context(tmp_path):
    app=create_app(db_path=str(tmp_path/'billing.db'),webhook_secret='w'*40,
      checkout_secret='c'*40,operator_secret='o'*40,
      signing_key=Ed25519PrivateKey.generate(),prices={PRICE:('pro',3)},clock=lambda:CLOCK)
    return app, str(uuid.uuid4()),str(uuid.uuid4()),Ed25519PrivateKey.generate()

def admin(api,path,data):
    return api.post(path,json=data,headers={'Authorization':'Bearer '+'o'*40})

def test_authenticated_account_closure_revokes_devices_and_protects_billing(tmp_path):
    app,account,device,private=context(tmp_path)
    pub=enc(private.public_key().public_bytes(Encoding.Raw,PublicFormat.Raw))
    with TestClient(app) as api:
        assert admin(api,'/v1/admin/accounts',{'account_id':account}).status_code==200
        assert admin(api,'/v1/admin/devices',{'account_id':account,'device_id':device,'public_key':pub}).status_code==200
        nonce=enc(secrets.token_bytes(16));timestamp=CLOCK
        message=f'LW-ACCOUNT-CLOSE-1\n{account}\n{device}\n{timestamp}\n{nonce}\n'.encode()
        data={'account_id':account,'device_id':device,'timestamp':timestamp,'nonce':nonce,
              'signature':enc(private.sign(message)),'confirm_account_id':account}
        assert api.post('/v1/accounts/close',json={**data,'confirm_account_id':device}).status_code==403
        assert api.post('/v1/accounts/close',json={**data,'signature':enc(b'0'*64)}).status_code==403
        response=api.post('/v1/accounts/close',json=data)
        assert response.status_code==200,response.text
        assert response.json()['result']=='closed'
        assert response.json()['provider_cancellation_required'] is False
        assert response.json()['cancellation_pending_count'] == 0
        assert api.post('/v1/accounts/close',json={**data,'nonce':enc(secrets.token_bytes(16))}).status_code==403
        assert admin(api,'/v1/admin/invitations',{'account_id':account}).status_code==404
        assert admin(api,'/v1/admin/devices',{'account_id':account,'device_id':str(uuid.uuid4()),'public_key':pub}).status_code==400
        assert admin(api,'/v1/admin/checkout-ticket',{'account_id':account,'price_id':PRICE}).status_code==404
        assert api.post('/v1/licenses/lease',json={'account_id':account,'device_id':device,
                    'timestamp':CLOCK,'nonce':enc(secrets.token_bytes(16)),'signature':enc(b'0'*64)}).status_code==403
        assert admin(api,'/v1/admin/accounts/close',{'account_id':account}).json()['result']=='already_closed'

def test_close_requires_operator_or_device_proof(tmp_path):
    app,account,device,private=context(tmp_path)
    with TestClient(app) as api:
        assert api.post('/v1/admin/accounts/close',json={'account_id':account}).status_code==401
        assert api.post('/v1/accounts/close',json={'account_id':account,'device_id':device,
           'timestamp':CLOCK,'nonce':enc(secrets.token_bytes(16)),
           'signature':enc(b'0'*64),'confirm_account_id':account}).status_code==403

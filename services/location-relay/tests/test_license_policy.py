import base64,json,uuid
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from cryptography.hazmat.primitives.serialization import Encoding,PublicFormat
from laptopwatch_relay.license_policy import RelayLicensePolicy
def b64(b):return base64.urlsafe_b64encode(b).decode().rstrip('=')
def test_signed_relay_lease_never_accepts_wrong_owner_or_device():
    k=Ed25519PrivateKey.generate()
    owner,dev=str(uuid.uuid4()),str(uuid.uuid4())
    v=RelayLicensePolicy(b64(k.public_key().public_bytes(Encoding.Raw,PublicFormat.Raw)),
       owner,clock=lambda:1784210000)
    c={'v':1,'issuer':'LaptopWatch','account_id':owner,'device_id':dev,
      'plan':'pro','subscription_id':'sub_'+'a'*26,
      'issued_at':1784210000,'expires_at':1784213500,'max_devices':3}
    def seal(value):
        body=b64(json.dumps(value).encode())
        return body+'.'+b64(k.sign(body.encode()))
    assert v.allows(seal(c),dev)
    assert not v.allows(seal(c),str(uuid.uuid4()))
    assert not v.allows(seal({**c,'account_id':str(uuid.uuid4())}),dev)
    assert not v.allows(seal({**c,'expires_at':1784217200}),dev)
    assert not v.allows(seal({**c,'max_devices':0}),dev)
    assert not v.allows(None,dev)

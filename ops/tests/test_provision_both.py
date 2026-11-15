import base64, hashlib
from ops.provision_license_issuer import provision

def test_both_clients_pin_only_the_public_key(tmp_path):
    raw=bytes(range(32))
    pub=base64.urlsafe_b64encode(raw).decode().rstrip('=')
    key=tmp_path/'key.pub';key.write_text(pub)
    desktop=tmp_path/'license_trust.py';mobile=tmp_path/'trust.js'
    assert provision(key,target=desktop,mobile_target=mobile)==hashlib.sha256(raw).hexdigest()
    assert pub in desktop.read_text()
    assert pub in mobile.read_text()

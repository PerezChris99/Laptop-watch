"""Regression tests for signed real-world release acceptance evidence."""
import base64
import json
import time
import hashlib
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
import pytest
from ops.release_attestation import REQUIRED, AcceptanceError, verify

HEAD = 'a'*40


def issue(tmp_path,now):
    private = Ed25519PrivateKey.generate()
    pub = base64.urlsafe_b64encode(private.public_key().public_bytes_raw()).decode().rstrip('=')
    evid = {}
    for gate in REQUIRED:
        p = tmp_path / (gate+'.txt')
        p.write_text('Actual signed-off inspection report: '+gate)
        evid[gate] = {'path':p.name,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()}
    content={'v':1,'commit_sha':HEAD,'issued_at':now,'expires_at':now+3600,'evidence':evid}
    signed = base64.urlsafe_b64encode(private.sign(json.dumps(content,sort_keys=True,separators=(',',':'),ensure_ascii=True).encode())).decode().rstrip('=')
    file=tmp_path/'manifest.json'
    file.write_text(json.dumps({**content,'signature':signed}))
    return file,pub,content


def test_authentic_evidence_verifies_exact_commit_and_all_gates(tmp_path):
    now=int(time.time())
    file,key,data=issue(tmp_path,now)
    result=verify(file,key,HEAD,now=now)
    assert result['commit_sha']==HEAD
    assert set(result['verified_gates']) == REQUIRED


def test_tampering_report_is_rejected_even_if_manifest_signed(tmp_path):
    now=int(time.time())
    file,key,content=issue(tmp_path,now)
    first=next(iter(content['evidence'].values()))
    (tmp_path/first['path']).write_bytes(b'tampered')
    with pytest.raises(AcceptanceError,match='digest'):verify(file,key,HEAD,now=now)


def test_wrong_commit_expiry_missing_gate_and_untrusted_signer_fail(tmp_path):
    now=int(time.time())
    file,key,content=issue(tmp_path,now)
    with pytest.raises(AcceptanceError):verify(file,key,'b'*40,now=now)
    with pytest.raises(AcceptanceError):verify(file,key,HEAD,now=now+3601)
    with pytest.raises(AcceptanceError):verify(file,'',HEAD,now=now)
    content['evidence'].pop('physical_devices')
    file.write_text(json.dumps({**content,'signature':json.loads(file.read_text())['signature']}))
    with pytest.raises(AcceptanceError):verify(file,key,HEAD,now=now)


def test_manifest_cannot_read_evidence_outside_review_directory(tmp_path):
    now=int(time.time())
    file,key,content=issue(tmp_path,now)
    first=next(iter(content['evidence'].values()))
    link=tmp_path/first['path']
    link.unlink()
    destination=tmp_path.parent/'external-evidence-secret'
    destination.write_bytes(b'private')
    link.symlink_to(destination)
    with pytest.raises(AcceptanceError):verify(file,key,HEAD,now=now)
    destination.unlink()

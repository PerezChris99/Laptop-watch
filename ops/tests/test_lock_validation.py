"""Lockfile integrity and manifest consistency offline unit tests."""
import base64
import json
import pytest
from ops.lock_validation import LockValidationError, npm_lock, cargo_lock, pod_lock


def fixture_npm(tmp_path):
    package={'name':'demo','version':'1.0.0','dependencies':{'foo':'1.2.3'}}
    lock={'lockfileVersion':3,'packages':{'':package,
          'node_modules/foo':{'version':'1.2.3','resolved':'https://registry.npmjs.org/foo/-/foo-1.2.3.tgz',
              'integrity':'sha512-'+base64.b64encode(bytes([11]*64)).decode()}}}
    manifest=tmp_path/'package.json'; locked=tmp_path/'package-lock.json'
    manifest.write_text(json.dumps(package));locked.write_text(json.dumps(lock))
    return manifest,locked,lock


def test_npm_resolutions_are_integrity_and_manifest_checked(tmp_path):
    p,l,data=fixture_npm(tmp_path)
    assert npm_lock(p,l)==1
    data['packages']['']['dependencies']['foo']='9.9.9'
    l.write_text(json.dumps(data))
    with pytest.raises(LockValidationError,match='differs'):npm_lock(p,l)


def test_npm_locks_reject_unverified_artifacts(tmp_path):
    p,l,data=fixture_npm(tmp_path)
    data['packages']['node_modules/foo']['resolved']='http://unsafe/file.tgz'
    l.write_text(json.dumps(data))
    with pytest.raises(LockValidationError,match='HTTPS'):npm_lock(p,l)
    data['packages']['node_modules/foo']['resolved']='https://registry.npmjs.org/foo.tgz'
    data['packages']['node_modules/foo']['integrity']='sha1-abc'
    l.write_text(json.dumps(data))
    with pytest.raises(LockValidationError,match='SHA-512'):npm_lock(p,l)


def test_cargo_requires_registry_checksums_and_versions(tmp_path):
    lock=tmp_path/'Cargo.lock'
    lock.write_text('version = 4\n[[package]]\nname = "app"\nversion = "0.1.0"\n'
                    '[[package]]\nname = "dep"\nversion = "2.0.0"\n'
                    'source = "registry+https://github.com/rust-lang/crates.io-index"\n'
                    'checksum = "'+'a'*64+'"\n')
    assert cargo_lock(lock)==2
    lock.write_text(lock.read_text().replace('a'*64,'abc'))
    with pytest.raises(LockValidationError,match='SHA-256'):cargo_lock(lock)


def test_pod_requires_actual_checksums(tmp_path):
    lock=tmp_path/'Podfile.lock'
    lock.write_text('PODS:\n  - Lib (1.2.0)\nDEPENDENCIES:\n  - Lib\n'
                    'SPEC CHECKSUMS:\n  Lib: '+'a'*40+'\nPODFILE CHECKSUM: '+'b'*40+'\nCOCOAPODS: 1.16.2\n')
    assert pod_lock(lock)==1
    lock.write_text(lock.read_text().replace('a'*40,'stub'))
    with pytest.raises(LockValidationError,match='spec checksums'):pod_lock(lock)

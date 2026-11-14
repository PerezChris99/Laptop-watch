"""Verified local-owner audit history must reject tampering before disclosure."""
from __future__ import annotations

import json
import uuid

from laptopwatch.audit import SignedAuditLog
from laptopwatch.cli import main
from laptopwatch.identity import initialize_identity, get_audit_key


def test_local_verified_audit_history(tmp_path, capsys):
    initialize_identity(tmp_path)
    log = SignedAuditLog(tmp_path / 'audit.jsonl', get_audit_key(tmp_path))
    device_id = str(uuid.uuid4())
    log.append('lock', device_id, result='success')
    assert main(['--state-dir', str(tmp_path), 'audit-history']) == 0
    printed = json.loads(capsys.readouterr().out)
    assert printed == [{
        'action':'lock', 'device':device_id, 'result':'success',
        'timestamp_ms':printed[0]['timestamp_ms'],
    }]
    assert len(printed) == 1


def test_tampered_audit_is_never_returned(tmp_path, capsys):
    initialize_identity(tmp_path)
    log = SignedAuditLog(tmp_path / 'audit.jsonl', get_audit_key(tmp_path))
    log.append('lock', str(uuid.uuid4()), result='success')
    path = tmp_path / 'audit.jsonl'
    path.write_bytes(path.read_bytes().replace(b'success', b'failure'))
    assert main(['--state-dir', str(tmp_path), 'audit-history']) == 3
    captured = capsys.readouterr()
    assert captured.out == ''
    assert 'integrity failure' in captured.err

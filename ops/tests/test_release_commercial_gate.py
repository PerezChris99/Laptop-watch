"""Commercial packaging must fail closed when trusted issuer pins are absent."""
from __future__ import annotations

from ops import verify_release


def test_commercial_source_gate_requires_matching_issuer_pins(monkeypatch):
    monkeypatch.setenv('LW_COMMERCIAL_BUILD', '1')
    calls = []
    def fake_record(name, command, **_kwargs):
        calls.append((name, command))
        return verify_release.Result(name, 'BLOCKED', 'No approved license issuer')
    monkeypatch.setattr(verify_release, 'record_command', fake_record)
    result = verify_release.checks('source')
    assert [r.gate for r in result] == [
        'source:contract', 'source:design', 'commercial:issuer-pins',
    ]
    assert result[-1].status == 'BLOCKED'
    assert calls[-1][1] == [
        verify_release.sys.executable, 'ops/provision_license_issuer.py', '--verify-only',
    ]


def test_free_development_source_gate_does_not_require_paid_issuer(monkeypatch):
    monkeypatch.delenv('LW_COMMERCIAL_BUILD', raising=False)
    monkeypatch.setattr(verify_release, 'record_command',
        lambda name, _command, **_kwargs: verify_release.Result(name, 'PASS', 'isolated stub'))
    assert [r.gate for r in verify_release.checks('source')] == [
        'source:contract', 'source:design',
    ]

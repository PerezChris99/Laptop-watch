"""Ensure manual acceptance cannot be silently skipped or forged green."""
import json
from pathlib import Path
from ops import verify_release


def test_full_release_stays_blocked_without_signed_acceptance(monkeypatch):
    monkeypatch.setattr(verify_release, "record_command",
        lambda name, cmd, **kwargs: verify_release.Result(name, "PASS", "stubbed"))
    monkeypatch.setattr(verify_release, "grouped",
        lambda name, cmd, **kwargs: verify_release.Result(name, "PASS", "stubbed"))
    results = verify_release.checks("full")
    assert results[-1].gate == "release:real-world-acceptance"
    assert results[-1].status == "BLOCKED"


def test_explicit_release_manifest_invokes_independent_verifier(monkeypatch):
    called=[]
    def fake_command(name, command, **kwargs):
        called.append((name, command))
        return verify_release.Result(name, "PASS", "stubbed")
    monkeypatch.setattr(verify_release, "record_command", fake_command)
    monkeypatch.setattr(verify_release, "grouped",
        lambda name, cmd, **kwargs: verify_release.Result(name, "PASS", "stubbed"))
    manifest=Path("/approval/evidence/manifest.json")
    results=verify_release.checks("full", manifest)
    assert results[-1].gate == "release:real-world-acceptance"
    assert called[-1][1][-2:] == ["--manifest", str(manifest)]


def test_source_only_can_never_claim_product_approval(monkeypatch, tmp_path):
    monkeypatch.setattr(verify_release, "checks",
        lambda mode, manifest=None: [verify_release.Result("source:syntactic", "PASS", "source")])
    result_file=tmp_path/"report.json"
    assert verify_release.main(["--mode","source","--json",str(result_file)])==0
    payload=json.loads(result_file.read_text())
    assert payload["release_approved"] is False


def test_full_release_requires_all_gates_and_explicit_signed_receipt(monkeypatch, tmp_path):
    report=tmp_path/"report.json"
    monkeypatch.setattr(verify_release, "checks", lambda mode, manifest=None: [
        verify_release.Result("source:syntactic", "PASS", "source"),
        verify_release.Result("release:real-world-acceptance", "BLOCKED", "not approved")])
    assert verify_release.main(["--mode","full","--json",str(report)])==1
    assert json.loads(report.read_text())["release_approved"] is False

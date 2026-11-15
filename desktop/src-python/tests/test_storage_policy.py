from pathlib import Path
import os
import time
import uuid
import pytest
from laptopwatch.storage_policy import EvidenceStoragePolicy, StorageLimitExceeded


def make_photo(root, content=b'abc'):
    file = root / f'photo-{uuid.uuid4().hex}.jpg'
    file.write_bytes(content)
    return file


def test_quota_counts_only_regular_evidence(tmp_path):
    make_photo(tmp_path, b'x' * 40)
    (tmp_path / 'secret.txt').write_bytes(b'x' * 900)
    quota = EvidenceStoragePolicy(tmp_path, limit_bytes=32 * 1024**2)
    assert quota.usage().bytes == 40
    assert quota.usage().files == 1
    quota.require_room(100)
    with pytest.raises(StorageLimitExceeded):
        quota.require_room(32 * 1024**2)


def test_explicit_purge_preserves_symlink_unknown_and_active(tmp_path):
    old = make_photo(tmp_path)
    active = tmp_path / f'video-{uuid.uuid4().hex}.mp4'
    active.write_bytes(b'active')
    secret = tmp_path / 'critical.txt'
    secret.write_text('never touch')
    link = tmp_path / f'photo-{uuid.uuid4().hex}.jpg'
    try:
        link.symlink_to(secret)
    except OSError:
        pass
    past = time.time() - 60 * 86400
    os.utime(old, (past, past)); os.utime(active, (past, past))
    q = EvidenceStoragePolicy(tmp_path)
    assert q.purge_expired(days=30, active_file=active.name) == 1
    assert not old.exists()
    assert active.exists() and secret.read_text() == 'never touch'
    if link.is_symlink():
        assert link.exists()


def test_purge_does_not_remove_recent_or_unknown_and_rejects_invalid(tmp_path):
    recent = make_photo(tmp_path)
    q = EvidenceStoragePolicy(tmp_path)
    assert q.purge_expired(days=1) == 0
    assert recent.exists()
    for val in [0, 3651, True, '5']:
        with pytest.raises(ValueError): q.purge_expired(days=val)
    for val in [0, -1, True, 9]:
        with pytest.raises(ValueError): EvidenceStoragePolicy(tmp_path, limit_bytes=val)

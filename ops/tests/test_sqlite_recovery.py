"""Encrypted SQLite backup/restore is atomic, authenticated and does not overwrite."""
import os
import sqlite3
import pytest
from ops.sqlite_recovery import backup, restore, key_bytes, RecoveryError, MAGIC


def sample(tmp_path):
    db=tmp_path/'live.sqlite'
    with sqlite3.connect(db) as conn:
        conn.execute('CREATE TABLE items(id INTEGER PRIMARY KEY, content TEXT NOT NULL)')
        conn.execute('INSERT INTO items(content) VALUES (?)',('safe historical evidence',))
    return db


def test_backup_is_encrypted_and_restore_is_consistent(tmp_path):
    db=sample(tmp_path)
    key=bytes(range(32)); encrypted=tmp_path/'backup.enc';out=tmp_path/'restored.sqlite'
    backup(db,encrypted,key)
    assert encrypted.read_bytes().startswith(MAGIC)
    assert b'safe historical evidence' not in encrypted.read_bytes()
    restore(encrypted,out,key)
    with sqlite3.connect(out) as conn:
        assert conn.execute('SELECT content FROM items').fetchone()[0]=='safe historical evidence'
        assert conn.execute('PRAGMA integrity_check').fetchone()[0]=='ok'


def test_tampered_ciphertext_and_wrong_key_never_restore(tmp_path):
    db=sample(tmp_path);encrypted=tmp_path/'encrypted.enc';out=tmp_path/'restored.sqlite'
    backup(db,encrypted,bytes(range(32)))
    with pytest.raises(RecoveryError,match='authentication'):
        restore(encrypted,out,b'\x02'*32)
    altered=bytearray(encrypted.read_bytes());altered[-1] ^= 1; encrypted.write_bytes(altered)
    with pytest.raises(RecoveryError,match='authentication'):
        restore(encrypted,out,bytes(range(32)))
    assert not out.exists()


def test_restore_refuses_existing_files_and_backup_refuses_overwrite(tmp_path):
    db=sample(tmp_path);sealed=tmp_path/'backup.enc';key=bytes(range(32))
    backup(db,sealed,key)
    with pytest.raises(RecoveryError,match='exists'):backup(db,sealed,key)
    with pytest.raises(RecoveryError,match='overwriting'):restore(sealed,db,key)


def test_key_file_requires_strict_permissions(tmp_path):
    path=tmp_path/'backup.key';path.write_bytes(bytes(range(32)))
    os.chmod(path,0o600)
    assert key_bytes(path)==bytes(range(32))
    if os.name=='posix':
        os.chmod(path,0o644)
        with pytest.raises(RecoveryError,match='permissions'):key_bytes(path)

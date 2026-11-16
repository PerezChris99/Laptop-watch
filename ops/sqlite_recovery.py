"""Operator-only encrypted, consistent SQLite snapshot and recovery.

Never include the data key with the backup. Does not back up evidence media,
third-party merchant state or secrets. Source must be an ordinary SQLite DB.
Restore is create-only and must be exercised before production acceptance.
"""
from __future__ import annotations

import argparse
import os
from pathlib import Path
import secrets
import sqlite3
import stat
import sys
from urllib.parse import quote

from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.exceptions import InvalidTag

MAGIC = b'LWSQLITE1'
AAD = b'LaptopWatch-Consistent-SQLite-Backup-v1'
MAX = 128 * 1024 * 1024
ROOT = Path(__file__).resolve().parents[1]


class RecoveryError(ValueError):
    """Invalid, unsafe, unauthenticated or incomplete backup operation."""


def key_bytes(path: Path) -> bytes:
    if path.is_symlink() or not path.is_file() or path.resolve().is_relative_to(ROOT):
        raise RecoveryError('Backup key unavailable, linked or under source control')
    if os.name == 'posix' and stat.S_IMODE(path.stat().st_mode) & 0o077:
        raise RecoveryError('Backup key file has unsafe permissions')
    raw = path.read_bytes()
    if len(raw) != 32:
        raise RecoveryError('Backup key must be exactly 32 raw random bytes')
    return raw


def backup(database: Path, output: Path, key: bytes) -> None:
    if database.is_symlink() or not database.is_file():
        raise RecoveryError('Source database missing or unsafe')
    if output.exists() or output.is_symlink() or not output.parent.is_dir():
        raise RecoveryError('Backup path already exists or parent missing')
    if not 0 < database.stat().st_size <= MAX:
        raise RecoveryError('Database exceeds bounded backup size')
    address = quote(database.resolve().as_posix(), safe='/')
    try:
        source = sqlite3.connect(f'file:{address}?mode=ro', uri=True, timeout=10)
    except sqlite3.Error as exc:
        raise RecoveryError('Unable to open source SQLite database') from exc
    snapshot = sqlite3.connect(':memory:')
    try:
        source.backup(snapshot, pages=128, sleep=0.02)
        if snapshot.execute('PRAGMA integrity_check').fetchone()[0] != 'ok':
            raise RecoveryError('Source database failed integrity check')
        plaintext = snapshot.serialize()
        if not 0 < len(plaintext) <= MAX:
            raise RecoveryError('Snapshot exceeds backup size')
        nonce = secrets.token_bytes(12)
        sealed = MAGIC + nonce + AESGCM(key).encrypt(nonce, plaintext, AAD)
        fd = os.open(output, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
        try:
            with os.fdopen(fd, 'wb') as stream:
                stream.write(sealed)
                stream.flush()
                os.fsync(stream.fileno())
        except BaseException:
            output.unlink(missing_ok=True)
            raise
    except sqlite3.Error as exc:
        raise RecoveryError('Consistent SQLite snapshot failed') from exc
    finally:
        snapshot.close()
        source.close()


def restore(input_file: Path, destination: Path, key: bytes) -> None:
    if input_file.is_symlink() or not input_file.is_file():
        raise RecoveryError('Backup missing or unsafe')
    if destination.exists() or destination.is_symlink() or not destination.parent.is_dir():
        raise RecoveryError('Restore refuses overwriting existing database')
    if not 0 < input_file.stat().st_size <= MAX + 100:
        raise RecoveryError('Backup size invalid')
    contents = input_file.read_bytes()
    if not contents.startswith(MAGIC) or len(contents) <= len(MAGIC)+12+16:
        raise RecoveryError('Invalid backup header')
    nonce = contents[len(MAGIC):len(MAGIC)+12]
    try:
        plaintext = AESGCM(key).decrypt(nonce, contents[len(MAGIC)+12:], AAD)
    except (ValueError, InvalidTag) as exc:
        raise RecoveryError('Backup authentication failed') from exc
    if not 0 < len(plaintext) <= MAX:
        raise RecoveryError('Invalid restored SQLite size')
    connection = sqlite3.connect(':memory:')
    try:
        connection.deserialize(plaintext)
        if connection.execute('PRAGMA integrity_check').fetchone()[0] != 'ok':
            raise RecoveryError('Restored SQLite integrity failed')
    except sqlite3.Error as exc:
        raise RecoveryError('Restored data is not a valid SQLite database') from exc
    finally:
        connection.close()
    fd = os.open(destination, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    try:
        with os.fdopen(fd, 'wb') as stream:
            stream.write(plaintext)
            stream.flush()
            os.fsync(stream.fileno())
    except BaseException:
        destination.unlink(missing_ok=True)
        raise


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['backup', 'restore'])
    parser.add_argument('--database', type=Path, required=True)
    parser.add_argument('--sealed-file', type=Path, required=True)
    parser.add_argument('--key-file', type=Path, required=True)
    args = parser.parse_args(argv)
    try:
        key = key_bytes(args.key_file)
        if args.action == 'backup':
            backup(args.database, args.sealed_file, key)
        else:
            restore(args.sealed_file, args.database, key)
    except (RecoveryError, OSError):
        print('Recovery operation refused: key, source or authentication invalid',
              file=sys.stderr)
        return 1
    print('Encrypted SQLite '+args.action+' completed; off-host restore verification still required')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())

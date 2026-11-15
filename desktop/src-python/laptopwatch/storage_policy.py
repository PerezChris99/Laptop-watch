"""Bounded local evidence storage with explicit, owner-initiated retention purge.

Never follows symlinks, never removes unknown files, and never silently
purges forensic media to make room for new recordings. No cross-process
reservation is offered: the desktop engine must be the exclusive writer.
"""
from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
import os
import re
import time

EVIDENCE_NAME = re.compile(r"^(?:photo-[0-9a-f]{32}\.jpg|video-[0-9a-f]{32}\.mp4)$")


class StorageLimitExceeded(RuntimeError):
    """The evidence volume has reached its configured safety limit."""


@dataclass(frozen=True)
class EvidenceUsage:
    files: int
    bytes: int
    limit_bytes: int


class EvidenceStoragePolicy:
    def __init__(self, directory: str | Path, *, limit_bytes: int = 2 * 1024**3):
        if type(limit_bytes) is not int or not 32 * 1024**2 <= limit_bytes <= 100 * 1024**3:
            raise ValueError("Evidence quota must be 32 MiB to 100 GiB")
        self.directory = Path(directory)
        self.limit_bytes = limit_bytes

    def _files(self):
        if not self.directory.is_dir() or self.directory.is_symlink():
            raise StorageLimitExceeded("Evidence storage directory missing or unsafe")
        with os.scandir(self.directory) as scan:
            for entry in scan:
                if not EVIDENCE_NAME.fullmatch(entry.name):
                    continue
                if not entry.is_file(follow_symlinks=False):
                    continue
                stat = entry.stat(follow_symlinks=False)
                if stat.st_nlink != 1 or stat.st_size < 0:
                    continue
                yield self.directory / entry.name, stat

    def usage(self) -> EvidenceUsage:
        files, size = 0, 0
        for _, stat in self._files():
            files += 1
            size += stat.st_size
        return EvidenceUsage(files, size, self.limit_bytes)

    def require_room(self, additional_bytes: int) -> None:
        if type(additional_bytes) is not int or additional_bytes < 0:
            raise ValueError("Invalid evidence reservation")
        if self.usage().bytes + additional_bytes > self.limit_bytes:
            raise StorageLimitExceeded("Evidence quota reached. Export or delete files explicitly")

    def purge_expired(self, *, days: int, now: float | None = None, active_file: str | None = None) -> int:
        """Purge only on an explicit owner action; never remove an active MP4.

        This is ordinary filesystem deletion, NOT cryptographic secure erasure.
        Callers must display the distinction in the UI and audit the action.
        """
        if type(days) is not int or not 1 <= days <= 3650:
            raise ValueError("Retention period must be 1..3650 days")
        cutoff = (time.time() if now is None else now) - days * 86400
        removed = 0
        for path, stat in self._files():
            if path.name == active_file or stat.st_mtime >= cutoff:
                continue
            try:
                fresh = path.lstat()
                if (not path.is_symlink() and fresh.st_dev == stat.st_dev
                    and fresh.st_ino == stat.st_ino and fresh.st_nlink == 1
                    and fresh.st_mtime < cutoff):
                    path.unlink()
                    removed += 1
            except FileNotFoundError:
                continue
        return removed

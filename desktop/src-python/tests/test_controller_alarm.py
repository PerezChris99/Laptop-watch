"""Prevent false positive audible-deterrent reporting and verify WAV lifecycle."""
from __future__ import annotations

import os
import sys
import types
import wave
from pathlib import Path
from types import SimpleNamespace

from laptopwatch.controller import NativeController


def test_linux_alarm_uses_available_pipewire_player_and_valid_wav(monkeypatch):
    monkeypatch.setattr('laptopwatch.controller.platform.system', lambda: 'Linux')
    monkeypatch.setattr('laptopwatch.controller.shutil.which',
                        lambda name: '/usr/bin/pw-play' if name == 'pw-play' else None)
    played = []
    def accepted(command, *, timeout, check, stdout, stderr):
        assert command[0] == '/usr/bin/pw-play'
        with wave.open(command[1], 'rb') as sound:
            assert sound.getnchannels() == 1
            assert sound.getframerate() == 16000
            assert sound.getnframes() == 16000
        played.append(command[1])
        return SimpleNamespace(returncode=0)
    monkeypatch.setattr('laptopwatch.controller.subprocess.run', accepted)
    assert NativeController().alarm() is True
    assert len(played) == 1 and not Path(played[0]).exists()


def test_audio_backend_failure_is_never_reported_as_success(monkeypatch):
    monkeypatch.setattr('laptopwatch.controller.platform.system', lambda: 'Linux')
    monkeypatch.setattr('laptopwatch.controller.shutil.which',
                        lambda name: '/usr/bin/aplay' if name == 'aplay' else None)
    played = []
    def failed(command, **_kwargs):
        played.append(command[1])
        return SimpleNamespace(returncode=1)
    monkeypatch.setattr('laptopwatch.controller.subprocess.run', failed)
    assert NativeController().alarm() is False
    assert played and not Path(played[0]).exists()


def test_missing_audio_player_returns_false(monkeypatch):
    monkeypatch.setattr('laptopwatch.controller.platform.system', lambda: 'Linux')
    monkeypatch.setattr('laptopwatch.controller.shutil.which', lambda _: None)
    assert NativeController().alarm() is False


def test_audio_player_timeout_returns_false_and_removes_wav(monkeypatch):
    from subprocess import TimeoutExpired
    monkeypatch.setattr('laptopwatch.controller.platform.system', lambda: 'Linux')
    monkeypatch.setattr('laptopwatch.controller.shutil.which', lambda _: '/usr/bin/pw-play')
    files = []
    def timeout(command, **_kwargs):
        files.append(command[1])
        raise TimeoutExpired(command, 5)
    monkeypatch.setattr('laptopwatch.controller.subprocess.run', timeout)
    assert NativeController().alarm() is False
    assert files and not Path(files[0]).exists()


def test_windows_beep_failure_is_not_success(monkeypatch):
    monkeypatch.setattr('laptopwatch.controller.platform.system', lambda: 'Windows')
    def fail_beep(_frequency, _duration):
        raise RuntimeError('No audio device')
    monkeypatch.setitem(sys.modules, 'winsound', types.SimpleNamespace(Beep=fail_beep))
    assert NativeController().alarm() is False


def test_windows_beep_sequence_is_complete(monkeypatch):
    monkeypatch.setattr('laptopwatch.controller.platform.system', lambda: 'Windows')
    notes = []
    monkeypatch.setitem(sys.modules, 'winsound', types.SimpleNamespace(
        Beep=lambda frequency, duration: notes.append((frequency, duration))))
    assert NativeController().alarm() is True
    assert notes == [(1000, 250), (1500, 250), (1000, 250)]

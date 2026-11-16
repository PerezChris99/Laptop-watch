"""Generate/review real dependency locks; never synthesize registry resolutions.

Run in a trusted, network-capable checkout with the proper native toolchains.
Without --generate, exits nonzero until all required tracked locks exist.
"""
from __future__ import annotations

import argparse
from pathlib import Path
import shutil
import subprocess
import sys

try:
    from .lock_validation import LockValidationError, npm_lock, cargo_lock, pod_lock, python_lock
except ImportError:  # also supports direct `python ops/lock_dependencies.py`
    from lock_validation import LockValidationError, npm_lock, cargo_lock, pod_lock, python_lock

ROOT = Path(__file__).resolve().parents[1]
LOCKS = {
    'mobile/package-lock.json': ('npm', ['npm', 'install', '--package-lock-only', '--ignore-scripts']),
    'desktop/package-lock.json': ('npm', ['npm', 'install', '--package-lock-only', '--ignore-scripts']),
    'desktop/src-tauri/Cargo.lock': ('cargo', ['cargo', 'generate-lockfile']),
    'mobile/ios/Podfile.lock': ('pod', ['pod', 'install']),
    'desktop/src-python/requirements.lock': ('uv', ['uv', 'pip', 'compile', 'pyproject.toml', '--all-extras', '--universal', '--generate-hashes', '-o', 'requirements.lock']),
    'services/billing/requirements.lock': ('uv', ['uv', 'pip', 'compile', 'pyproject.toml', '--all-extras', '--universal', '--generate-hashes', '-o', 'requirements.lock']),
    'services/location-relay/requirements.lock': ('uv', ['uv', 'pip', 'compile', 'pyproject.toml', '--all-extras', '--universal', '--generate-hashes', '-o', 'requirements.lock']),
}



def validate(path: str) -> int:
    target = ROOT / path
    if path.endswith('package-lock.json'):
        return npm_lock(target.parent / 'package.json', target)
    if path.endswith('Cargo.lock'):
        return cargo_lock(target)
    if path.endswith('Podfile.lock'):
        return pod_lock(target)
    if path.endswith('requirements.lock'):
        return python_lock(target.parent / 'pyproject.toml', target)
    raise LockValidationError('Unknown lock type')


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--generate', action='store_true', help='resolve using authentic registries and toolchains')
    parser.add_argument('--platform', choices=['all', 'mobile', 'desktop', 'ios'], default='all')
    args = parser.parse_args()
    selected = {path: spec for path, spec in LOCKS.items() if
                args.platform == 'all' or (args.platform == 'mobile' and path.startswith('mobile/'))
                or (args.platform == 'desktop' and path.startswith('desktop/'))
                or (args.platform == 'ios' and path.startswith('mobile/ios/'))
                or (args.platform == 'all' and path.startswith('services/'))}
    missing = []
    for path, (executable, command) in selected.items():
        target = ROOT / path
        if not target.exists() and args.generate:
            if shutil.which(executable) is None:
                print(f'BLOCKED {path}: {executable} is unavailable')
                missing.append(path)
                continue
            print(f'GENERATE {path}: {" ".join(command)}', flush=True)
            result = subprocess.run(command, cwd=target.parent, check=False)
            if result.returncode:
                print(f'FAIL {path}: exit {result.returncode}')
                missing.append(path)
                continue
        if target.is_file() and target.stat().st_size > 0:
            try:
                count = validate(path)
                print(f'VALIDATED {path}: {count} resolved dependencies; '
                      'real package installation and supply-chain audit still required')
            except (LockValidationError, OSError) as exc:
                print(f'FAIL {path}: {exc}')
                missing.append(path)
        else:
            print(f'BLOCKED {path}: genuine reviewed lock missing')
            missing.append(path)
    if not args.generate:
        print('Generate with --generate on network-connected tooling; do not commit guessed lockfiles.')
    return 1 if missing else 0


if __name__ == '__main__':
    sys.exit(main())

"""Validate actual dependency resolutions instead of checking file presence only.

This is a static integrity preflight, not a substitute for npm ci, cargo --locked,
Pod installation or an independent package vulnerability/license audit.
"""
from __future__ import annotations

import base64
import json
from pathlib import Path
import re
import tomllib


class LockValidationError(ValueError):
    """A lock is missing, corrupt, stale or contains unverifiable resolutions."""


def npm_lock(manifest: Path, lock: Path) -> int:
    try:
        project = json.loads(manifest.read_text(encoding='utf-8'))
        data = json.loads(lock.read_text(encoding='utf-8'))
    except (ValueError, OSError) as exc:
        raise LockValidationError('npm manifest or lock is not readable JSON') from exc
    if not isinstance(data, dict) or data.get('lockfileVersion') not in (2, 3):
        raise LockValidationError('npm lockfileVersion must be supported (2 or 3)')
    packages = data.get('packages')
    if not isinstance(packages, dict) or not isinstance(packages.get(''), dict):
        raise LockValidationError('npm lock missing resolved packages and root')
    root = packages['']
    for field in ('name', 'version'):
        if root.get(field) != project.get(field):
            raise LockValidationError(f'npm root {field} differs from package.json')
    for group in ('dependencies', 'devDependencies', 'optionalDependencies'):
        expected = project.get(group, {})
        if not isinstance(expected, dict) or root.get(group, {}) != expected:
            raise LockValidationError(f'npm {group} differs from package.json')
        for dependency in expected:
            if f'node_modules/{dependency}' not in packages:
                raise LockValidationError(f'npm direct dependency not resolved: {dependency}')
    count = 0
    for path, item in packages.items():
        if not isinstance(path, str) or not isinstance(item, dict):
            raise LockValidationError('npm package lock has malformed entries')
        if path == '':
            continue
        if not path.startswith('node_modules/') or '..' in path.split('/'):
            raise LockValidationError('npm lock contains an unsafe package path')
        if item.get('link') is True:
            raise LockValidationError('npm release lock must not contain local links')
        if not isinstance(item.get('version'), str) or not item['version']:
            raise LockValidationError('npm dependency lacks a version')
        resolved = item.get('resolved')
        integrity = item.get('integrity')
        if not isinstance(resolved, str) or not resolved.startswith('https://'):
            raise LockValidationError(f'npm package lacks HTTPS resolution: {path}')
        if not isinstance(integrity, str) or not integrity.startswith('sha512-'):
            raise LockValidationError(f'npm package lacks SHA-512 integrity: {path}')
        try:
            proof = base64.b64decode(integrity[7:], validate=True)
        except (ValueError, base64.binascii.Error) as exc:
            raise LockValidationError('npm malformed package integrity') from exc
        if len(proof) != 64:
            raise LockValidationError('npm SHA-512 integrity digest has wrong length')
        count += 1
    if count < len(project.get('dependencies', {})):
        raise LockValidationError('npm lock is incomplete')
    return count


def cargo_lock(lock: Path) -> int:
    try:
        data = tomllib.loads(lock.read_text(encoding='utf-8'))
    except (OSError, ValueError) as exc:
        raise LockValidationError('Cargo.lock invalid TOML') from exc
    if data.get('version') not in (3, 4) or not isinstance(data.get('package'), list):
        raise LockValidationError('Cargo.lock invalid format')
    packages = data['package']
    if len(packages) < 2:
        raise LockValidationError('Cargo.lock lacks a dependency graph')
    names = set()
    for package in packages:
        if not isinstance(package, dict) or not isinstance(package.get('name'), str):
            raise LockValidationError('Cargo.lock invalid package')
        if not re.fullmatch(r'[A-Za-z0-9_.+-]+', package['name']):
            raise LockValidationError('Cargo.lock invalid package identifier')
        if not isinstance(package.get('version'), str) or not package['version']:
            raise LockValidationError('Cargo.lock unpinned package')
        identity = (package['name'], package['version'], package.get('source'))
        if identity in names:
            raise LockValidationError('Cargo.lock duplicate package')
        names.add(identity)
        origin = package.get('source')
        if origin is None:
            continue
        if isinstance(origin, str) and origin.startswith('registry+'):
            digest = package.get('checksum')
            if not isinstance(digest, str) or not re.fullmatch('[0-9a-f]{64}', digest):
                raise LockValidationError('Cargo registry artifact lacks SHA-256')
        elif isinstance(origin, str) and origin.startswith('git+'):
            if not re.search(r'#[0-9a-f]{40}$', origin):
                raise LockValidationError('Cargo Git dependency is not commit-pinned')
        else:
            raise LockValidationError('Cargo dependency origin is not pinned')
    return len(packages)


def pod_lock(lock: Path) -> int:
    try:
        content = lock.read_text(encoding='utf-8')
    except OSError as exc:
        raise LockValidationError('Podfile.lock missing') from exc
    lines = content.splitlines()
    for field in ('PODS:', 'DEPENDENCIES:', 'SPEC CHECKSUMS:', 'PODFILE CHECKSUM:', 'COCOAPODS:'):
        if not any(line.startswith(field) for line in lines):
            raise LockValidationError(f'Podfile.lock missing {field}')
    section = content.split('SPEC CHECKSUMS:', 1)[1].split('\n' + 'PODFILE CHECKSUM:', 1)[0]
    checksums = re.findall(r'^  [A-Za-z0-9_+./-]+: ([0-9a-f]{40})$', section, re.MULTILINE)
    if not checksums:
        raise LockValidationError('Podfile.lock lacks spec checksums')
    podfile = re.search(r'^PODFILE CHECKSUM: ([0-9a-f]{40})$', content, re.MULTILINE)
    if not podfile:
        raise LockValidationError('Podfile.lock has no verified Podfile checksum')
    cocoa = re.search(r'^COCOAPODS: (\d+\.\d+(?:\.\d+)?)$', content, re.MULTILINE)
    if not cocoa:
        raise LockValidationError('Podfile.lock missing installed CocoaPods version')
    return len(checksums)


def python_lock(manifest: Path, lock: Path) -> int:
    """Require hashes, exact versions and coverage of all direct Python deps."""
    try:
        project = tomllib.loads(manifest.read_text(encoding='utf-8'))
        text = lock.read_text(encoding='utf-8')
    except (OSError, ValueError) as exc:
        raise LockValidationError('Python manifest or lock unreadable') from exc
    normalized = []
    buffer = ''
    for original in text.splitlines():
        line = original.strip()
        if not line or line.startswith('#'):
            continue
        if line.endswith('\\'):
            buffer += line[:-1] + ' '
        else:
            normalized.append((buffer + line).strip())
            buffer = ''
    if buffer or not normalized:
        raise LockValidationError('Python lock is empty or truncated')
    names = set()
    for line in normalized:
        match = re.match(r'^([A-Za-z0-9_.-]+)==([0-9][A-Za-z0-9_.!+-]*)\b', line)
        if not match:
            raise LockValidationError('Python lock has an unpinned or external dependency')
        hashes = re.findall(r'--hash=sha256:([0-9a-f]{64})(?=\s|$)', line)
        if not hashes or '--hash=' in re.sub(r'--hash=sha256:[0-9a-f]{64}', '', line):
            raise LockValidationError('Python lock dependency lacks valid SHA-256 hash')
        names.add(re.sub('[-_.]+', '-', match.group(1)).lower())
    declarations = list(project['project'].get('dependencies', []))
    for extra in project['project'].get('optional-dependencies', {}).values():
        declarations.extend(extra)
    required = {re.sub('[-_.]+', '-', re.match(r'^[A-Za-z0-9_.-]+', req).group()).lower()
                for req in declarations}
    if not required.issubset(names):
        raise LockValidationError('Python lock does not cover pyproject direct dependencies')
    return len(normalized)

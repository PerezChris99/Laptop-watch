"""LaptopWatch local verification orchestrator.

It never treats missing dependencies, unavailable native hosts, external
credentials, physical devices or release approvals as passing checks.

Modes:
    python ops/verify_release.py --mode source
    python ops/verify_release.py --mode full --json results.json

No network, mock results, CI secrets or GitHub workflows are implied.
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import sys
from dataclasses import dataclass, asdict
from time import monotonic

ROOT = Path(__file__).resolve().parents[1]


@dataclass
class Result:
    gate: str
    status: str
    detail: str
    seconds: float = 0.0


def record_command(name: str, cmd: list[str], *, cwd: Path = ROOT,
                   paths: tuple[Path, ...] = (),
                   tools: tuple[str, ...] = (),
                   timeout: int = 600) -> Result:
    missing = [str(path.relative_to(ROOT)) for path in paths if not path.is_file()]
    missing += [binary for binary in tools if shutil.which(binary) is None]
    if missing:
        return Result(name, "BLOCKED", "Required input or tool missing: " + ", ".join(missing))
    start = monotonic()
    try:
        # Do not echo command output: dependency test logs can contain local
        # owner paths or environment details. Run explicitly outside the
        # verifier when human debugging and log-redaction policy are available.
        proc = subprocess.run(cmd, cwd=cwd, check=False, timeout=timeout,
                              stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                              text=True, errors="replace")
    except (FileNotFoundError, PermissionError, subprocess.TimeoutExpired) as exc:
        return Result(name, "BLOCKED", type(exc).__name__, monotonic() - start)
    return Result(name, "PASS" if proc.returncode == 0 else "FAIL",
                  "exit code " + str(proc.returncode), monotonic() - start)


def grouped(name: str, commands: list[list[str]], *,
            cwd: Path, paths: tuple[Path, ...] = (),
            tools: tuple[str, ...] = (), timeout: int = 600) -> Result:
    missing = [str(path.relative_to(ROOT)) for path in paths if not path.is_file()]
    missing += [binary for binary in tools if shutil.which(binary) is None]
    if missing:
        return Result(name, "BLOCKED", "Required input or tool missing: " + ", ".join(missing))
    start = monotonic()
    try:
        for step, command in enumerate(commands, 1):
            done = subprocess.run(command, cwd=cwd, check=False, timeout=timeout,
                                  stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                  text=True, errors="replace")
            if done.returncode:
                return Result(name, "FAIL", f"step {step} exited {done.returncode}",
                              monotonic() - start)
    except (FileNotFoundError, PermissionError, subprocess.TimeoutExpired) as exc:
        return Result(name, "BLOCKED", type(exc).__name__, monotonic() - start)
    return Result(name, "PASS", f"{len(commands)} commands passed",
                  monotonic() - start)


def checks(mode: str, acceptance_manifest: Path | None = None) -> list[Result]:
    results = [
        record_command("source:contract", [sys.executable, "ops/source_preflight.py"],
                       paths=(ROOT / "ops/source_preflight.py",), tools=("node",)),
        record_command("source:design", [sys.executable, "design/generate.py", "--check"],
                       paths=(ROOT / "design/generate.py",)),
    ]
    if os.getenv("LW_COMMERCIAL_BUILD") == "1":
        results.append(record_command("commercial:issuer-pins",
            [sys.executable, "ops/provision_license_issuer.py", "--verify-only"],
            paths=(ROOT / "ops/provision_license_issuer.py",
                   ROOT / "desktop/src-python/laptopwatch/license_trust.py",
                   ROOT / "mobile/src/licensing/trust.js")))
    if mode == "source":
        return results

    desktop = ROOT / "desktop"
    mobile = ROOT / "mobile"
    relay = ROOT / "services/location-relay"
    billing = ROOT / "services/billing"
    rust = desktop / "src-tauri"
    android = mobile / "android"

    results += [
        record_command("python:desktop-tests",
                       [sys.executable, "-m", "pytest", "-q", "desktop/src-python/tests"],
                       paths=(ROOT / "desktop/src-python/pyproject.toml",)),
        record_command("python:relay-tests",
                       [sys.executable, "-m", "pytest", "-q", "services/location-relay/tests"],
                       paths=(relay / "pyproject.toml",)),
        record_command("python:billing-tests",
                       [sys.executable, "-m", "pytest", "-q", "services/billing/tests"],
                       paths=(billing / "pyproject.toml",)),
        record_command("dependencies:locked",
                       [sys.executable, "ops/lock_dependencies.py"],
                       paths=(ROOT / "ops/lock_dependencies.py",)),
        grouped("desktop:node", [
            ["npm", "ci"], ["npm", "run", "test:ui"], ["npm", "run", "lint"],
            ["npm", "run", "check:js"], ["npm", "run", "build"],
        ], cwd=desktop, tools=("node", "npm"),
            paths=(desktop / "package-lock.json",)),
        grouped("mobile:node", [
            ["npm", "ci"], ["npm", "run", "test:protocol"],
            ["npm", "run", "test:ui"], ["npm", "run", "lint"],
            ["npm", "run", "check:js"],
        ], cwd=mobile, tools=("node", "npm"),
            paths=(mobile / "package-lock.json",)),
        grouped("desktop:rust", [
            ["cargo", "test", "--locked"],
            ["cargo", "build", "--locked"],
        ], cwd=rust, tools=("cargo",),
            paths=(rust / "Cargo.lock",)),
    ]
    wrapper = "gradlew.bat" if os.name == "nt" else "./gradlew"
    android_sdk = os.getenv("ANDROID_HOME") or os.getenv("ANDROID_SDK_ROOT")
    if not android_sdk or not Path(android_sdk).is_dir():
        results.append(Result("android:native", "BLOCKED", "Android SDK not configured"))
    else:
        results.append(grouped("android:native", [
            [wrapper, "--version"],
            [wrapper, "assembleDebug", "test", "lint"],
        ], cwd=android, paths=(android / ("gradlew.bat" if os.name == "nt" else "gradlew"),
                              android / "gradle/wrapper/gradle-wrapper.jar"),
            tools=("java",)))
    if platform.system() != "Darwin":
        results.append(Result("ios:native", "BLOCKED", "macOS/Xcode host required"))
    else:
        ios = mobile / "ios"
        results.append(grouped("ios:native", [
            ["xcodebuild", "-list", "-project", "LaptopWatchMobile.xcodeproj"],
            ["xcodebuild", "-workspace", "LaptopWatchMobile.xcworkspace",
             "-scheme", "LaptopWatchMobile", "-configuration", "Debug",
             "-destination", "generic/platform=iOS Simulator", "build"],
        ], cwd=ios, tools=("xcodebuild", "pod"),
            paths=(ios / "Podfile.lock", ios / "LaptopWatchMobile.xcworkspace/contents.xcworkspacedata")))
    # An independent signing authority must attest to all real-world gates.
    # Checking an owner's signed reports is not the same as doing the tests.
    if acceptance_manifest is None:
        results.append(Result("release:real-world-acceptance", "BLOCKED",
            "No independently signed device/security/merchant/legal acceptance manifest; "
            "see docs/RELEASE_RUNBOOK.md"))
    else:
        results.append(record_command("release:real-world-acceptance",
            [sys.executable, "ops/release_attestation.py",
             "--manifest", str(acceptance_manifest)],
            paths=(ROOT / "ops/release_attestation.py",
                   ROOT / "ops/release_trust.py"),
            tools=("git",)))
    return results


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="LaptopWatch strict, non-fabricated release gates")
    parser.add_argument("--mode", choices=("source", "full"), default="full")
    parser.add_argument("--json", type=Path, help="Write local non-secret result summary")
    parser.add_argument("--acceptance-manifest", type=Path,
                        help="Independent signed device, merchant, security and legal acceptance evidence")
    opts = parser.parse_args(argv)
    results = checks(opts.mode, opts.acceptance_manifest)
    for item in results:
        print(f"{item.status:7} {item.gate}: {item.detail} ({item.seconds:.1f}s)")
    approved = (opts.mode == "full" and
                any(item.gate == "release:real-world-acceptance" and item.status == "PASS"
                    for item in results) and
                all(item.status == "PASS" for item in results))
    message = {
        "mode": opts.mode,
        "release_approved": approved,
        "note": ("Independent acceptance verified for exactly this source revision; "
                 "the designated approver remains responsible for evidence authenticity."
                 if approved else "Source checks do not confer native/production acceptance."),
        "results": [asdict(item) for item in results],
    }
    if opts.json:
        opts.json.parent.mkdir(parents=True, exist_ok=True)
        opts.json.write_text(json.dumps(message, indent=2) + "\n", encoding="utf-8")
    # Always fail closed when ANY gate is unavailable or failed.
    return 0 if all(item.status == "PASS" for item in results) else 1


if __name__ == "__main__":
    raise SystemExit(main())

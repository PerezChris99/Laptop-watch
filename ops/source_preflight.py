"""Offline source integrity/parity preflight. NOT a native build.

Usage from repository root:
    python ops/source_preflight.py

This gate executes Python syntax and installed Node's JavaScript parser,
validates linkages and obvious unsafe placeholders, and fails on source
inconsistencies. It cannot certify compiled Kotlin/Swift/Rust/JSX or hardware.
"""
from __future__ import annotations

import compileall
import json
from pathlib import Path
import re
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
ERRORS: list[str] = []
SKIP = {"node_modules", "target", "dist", ".git", "build", ".gradle",
        "DerivedData", "Pods", "__pycache__", ".venv"}


def require_file(path: str, contains: tuple[str, ...] = ()) -> str:
    file = ROOT / path
    if not file.is_file():
        ERRORS.append(f"Required source missing: {path}")
        return ""
    try:
        src = file.read_text(encoding="utf-8")
    except (OSError, UnicodeDecodeError):
        ERRORS.append(f"Required source unreadable: {path}")
        return ""
    for label in contains:
        if label not in src:
            ERRORS.append(f"{path}: missing expected {label}")
    return src


def command(argv: list[str]) -> None:
    try:
        result = subprocess.run(argv, cwd=ROOT, check=False, timeout=40,
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    except (FileNotFoundError, subprocess.TimeoutExpired) as exc:
        ERRORS.append(f"Unavailable source parser: {argv[0]} ({type(exc).__name__})")
        return
    if result.returncode:
        ERRORS.append(f"Nonzero syntax check: {' '.join(argv)} (exit {result.returncode})")


def scan_sources() -> None:
    for part in ("desktop", "mobile"):
        base = ROOT / part
        if not base.is_dir():
            ERRORS.append(f"Missing client root: {part}")
            continue
        for file in base.rglob("*"):
            if any(p in SKIP for p in file.relative_to(ROOT).parts):
                continue
            if not file.is_file():
                continue
            if file.suffix in {".ts", ".tsx"} or file.name == "tsconfig.json":
                ERRORS.append(f"Forbidden client TypeScript: {file.relative_to(ROOT)}")

    for part in ("desktop/src-ui/src", "mobile/src"):
        base = ROOT / part
        for file in base.rglob("*"):
            if file.suffix not in {".js", ".jsx"} or not file.is_file():
                continue
            if any(p in SKIP for p in file.relative_to(ROOT).parts):
                continue
            value = file.read_text(encoding="utf-8")
            if "console.log(" in value or "http://" in value:
                ERRORS.append(f"Insecure client debug/cleartext string: {file.relative_to(ROOT)}")
            if (part == "mobile/src" and file.suffix == ".js" and
                    file.parent.name in {"crypto", "net", "storage", "pairing",
                                         "discovery", "background", "permissions",
                                         "reports", "notifications"}):
                if not value.startswith("// @ts-check"):
                    ERRORS.append(f"Missing @ts-check: {file.relative_to(ROOT)}")
                if "export " in value and "/**" not in value:
                    ERRORS.append(f"Missing JSDoc: {file.relative_to(ROOT)}")
            for imported in re.findall(r"\bfrom\s+['\"](\.[^'\"]+)['\"]", value):
                destination = file.parent / imported
                if not (destination.is_file() or
                        (destination.with_suffix(".js")).is_file() or
                        (destination.with_suffix(".jsx")).is_file()):
                    ERRORS.append(f"Unresolved local JS import: {file.relative_to(ROOT)} -> {imported}")
            if file.suffix == ".js":
                command(["node", "--check", str(file.relative_to(ROOT))])


def native_contract() -> None:
    checks = {
        "mobile/android/app/src/main/java/com/kc/laptopwatch/mobile/MainApplication.kt": (
            "LaptopWatchPinnedPackage()", "LaptopWatchForegroundPackage()",
            "LaptopWatchEvidencePackage()", "LaptopWatchEvidenceManagementPackage()",
            "LaptopWatchRelayPackage()", "LaptopWatchDiscoveryPackage()",
        ),
        "mobile/android/app/src/main/AndroidManifest.xml": (
            "LaptopWatchForegroundService", "LaptopWatchFcmService",
            "android:allowBackup=\"false\"", "android.permission.NEARBY_WIFI_DEVICES",
        ),
        "mobile/android/app/src/main/java/com/kc/laptopwatch/LaptopWatchForegroundService.kt": (
            "LW1", "/api/status", "Ed25519Signer", "startForeground",
        ),
        "mobile/ios/LaptopWatchPinnedTransport/Sources/LaptopWatchPinnedTransportBridge.m": (
            "sendPinned:", "downloadPinned:", "deleteLocal:", "exportLocal:",
            "sendSignedRelay:", "exportReport:",
        ),
        "mobile/src/net/SignedClient.js": (
            "GET /api/status", "POST /api/lock", "POST /api/alarm",
            "POST /api/warning/tts", "POST /api/config/person-monitor",
            "GET /api/location/history", "GET /api/evidence/list",
        ),
        "desktop/src-python/laptopwatch/gateway.py": (
            '"/api/evidence/list"', '"/api/location/history"',
            '"/api/webrtc/offer"', '"/api/config/person-monitor"',
        ),
        "desktop/src-tauri/src/main.rs": (
            "LAPTOPWATCH_OWNER_BEARER", "authenticated_health_probe",
            "restart_delay", "tauri_plugin_single_instance",
        ),
        "mobile/src/net/evidence.js": (
            "downloadEvidence", "deleteEvidenceLocal", "exportEvidenceLocal",
        ),
        "mobile/src/media/live.js": (
            "startLiveView", "mediaDevices.getUserMedia", "localMicrophone.getTracks()",
        ),
        "mobile/src/storage/db.js": (
            "encryptionKey", "cacheEncryptionKey", "laptopwatch-private-v2.sqlite",
        ),
    }
    for path, tokens in checks.items():
        require_file(path, tokens)

    manifest = require_file("mobile/package.json")
    if manifest:
        try:
            data = json.loads(manifest)
            if data.get("op-sqlite", {}).get("sqlcipher") is not True:
                ERRORS.append("SQLCipher must be explicitly enabled in mobile/package.json")
            if data.get("type") != "module":
                ERRORS.append("React Native JavaScript must stay ECMAScript module")
        except ValueError:
            ERRORS.append("Invalid mobile package manifest")
    jar = ROOT / "mobile/android/gradle/wrapper/gradle-wrapper.jar"
    if not jar.is_file() or not zipfile.is_zipfile(jar):
        ERRORS.append("Missing or corrupt official Android Gradle wrapper JAR")
    for path in (
        "mobile/android/gradlew", "mobile/android/gradlew.bat",
        "mobile/ios/Podfile", "mobile/ios/LaptopWatchMobile.xcodeproj/project.pbxproj",
        "mobile/ios/LaptopWatchPinnedTransport/LaptopWatchPinnedTransport.podspec",
        "desktop/src-python/laptopwatch/owner_health.py",
        "desktop/src-python/tests/test_location_history.py",
    ):
        if not (ROOT / path).is_file():
            ERRORS.append(f"Missing native project source: {path}")
    if not (ROOT / "app/src/main/java/com/example/security/SecureLaptopGateway.kt").is_file():
        ERRORS.append("Legacy Kotlin reference prematurely removed")
    for path in (ROOT / ".github/workflows", ROOT / "LICENSE"):
        if path.is_file() or path.is_dir():
            ERRORS.append(f"New workflow or owner-unapproved LICENSE must be reviewed: {path.relative_to(ROOT)}")


def design_and_python() -> None:
    path = ROOT / "design/tokens.json"
    if not path.is_file():
        ERRORS.append("Shared palette missing")
    else:
        colors = json.loads(path.read_text(encoding="utf-8")).get("colors", {})
        expected = {
            "paper": "#FAF9F5", "whiteSmoke": "#F5F5F5",
            "blue": "#2B5C88", "sweetYellow": "#F6D36B", "gold": "#C6A24B",
        }
        for key, value in expected.items():
            if colors.get(key) != value:
                ERRORS.append(f"Palette drift: {key}")
    command([sys.executable, "design/generate.py", "--check"])
    if not compileall.compile_dir(ROOT / "desktop/src-python/laptopwatch", quiet=1):
        ERRORS.append("Python sidecar compilation failed")
    if not compileall.compile_dir(ROOT / "desktop/src-python/tests", quiet=1):
        ERRORS.append("Python test source compilation failed")


def main() -> int:
    scan_sources()
    native_contract()
    design_and_python()
    for error in ERRORS:
        print("FAIL:", error, file=sys.stderr)
    if ERRORS:
        print(f"Source preflight: {len(ERRORS)} failure(s). No release acceptance.", file=sys.stderr)
        return 1
    print("Source parity preflight passed. Native builds/device/security verification are separate.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

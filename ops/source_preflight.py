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
import tomllib
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
            'payload == null', 'optBoolean("online", false)',
        ),
        "mobile/android/app/src/main/java/com/kc/laptopwatch/LaptopWatchEvidenceManagement.kt": (
            "fun deleteAllLocal(", "fun exportReport(", "content.none",
        ),
        "mobile/ios/LaptopWatchPinnedTransport/Sources/LaptopWatchPinnedTransport.swift": (
            "import UIKit", "@objc(deleteAllLocal:rejecter:)",
        ),
        "mobile/ios/LaptopWatchPinnedTransport/Sources/LaptopWatchPinnedTransportBridge.m": (
            "sendPinned:", "downloadPinned:", "deleteLocal:", "exportLocal:",
            "sendSignedRelay:", "exportReport:", "deleteAllLocal:",
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
            "downloadEvidence", "deleteEvidenceLocal", "exportEvidenceLocal", "purgePrivateEvidence",
        ),
        "mobile/src/media/live.js": (
            "startLiveView", "mediaDevices.getUserMedia", "localMicrophone.getTracks()",
        ),
        "mobile/src/storage/db.js": (
            "encryptionKey", "cacheEncryptionKey", "laptopwatch-private-v2.sqlite", "clearCachedAlerts",
        ),
        "mobile/src/App.jsx": (
            "await purgePrivateEvidence()", "await clearCachedAlerts(", "reconcilePushOnForeground",
        ),
        "mobile/android/app/build.gradle": (
            "laptopwatchFcmEnabled", "google-services.json",
        ),
        "mobile/android/app/src/main/java/com/kc/laptopwatch/LaptopWatchFcmService.kt": (
            "token_sync_pending", "onNewToken",
        ),
        "mobile/src/notifications/push.js": (
            "reconcilePushOnForeground", "acknowledgeTokenSync",
        ),

    }
    for path, tokens in checks.items():
        require_file(path, tokens)

    # A doubly escaped Kotlin Unicode literal is not a valid Char.
    android_export = require_file(
        "mobile/android/app/src/main/java/com/kc/laptopwatch/LaptopWatchEvidenceManagement.kt")
    bad_literal = "'" + chr(92) * 2 + "u0000'"
    if bad_literal in android_export:
        ERRORS.append("Android report export uses malformed NUL char literal")

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
    require_file("mobile/android/gradle/wrapper/gradle-wrapper.properties", (
        "gradle-9.4.1-bin.zip",
        "distributionSha256Sum=2ab2958f2a1e51120c326cad6f385153bb11ee93b3c216c5fccebfdfbb7ec6cb",
    ))
    require_file("mobile/android/build.gradle", ("ndkVersion = \"28.2.13676358\"",))
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
    # Owner explicitly authorized removal of the old Compose/Room app.
    # The root Gradle launcher and an unregistered adapter copy must not
    # reappear and confuse the sole React Native Android entrypoint.
    for obsolete in (
        "app", "mobile/android-native", "settings.gradle.kts",
        "build.gradle.kts", "gradle.properties", "gradle", "metadata.json",
    ):
        if (ROOT / obsolete).exists():
            ERRORS.append(f"Retired Kotlin build path reintroduced: {obsolete}")
    require_file("mobile/tests/source-retirement.test.js", (
        "legacy Kotlin", "mobile/android/app", "mobile/ios/",
    ))
    # Guard the owner-specific Firebase config against accidental git add.
    require_file("mobile/.gitignore", (
        "**/google-services.json", "**/GoogleService-Info.plist",
    ))
    if (ROOT / ".github/workflows").exists():
        ERRORS.append("GitHub Actions workflows are disabled by owner policy")
    # Owner explicitly approved proprietary licensing of original source.
    require_file("LICENSE", ("Proprietary Software License Notice", "Third-party components"))



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
    if not compileall.compile_dir(ROOT / "services/location-relay/laptopwatch_relay", quiet=1):
        ERRORS.append("Owner relay Python source compilation failed")
    if not compileall.compile_dir(ROOT / "services/location-relay/tests", quiet=1):
        ERRORS.append("Owner relay Python test compilation failed")
    if not compileall.compile_dir(ROOT / "services/billing/laptopwatch_billing", quiet=1):
        ERRORS.append("Billing service Python source compilation failed")
    if not compileall.compile_dir(ROOT / "services/billing/tests", quiet=1):
        ERRORS.append("Billing service Python test compilation failed")
    require_file("ops/lock_dependencies.py", ("--generate", "package-lock.json", "Cargo.lock", "Podfile.lock"))
    for path in ("desktop/src-python/pyproject.toml", "services/location-relay/pyproject.toml",
                 "services/billing/pyproject.toml"):
        value = require_file(path)
        if value:
            try:
                project = tomllib.loads(value)
                extras = project["project"]["optional-dependencies"]
                if "test" not in extras:
                    ERRORS.append(f"Missing test extra for {path}")
                if path == "services/location-relay/pyproject.toml" and "fcm" not in extras:
                    ERRORS.append("Relay needs explicit optional FCM dependencies")
            except (ValueError, KeyError, TypeError):
                ERRORS.append(f"Invalid Python package metadata: {path}")
    require_file("services/billing/laptopwatch_billing/app.py", (
        "verify_paddle", "verify_ticket", "verify_license",
        "subscription.", "claim_nonce", "Paddle-Signature",
        "LW-CHECKOUT-1", "/v1/billing/checkout-ticket",
    ))
    require_file("services/billing/tests/test_billing.py", (
        "test_webhook_verifier_tampering_and_replay", "test_device_caps_fail_closed",
        "test_subscription_renewal_requires_payment_for_that_period",
        "test_checkout_ticket_requires_live_validation_and_single_subscription_binding",
    ))
    require_file("desktop/src-python/laptopwatch/licensing.py", (
        "verify_license", "LicenseGate", "TRUSTED_ISSUER_PUBLIC_KEY_B64URL",
    ))
    require_file("services/billing/laptopwatch_billing/public/checkout.html", ("checkout.js", "LaptopWatch",))
    require_file("services/billing/laptopwatch_billing/public/checkout.js", (
        "Paddle.Checkout.open", "history.replaceState", "checkout-validation",
    ))
    command(["node", "--check", "services/billing/laptopwatch_billing/public/checkout.js"])
    require_file("services/location-relay/Dockerfile", (
        "COPY services/location-relay/pyproject.toml", "'.[fcm]'", 
    ))


def main() -> int:
    require_file("ops/verify_release.py", (
        "release_approved", "release:real-world-acceptance", "BLOCKED",
    ))
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

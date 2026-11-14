"""Non-network source-policy gate; no claim of native build readiness.

From repository root: python ops/source_preflight.py
"""
from __future__ import annotations

import compileall
import json
import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
ERRORS: list[str] = []


def command(argv: list[str]) -> None:
    result = subprocess.run(argv, cwd=ROOT, check=False)
    if result.returncode:
        ERRORS.append(f"Command exited {result.returncode}: {' '.join(argv)}")


def check() -> None:
    for part in ("desktop", "mobile"):
        base = ROOT / part
        if not base.is_dir():
            ERRORS.append(f"Missing {part}/")
            continue
        for item in base.rglob("*"):
            if any(x in {"node_modules", "target", "dist", ".git", "build", ".gradle"} for x in item.parts):
                continue
            if item.is_file() and (item.suffix in {".ts", ".tsx"} or item.name == "tsconfig.json"):
                ERRORS.append(f"Forbidden TypeScript file: {item.relative_to(ROOT)}")
    for part in ("desktop/src-ui/src", "mobile/src"):
        for item in (ROOT / part).rglob("*"):
            if item.suffix not in {".js", ".jsx"} or not item.is_file():
                continue
            body = item.read_text(encoding="utf-8")
            if "console.log(" in body or "http://" in body:
                ERRORS.append(f"Sensitive or cleartext practice to inspect: {item.relative_to(ROOT)}")
            if part == "mobile/src" and item.parent.name in {"crypto", "net", "storage", "pairing"}:
                if not body.startswith("// @ts-check"):
                    ERRORS.append(f"Missing @ts-check: {item.relative_to(ROOT)}")
                if "export " in body and "/**" not in body:
                    ERRORS.append(f"Missing JSDoc exports: {item.relative_to(ROOT)}")
    source = ROOT / "design/tokens.json"
    if not source.is_file():
        ERRORS.append("Missing shared design tokens")
    else:
        try:
            colors = json.loads(source.read_text(encoding="utf-8"))["colors"]
            expected = {
                "paper": "#FAF9F5", "whiteSmoke": "#F5F5F5", "surface": "#FFFFFF",
                "ink": "#1B2A33", "blue": "#2B5C88", "sweetYellow": "#F6D36B", "gold": "#C6A24B",
            }
            if any(colors.get(k) != v for k, v in expected.items()):
                ERRORS.append("Required owner palette changed")
        except (ValueError, KeyError):
            ERRORS.append("Invalid shared design token schema")
    command([sys.executable, "design/generate.py", "--check"])
    if not compileall.compile_dir(ROOT / "desktop/src-python/laptopwatch", quiet=1):
        ERRORS.append("Python sidecar syntax compile failed")
    command(["node", "--check", "mobile/src/crypto/protocol.js"])
    command(["node", "--check", "mobile/src/net/SignedClient.js"])
    command(["node", "--check", "mobile/src/pairing/pair.js"])
    command(["node", "--check", "mobile/src/net/pinnedTransport.js"])
    command(["node", "--check", "mobile/src/storage/identity.js"])


if __name__ == "__main__":
    check()
    for problem in ERRORS:
        print("FAIL:", problem, file=sys.stderr)
    if ERRORS:
        raise SystemExit(1)
    print("Source policy and syntax preflight passed (NOT a native build/test).")

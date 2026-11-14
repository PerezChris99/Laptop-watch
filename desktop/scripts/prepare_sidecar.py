"""Package the Python sidecar and derive OS icons from the checked-in original logo.

Usage from repository root:
  python -m pip install pyinstaller pillow
  python desktop/scripts/prepare_sidecar.py
  cd desktop && npm install && npm run tauri build

Target must be a native Rust host triple. Cross-compilation requires a
platform-specific build/signing environment and cannot be inferred here.
"""
from __future__ import annotations

import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
DESKTOP = ROOT / "desktop"
BIN = DESKTOP / "src-tauri" / "binaries"
ICONS = DESKTOP / "src-tauri" / "icons"


def main() -> None:
    from PIL import Image
    import PyInstaller.__main__

    ICONS.mkdir(parents=True, exist_ok=True)
    original = ROOT / "docs" / "images" / "app_icon.jpg"
    if not original.is_file():
        raise RuntimeError("Missing tracked original LaptopWatch logo")
    with Image.open(original) as img:
        img = img.convert("RGBA")
        for size in (32, 128):
            img.resize((size, size), Image.Resampling.LANCZOS).save(
                ICONS / f"{size}x{size}.png"
            )
        img.resize((512, 512), Image.Resampling.LANCZOS).save(ICONS / "icon.png")
        img.save(ICONS / "icon.ico", sizes=[(16,16),(32,32),(48,48),(64,64),(128,128),(256,256)])
        img.save(ICONS / "icon.icns")

    out = subprocess.check_output(["rustc", "-vV"], text=True)
    target = os.getenv("TAURI_TARGET_TRIPLE") or next(
        line.partition(": ")[2].strip() for line in out.splitlines()
        if line.startswith("host:")
    )
    BIN.mkdir(parents=True, exist_ok=True)
    entry = DESKTOP / "src-python" / "laptopwatch" / "cli.py"
    # -m module entry is safer for relative package imports. Avoid passing the
    # library file as a standalone script with broken import paths.
    bootstrap = DESKTOP / "scripts" / "sidecar_main.py"
    build = DESKTOP / ".sidecar-build"
    build.mkdir(parents=True, exist_ok=True)
    PyInstaller.__main__.run([
        "--noconfirm", "--clean", "--onefile",
        "--name", "laptopwatch-sidecar",
        "--paths", str(DESKTOP / "src-python"),
        "--distpath", str(build),
        "--workpath", str(build / "work"),
        "--specpath", str(build),
        str(bootstrap)
    ])
    extension = ".exe" if sys.platform == "win32" else ""
    built = build / ("laptopwatch-sidecar" + extension)
    final = BIN / ("laptopwatch-sidecar-" + target + extension)
    built.replace(final)
    print(f"Prepared {final}. Installer signing/notarization remains required.")


if __name__ == "__main__":
    main()

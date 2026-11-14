"""PyInstaller-compatible entrypoint for the bundled Python companion."""
from laptopwatch.cli import main

if __name__ == "__main__":
    raise SystemExit(main())

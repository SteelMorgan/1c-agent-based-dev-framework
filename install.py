#!/usr/bin/env python3
"""
Root-level wrapper for the framework installer.

The actual implementation lives in `tools/install.py`, but keeping this file in
the repo root makes README examples work out of the box:

    python install.py ...
"""

from pathlib import Path
from runpy import run_path


def main() -> None:
    repo_root = Path(__file__).resolve().parent
    run_path(str(repo_root / "tools" / "install.py"), run_name="__main__")


if __name__ == "__main__":
    main()


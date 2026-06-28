#!/usr/bin/env python3
"""Backward-compatible wrapper. Prefer: lmserver ..."""
from lmserver.board_client import main

if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""Backward-compatible wrapper. Prefer: platalea ..."""
from platalea.board_client import main

if __name__ == "__main__":
    raise SystemExit(main())

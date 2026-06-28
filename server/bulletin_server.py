#!/usr/bin/env python3
"""Backward-compatible wrapper. Prefer: lmserver start"""
from lmserver.bulletin_server import main

if __name__ == "__main__":
    raise SystemExit(main())

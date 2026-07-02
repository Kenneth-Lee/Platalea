"""Backward compatibility shim for the deprecated ``lmserver`` command."""
from __future__ import annotations

import sys

from .cli import main as platalea_main

_DEPRECATION = (
    "注意: 命令 lmserver 已弃用，请改用 platalea。"
    " 当前调用仍会继续执行。\n"
)


def main_lmserver(argv: list[str] | None = None) -> int:
    if argv is None:
        argv = sys.argv[1:]
    if argv and argv[0] not in {"-h", "--help", "--version", "-V"}:
        print(_DEPRECATION, file=sys.stderr, end="")
    elif not argv:
        print(_DEPRECATION, file=sys.stderr, end="")
    return platalea_main(argv)

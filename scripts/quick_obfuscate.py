#!/usr/bin/env python3
"""
兼容包装：快速混淆/反混淆已集成到 platalea 命令。

推荐用法:
  platalea obfuscate <文件或目录> -p <密码>
  platalea deobfuscate <文件或目录> -p <密码>
"""
from __future__ import annotations

import sys

from platalea.obfuscate import run_deobfuscate, run_obfuscate


def main() -> int:
    if len(sys.argv) >= 2 and sys.argv[1] == "deobfuscate":
        print(
            "注意: scripts/quick_obfuscate.py 已弃用，请改用 platalea deobfuscate。\n",
            file=sys.stderr,
            end="",
        )
        return run_deobfuscate(sys.argv[2:])
    if len(sys.argv) >= 2 and sys.argv[1] == "obfuscate":
        print(
            "注意: scripts/quick_obfuscate.py 已弃用，请改用 platalea obfuscate。\n",
            file=sys.stderr,
            end="",
        )
        return run_obfuscate(sys.argv[2:])
    print(__doc__, file=sys.stderr)
    return 2


if __name__ == "__main__":
    raise SystemExit(main())

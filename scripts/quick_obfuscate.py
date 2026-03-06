#!/usr/bin/env python3
"""
跨平台快速混淆/反混淆脚本，与 Local Manager 应用的「快速混淆」格式兼容。

用法:
  反混淆（.qx -> 原文件）:
    python quick_obfuscate.py deobfuscate <输入文件或目录> [选项]
  混淆（原文件 -> 可被本程序反混淆的 .qx 文件）:
    python quick_obfuscate.py obfuscate <输入文件或目录> [选项]

  选项:
    -p, --password <密码>    密码（必填）
    -o, --output <路径>       输出文件或目录（默认：同目录，反混淆时去掉 .qx，混淆时加上 .qx）
    --in-place                原地修改（仅单文件时建议先备份）
    -q, --quiet               少输出

与 Android 端一致：
  - 仅对文件前 min(文件大小, 4096) 字节做 XOR
  - 密钥：PBKDF2-HMAC-SHA256，盐 "LocalManagerQuickObfuscation"，1000 次迭代
  - 混淆后扩展名为 .qx
"""

from __future__ import annotations

import argparse
import hashlib
import sys
from pathlib import Path

HEADER_SIZE = 4096
EXTENSION = ".qx"
SALT = b"LocalManagerQuickObfuscation"
KDF_ITERATIONS = 1000


def derive_key(password: bytes, key_len: int) -> bytes:
    return hashlib.pbkdf2_hmac(
        "sha256",
        password,
        SALT,
        KDF_ITERATIONS,
        dklen=key_len,
    )


def xor_in_place(data: bytearray, key: bytes) -> None:
    assert len(data) <= len(key)
    for i in range(len(data)):
        data[i] ^= key[i]


def process_file(
    inp: Path,
    password: bytes,
    out_path: Path | None,
    in_place: bool,
) -> bool:
    """对文件头做 XOR（混淆/反混淆同一操作），写入 out_path 或原地。返回是否成功。"""
    if not inp.is_file():
        return False
    raw = inp.read_bytes()
    size = len(raw)
    if size == 0:
        if out_path and out_path != inp:
            out_path.write_bytes(b"")
        return True
    len_ = min(HEADER_SIZE, size)
    key = derive_key(password, len_)
    data = bytearray(raw[:len_])
    rest = raw[len_:]
    xor_in_place(data, key)
    result = bytes(data) + rest
    if in_place:
        inp.write_bytes(result)
    else:
        (out_path or inp).write_bytes(result)
    return True


def main() -> int:
    ap = argparse.ArgumentParser(
        description="与 Local Manager 兼容的快速混淆/反混淆",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    ap.add_argument(
        "mode",
        choices=("obfuscate", "deobfuscate"),
        help="obfuscate=混淆(生成 .qx)；deobfuscate=反混淆(去掉 .qx)",
    )
    ap.add_argument(
        "input",
        type=Path,
        help="输入文件或目录",
    )
    ap.add_argument(
        "-p", "--password",
        required=True,
        help="密码",
    )
    ap.add_argument(
        "-o", "--output",
        type=Path,
        default=None,
        help="输出文件或目录（默认：同目录/同名）",
    )
    ap.add_argument(
        "--in-place",
        action="store_true",
        help="原地修改（仅支持单文件）",
    )
    ap.add_argument(
        "-q", "--quiet",
        action="store_true",
        help="少输出",
    )
    args = ap.parse_args()
    password = args.password.encode("utf-8")
    if not password:
        ap.error("密码不能为空")
    inp = args.input.resolve()
    if not inp.exists():
        print(f"错误：不存在 {inp}", file=sys.stderr)
        return 1

    is_obfuscate = args.mode == "obfuscate"
    out_base = args.output.resolve() if args.output else None

    if inp.is_file():
        if is_obfuscate:
            if out_base is None:
                out_path = inp.parent / (inp.name + EXTENSION)
            elif out_base.is_dir():
                out_path = out_base / (inp.name + EXTENSION)
            else:
                out_path = out_base
            if args.in_place:
                tmp = inp.parent / (".tmp_" + inp.name + EXTENSION)
                if not process_file(inp, password, tmp, False):
                    print("处理失败", file=sys.stderr)
                    return 1
                inp.unlink()
                tmp.rename(inp.parent / (inp.name + EXTENSION))
                if not args.quiet:
                    print(f"已混淆（原地）: {inp.name} -> {inp.name}{EXTENSION}")
            else:
                if not process_file(inp, password, out_path, False):
                    print("处理失败", file=sys.stderr)
                    return 1
                if not args.quiet:
                    print(f"已混淆: {inp} -> {out_path}")
        else:
            # deobfuscate
            if not inp.name.endswith(EXTENSION):
                print(f"警告：文件名未以 {EXTENSION} 结尾，仍将对前 {HEADER_SIZE} 字节做 XOR 反混淆。", file=sys.stderr)
            if out_base is None:
                out_name = inp.name[: -len(EXTENSION)] if inp.name.endswith(EXTENSION) else inp.name + ".deob"
                out_path = inp.parent / out_name
            elif out_base.is_dir():
                out_name = inp.name[: -len(EXTENSION)] if inp.name.endswith(EXTENSION) else inp.name + ".deob"
                out_path = out_base / out_name
            else:
                out_path = out_base
            if args.in_place:
                out_name = inp.name[: -len(EXTENSION)] if inp.name.endswith(EXTENSION) else inp.name
                tmp = inp.parent / (".tmp_" + out_name)
                if not process_file(inp, password, tmp, False):
                    return 1
                inp.unlink()
                tmp.rename(inp.parent / out_name)
                if not args.quiet:
                    print(f"已反混淆（原地）: {inp.name} -> {out_name}")
            else:
                if not process_file(inp, password, out_path, False):
                    return 1
                if not args.quiet:
                    print(f"已反混淆: {inp} -> {out_path}")
        return 0

    # 目录
    if args.in_place:
        print("错误：--in-place 仅支持单文件", file=sys.stderr)
        return 1
    out_dir = out_base if (out_base and out_base.is_dir()) else (out_base if out_base else inp.parent / (inp.name + "_out"))
    if out_dir.exists() and not out_dir.is_dir():
        print("错误：输出路径已存在且不是目录", file=sys.stderr)
        return 1
    out_dir.mkdir(parents=True, exist_ok=True)
    ok, fail = 0, 0
    for f in inp.rglob("*"):
        if not f.is_file():
            continue
        rel = f.relative_to(inp)
        if is_obfuscate:
            out_path = out_dir / rel.parent / (f.name + EXTENSION)
        else:
            out_name = f.name[: -len(EXTENSION)] if f.name.endswith(EXTENSION) else f.name + ".deob"
            out_path = out_dir / rel.parent / out_name
        out_path.parent.mkdir(parents=True, exist_ok=True)
        if process_file(f, password, out_path, False):
            ok += 1
            if not args.quiet:
                print(out_path)
        else:
            fail += 1
            print(f"失败: {f}", file=sys.stderr)
    if not args.quiet:
        print(f"完成: {ok} 个成功, {fail} 个失败")
    return 0 if fail == 0 else 1


if __name__ == "__main__":
    sys.exit(main())

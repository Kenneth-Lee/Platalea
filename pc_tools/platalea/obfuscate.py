"""Quick obfuscation compatible with Local Manager Android (.qx files)."""
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


def transform_header_inplace(path: Path, password: bytes) -> bool:
    """仅读写文件头 min(size, 4096) 字节，不复制文件体。"""
    with path.open("r+b") as handle:
        handle.seek(0, 2)
        size = handle.tell()
        if size == 0:
            return True
        len_ = min(HEADER_SIZE, size)
        handle.seek(0)
        header = bytearray(handle.read(len_))
        if len(header) != len_:
            return False
        key = derive_key(password, len_)
        xor_in_place(header, key)
        handle.seek(0)
        handle.write(header)
        handle.flush()
    return True


def obfuscate_file_inplace(path: Path, password: bytes) -> bool:
    if not path.is_file() or path.name.endswith(EXTENSION):
        return False
    target = path.with_name(path.name + EXTENSION)
    if target.exists():
        return False
    if not transform_header_inplace(path, password):
        return False
    path.rename(target)
    return True


def deobfuscate_file_inplace(path: Path, password: bytes) -> bool:
    if not path.is_file():
        return False
    if path.name.endswith(EXTENSION):
        out_name = path.name[: -len(EXTENSION)]
    else:
        out_name = path.name + ".deob"
    target = path.with_name(out_name)
    if target.exists():
        return False
    if not transform_header_inplace(path, password):
        return False
    path.rename(target)
    return True


def run_obfuscate(argv: list[str] | None = None) -> int:
    return _run_mode("obfuscate", argv)


def run_deobfuscate(argv: list[str] | None = None) -> int:
    return _run_mode("deobfuscate", argv)


def _run_mode(mode: str, argv: list[str] | None) -> int:
    ap = argparse.ArgumentParser(
        prog=f"platalea {mode}",
        description="与 Local Manager 兼容的快速混淆/反混淆（.qx 格式，原地修改文件头）",
    )
    ap.add_argument("input", type=Path, help="输入文件或目录")
    ap.add_argument("-p", "--password", required=True, help="密码")
    ap.add_argument("-q", "--quiet", action="store_true", help="少输出")
    args = ap.parse_args(argv)

    password = args.password.encode("utf-8")
    if not password:
        ap.error("密码不能为空")

    inp = args.input.resolve()
    if not inp.exists():
        print(f"错误：不存在 {inp}", file=sys.stderr)
        return 1

    is_obfuscate = mode == "obfuscate"
    handler = obfuscate_file_inplace if is_obfuscate else deobfuscate_file_inplace

    if inp.is_file():
        if is_obfuscate and inp.name.endswith(EXTENSION):
            print(f"错误：已是 {EXTENSION} 文件，无需混淆: {inp}", file=sys.stderr)
            return 1
        if not is_obfuscate and not inp.name.endswith(EXTENSION):
            print(
                f"警告：文件名未以 {EXTENSION} 结尾，仍将对前 {HEADER_SIZE} 字节做 XOR 反混淆。",
                file=sys.stderr,
            )
        if not handler(inp, password):
            print(f"处理失败: {inp}", file=sys.stderr)
            return 1
        if not args.quiet:
            if is_obfuscate:
                print(f"已混淆: {inp.name} -> {inp.name}{EXTENSION}")
            else:
                out_name = inp.name[: -len(EXTENSION)] if inp.name.endswith(EXTENSION) else inp.name + ".deob"
                print(f"已反混淆: {inp.name} -> {out_name}")
        return 0

    if not inp.is_dir():
        print(f"错误：不是文件或目录: {inp}", file=sys.stderr)
        return 1

    ok, fail, skipped = 0, 0, 0
    for f in sorted(inp.rglob("*")):
        if not f.is_file():
            continue
        if is_obfuscate:
            if f.name.endswith(EXTENSION):
                skipped += 1
                continue
        elif not f.name.endswith(EXTENSION):
            skipped += 1
            continue
        if handler(f, password):
            ok += 1
            if not args.quiet:
                if is_obfuscate:
                    print(f"{f.name} -> {f.name}{EXTENSION}")
                else:
                    out_name = f.name[: -len(EXTENSION)] if f.name.endswith(EXTENSION) else f.name + ".deob"
                    print(f"{f.name} -> {out_name}")
        else:
            fail += 1
            print(f"失败: {f}", file=sys.stderr)

    if not args.quiet:
        print(f"完成: {ok} 个成功, {fail} 个失败, {skipped} 个跳过")
    return 0 if fail == 0 else 1

"""Quick mask compatible with Local Manager Android (.qx files)."""
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


def toggle_extension_name(name: str) -> str:
    return name[: -len(EXTENSION)] if name.endswith(EXTENSION) else f"{name}{EXTENSION}"


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


def mask_file_inplace(path: Path, password: bytes) -> bool:
    if not path.is_file():
        return False
    target = path.with_name(toggle_extension_name(path.name))
    if target.exists():
        return False
    if not transform_header_inplace(path, password):
        return False
    path.rename(target)
    return True


def obfuscate_file_inplace(path: Path, password: bytes) -> bool:
    if not path.is_file() or path.name.endswith(EXTENSION):
        return False
    return mask_file_inplace(path, password)


def deobfuscate_file_inplace(path: Path, password: bytes) -> bool:
    if not path.is_file() or not path.name.endswith(EXTENSION):
        return False
    return mask_file_inplace(path, password)


def run_mask(argv: list[str] | None = None) -> int:
    return _run_mode("mask", argv)


def _run_mode(mode: str, argv: list[str] | None) -> int:
    ap = argparse.ArgumentParser(
        prog=f"platalea file {mode}",
        description="与 Local Manager 兼容的快速遮罩（.qx 格式，原地修改文件头并自动切换扩展名）",
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

    if inp.is_file():
        out_name = toggle_extension_name(inp.name)
        if not mask_file_inplace(inp, password):
            print(f"处理失败: {inp}", file=sys.stderr)
            return 1
        if not args.quiet:
            print(f"已切换遮罩: {inp.name} -> {out_name}")
        return 0

    if not inp.is_dir():
        print(f"错误：不是文件或目录: {inp}", file=sys.stderr)
        return 1

    ok, fail = 0, 0
    for f in sorted(inp.rglob("*")):
        if not f.is_file():
            continue
        out_name = toggle_extension_name(f.name)
        if mask_file_inplace(f, password):
            ok += 1
            if not args.quiet:
                print(f"{f.name} -> {out_name}")
        else:
            fail += 1
            print(f"失败: {f}", file=sys.stderr)

    if not args.quiet:
        print(f"完成: {ok} 个成功, {fail} 个失败")
    return 0 if fail == 0 else 1

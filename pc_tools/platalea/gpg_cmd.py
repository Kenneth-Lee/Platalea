"""GPG encryption for .pass files and quick-crypto (快密), via local gpg binary."""
from __future__ import annotations

import argparse
import base64
import os
import re
import shutil
import subprocess
import sys
import tempfile
from contextlib import contextmanager
from pathlib import Path

from .paths import gnupg_dir

PASS_EXTENSION = ".pass"
PGP_ARMOR_BEGIN = "-----BEGIN PGP MESSAGE-----"


class GpgError(RuntimeError):
    pass


def find_gpg() -> str:
    gpg = shutil.which("gpg")
    if not gpg:
        raise GpgError("未找到 gpg 可执行文件，请安装 GnuPG（例如 apt install gnupg）")
    return gpg


def default_pubring_path() -> Path:
    return gnupg_dir() / "pubring.gpg"


def default_secring_path() -> Path:
    return gnupg_dir() / "secring.gpg"


def resolve_keyring(explicit: str | None, default: Path) -> Path:
    if explicit:
        path = Path(explicit).expanduser().resolve()
    else:
        path = default
    if not path.is_file():
        raise GpgError(f"密钥环不存在: {path}")
    return path


@contextmanager
def gpg_homedir(
    *,
    pubring: Path | None = None,
    secring: Path | None = None,
    homedir: Path | None = None,
):
    gpg = find_gpg()
    if homedir is not None:
        home = homedir.expanduser().resolve()
        if not home.is_dir():
            raise GpgError(f"GnuPG 目录不存在: {home}")
        yield gpg, home
        return

    with tempfile.TemporaryDirectory(prefix="platalea-gpg-") as tmp:
        home = Path(tmp)
        os.chmod(home, 0o700)
        import_cmd = [gpg, "--homedir", str(home), "--batch", "--yes", "--import"]
        if pubring is not None:
            _run_gpg([*import_cmd, str(pubring)], what="导入公钥")
        if secring is not None:
            _run_gpg([*import_cmd, str(secring)], what="导入私钥")
        yield gpg, home


def _run_gpg(
    cmd: list[str],
    *,
    what: str,
    input_bytes: bytes | None = None,
) -> subprocess.CompletedProcess[bytes]:
    try:
        proc = subprocess.run(
            cmd,
            input=input_bytes,
            capture_output=True,
            check=False,
        )
    except OSError as exc:
        raise GpgError(f"{what}失败: {exc}") from exc
    if proc.returncode != 0:
        err = proc.stderr.decode("utf-8", errors="replace").strip()
        raise GpgError(f"{what}失败 (exit {proc.returncode}): {err or proc.stdout.decode('utf-8', errors='replace')}")
    return proc


def _encrypt_public(
    *,
    plain: bytes,
    recipient: str,
    pubring: Path | None,
    homedir: Path | None,
    armor: bool,
    literal_name: str,
) -> bytes:
    with gpg_homedir(pubring=pubring, homedir=homedir) as (gpg, home):
        with tempfile.NamedTemporaryFile(prefix="platalea-plain-", delete=False) as plain_file:
            plain_path = Path(plain_file.name)
            plain_file.write(plain)
        out_path = plain_path.with_suffix(plain_path.suffix + ".pgp-out")
        try:
            cmd = [
                gpg,
                "--homedir",
                str(home),
                "--batch",
                "--yes",
                "--trust-model",
                "always",
                "--encrypt",
                "-r",
                recipient,
                "--set-filename",
                literal_name,
                "--output",
                str(out_path),
            ]
            if armor:
                cmd.insert(-4, "--armor")
            _run_gpg([*cmd, str(plain_path)], what="GPG 公钥加密")
            return out_path.read_bytes()
        finally:
            plain_path.unlink(missing_ok=True)
            out_path.unlink(missing_ok=True)


def _decrypt_bytes(
    encrypted: bytes,
    *,
    secring: Path | None,
    homedir: Path | None,
    passphrase: str,
) -> bytes:
    with gpg_homedir(secring=secring, homedir=homedir) as (gpg, home):
        with tempfile.NamedTemporaryFile(prefix="platalea-cipher-", delete=False) as cipher_file:
            cipher_path = Path(cipher_file.name)
            cipher_file.write(encrypted)
        try:
            proc = _run_gpg(
                [
                    gpg,
                    "--homedir",
                    str(home),
                    "--batch",
                    "--yes",
                    "--pinentry-mode",
                    "loopback",
                    "--passphrase",
                    passphrase,
                    "--decrypt",
                    str(cipher_path),
                ],
                what="GPG 解密",
            )
            return proc.stdout
        finally:
            cipher_path.unlink(missing_ok=True)


def decode_quick_ciphertext(text: str) -> bytes:
    normalized = text.strip()
    if not normalized:
        raise GpgError("密文为空")
    if PGP_ARMOR_BEGIN in normalized:
        return normalized.encode("utf-8")
    try:
        compact = re.sub(r"\s+", "", normalized)
        return base64.b64decode(compact, validate=True)
    except ValueError as exc:
        raise GpgError("快密密文无效：需要 Base64 或 ASCII armor") from exc


def _read_input_bytes(path: Path | None) -> bytes:
    if path is None:
        data = sys.stdin.buffer.read()
        if not data:
            raise GpgError("未提供输入（文件参数或 stdin）")
        return data
    if not path.is_file():
        raise GpgError(f"输入文件不存在: {path}")
    return path.read_bytes()


def _default_pass_output(input_path: Path) -> Path:
    name = input_path.name
    if name.endswith(PASS_EXTENSION):
        return input_path
    return input_path.with_name(name + PASS_EXTENSION)


def _default_pass_decrypt_output(input_path: Path) -> Path:
    name = input_path.name
    if name.endswith(PASS_EXTENSION):
        return input_path.with_name(name[: -len(PASS_EXTENSION)])
    return input_path.with_suffix("")


def run_pass_encrypt(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(
        prog="platalea pass-encrypt",
        description="公钥加密为 .pass 文件（OpenPGP ASCII armor，与 Android 密码保护兼容）",
    )
    ap.add_argument("input", nargs="?", type=Path, help="明文文件；省略则从 stdin 读取")
    ap.add_argument("-o", "--output", type=Path, help="输出 .pass 文件（默认同名加 .pass）")
    ap.add_argument("-r", "--recipient", required=True, help="收件人：密钥 ID、指纹或邮箱")
    ap.add_argument("--pubring", help=f"公钥环（默认 {default_pubring_path()}）")
    ap.add_argument("--homedir", type=Path, help="使用已有 GnuPG 目录，不再 --import 密钥环")
    ap.add_argument(
        "--literal-name",
        default="",
        help="写入 OpenPGP literal 的文件名（默认取输入文件名）",
    )
    args = ap.parse_args(argv)

    try:
        plain = _read_input_bytes(args.input)
        literal = args.literal_name.strip() or (args.input.name if args.input else "stdin.txt")
        pubring = None if args.homedir else resolve_keyring(args.pubring, default_pubring_path())
        encrypted = _encrypt_public(
            plain=plain,
            recipient=args.recipient,
            pubring=pubring,
            homedir=args.homedir,
            armor=True,
            literal_name=literal,
        )
        if args.input is not None:
            out_path = args.output or _default_pass_output(args.input.resolve())
        else:
            if args.output is None:
                ap.error("从 stdin 读取时必须指定 -o/--output")
            out_path = args.output
        out_path = out_path.expanduser().resolve()
        out_path.write_bytes(encrypted)
        print(f"已加密: {out_path}")
        return 0
    except GpgError as exc:
        print(str(exc), file=sys.stderr)
        return 1


def run_pass_decrypt(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(
        prog="platalea pass-decrypt",
        description="解密 .pass 文件（公钥加密，需本地私钥）",
    )
    ap.add_argument("input", nargs="?", type=Path, help=".pass 文件；省略则从 stdin 读取")
    ap.add_argument("-o", "--output", type=Path, help="输出明文文件（默认去掉 .pass 后缀）")
    ap.add_argument("-p", "--passphrase", required=True, help="私钥保护密码")
    ap.add_argument("--secret-keyring", help=f"私钥环（默认 {default_secring_path()}）")
    ap.add_argument("--homedir", type=Path, help="使用已有 GnuPG 目录")
    args = ap.parse_args(argv)

    try:
        encrypted = _read_input_bytes(args.input)
        secring = None if args.homedir else resolve_keyring(args.secret_keyring, default_secring_path())
        plain = _decrypt_bytes(
            encrypted,
            secring=secring,
            homedir=args.homedir,
            passphrase=args.passphrase,
        )
        if args.output is not None:
            out_path = args.output.expanduser().resolve()
            out_path.write_bytes(plain)
            print(f"已解密: {out_path}")
        elif args.input is not None:
            out_path = _default_pass_decrypt_output(args.input.resolve())
            out_path.write_bytes(plain)
            print(f"已解密: {out_path}")
        else:
            sys.stdout.buffer.write(plain)
        return 0
    except GpgError as exc:
        print(str(exc), file=sys.stderr)
        return 1


def run_quick_encrypt(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(
        prog="platalea quick-encrypt",
        description="快密加密：公钥加密后输出 Base64（与 Android 快密 Tab 兼容）",
    )
    ap.add_argument("text", nargs="?", help="明文；省略则从 stdin 读取")
    ap.add_argument("-r", "--recipient", required=True, help="收件人公钥 ID / 指纹 / 邮箱")
    ap.add_argument("--pubring", help=f"公钥环（默认 {default_pubring_path()}）")
    ap.add_argument("--homedir", type=Path, help="使用已有 GnuPG 目录")
    args = ap.parse_args(argv)

    try:
        if args.text is not None:
            plain = args.text.encode("utf-8")
        else:
            plain = sys.stdin.read().encode("utf-8")
            if not plain:
                raise GpgError("明文为空")
        pubring = None if args.homedir else resolve_keyring(args.pubring, default_pubring_path())
        encrypted = _encrypt_public(
            plain=plain,
            recipient=args.recipient,
            pubring=pubring,
            homedir=args.homedir,
            armor=False,
            literal_name="quick-text.txt",
        )
        encoded = base64.b64encode(encrypted).decode("ascii")
        print(encoded)
        return 0
    except GpgError as exc:
        print(str(exc), file=sys.stderr)
        return 1


def run_quick_decrypt(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(
        prog="platalea quick-decrypt",
        description="快密解密：Base64 或 ASCII armor 密文 → 明文 stdout",
    )
    ap.add_argument("text", nargs="?", help="密文；省略则从 stdin 读取")
    ap.add_argument("-p", "--passphrase", required=True, help="私钥保护密码")
    ap.add_argument("--secret-keyring", help=f"私钥环（默认 {default_secring_path()}）")
    ap.add_argument("--homedir", type=Path, help="使用已有 GnuPG 目录")
    args = ap.parse_args(argv)

    try:
        cipher_text = args.text if args.text is not None else sys.stdin.read()
        encrypted = decode_quick_ciphertext(cipher_text)
        secring = None if args.homedir else resolve_keyring(args.secret_keyring, default_secring_path())
        plain = _decrypt_bytes(
            encrypted,
            secring=secring,
            homedir=args.homedir,
            passphrase=args.passphrase,
        )
        sys.stdout.buffer.write(plain)
        return 0
    except GpgError as exc:
        print(str(exc), file=sys.stderr)
        return 1

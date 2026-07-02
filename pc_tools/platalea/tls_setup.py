"""Install TLS certificate materials into the user's ~/.localmanager/tls directory."""
from __future__ import annotations

import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

from .paths import default_ca_cert, dev_repo_tls_dir

TLS_FILE_NAMES = ("ca_cert.pem", "pc_server_cert.pem", "pc_server_key.pem")
BUNDLED_TLS_DIR = Path(__file__).resolve().parent / "bundled_tls"
DEFAULT_VALID_DAYS = 3650


def tls_materials_ready(tls_home: Path) -> bool:
    return all((tls_home / name).is_file() for name in TLS_FILE_NAMES)


def _copy_tls_tree(source: Path, destination: Path) -> None:
    destination.mkdir(parents=True, exist_ok=True)
    for name in TLS_FILE_NAMES:
        shutil.copy2(source / name, destination / name)


def _openssl_available() -> bool:
    try:
        result = subprocess.run(
            ["openssl", "version"],
            check=False,
            capture_output=True,
            text=True,
        )
        return result.returncode == 0
    except OSError:
        return False


def _generate_tls_with_openssl(tls_home: Path, *, days: int = DEFAULT_VALID_DAYS) -> None:
    if not _openssl_available():
        raise RuntimeError(
            "OpenSSL is not available. Install OpenSSL or run platalea init-tls from a machine "
            "that has the bundled development certificates."
        )

    tls_home.mkdir(parents=True, exist_ok=True)
    ca_key = tls_home / "ca_key.pem"
    ca_cert = tls_home / "ca_cert.pem"
    ca_serial = tls_home / "ca_cert.srl"
    pc_key = tls_home / "pc_server_key.pem"
    pc_csr = tls_home / "pc_server.csr"
    pc_cert = tls_home / "pc_server_cert.pem"

    with tempfile.TemporaryDirectory(prefix="platalea-tls-") as tmp:
        leaf_ext = Path(tmp) / "leaf_ext.cnf"
        leaf_ext.write_text(
            "\n".join(
                [
                    "basicConstraints=CA:FALSE",
                    "keyUsage=digitalSignature,keyEncipherment",
                    "extendedKeyUsage=serverAuth",
                    "subjectKeyIdentifier=hash",
                    "authorityKeyIdentifier=keyid,issuer",
                ]
            )
            + "\n",
            encoding="utf-8",
        )

        if not ca_key.exists() or not ca_cert.exists():
            subprocess.run(
                [
                    "openssl",
                    "req",
                    "-x509",
                    "-newkey",
                    "rsa:3072",
                    "-sha256",
                    "-days",
                    str(days),
                    "-nodes",
                    "-keyout",
                    str(ca_key),
                    "-out",
                    str(ca_cert),
                    "-subj",
                    "/CN=LocalManager Family TLS Root CA",
                    "-addext",
                    "basicConstraints=critical,CA:TRUE",
                    "-addext",
                    "keyUsage=critical,keyCertSign,cRLSign",
                    "-addext",
                    "subjectKeyIdentifier=hash",
                ],
                check=True,
                capture_output=True,
                text=True,
            )

        subprocess.run(
            [
                "openssl",
                "req",
                "-newkey",
                "rsa:3072",
                "-sha256",
                "-nodes",
                "-keyout",
                str(pc_key),
                "-out",
                str(pc_csr),
                "-subj",
                "/CN=LocalManager PC Service",
            ],
            check=True,
            capture_output=True,
            text=True,
        )
        subprocess.run(
            [
                "openssl",
                "x509",
                "-req",
                "-sha256",
                "-days",
                str(days),
                "-in",
                str(pc_csr),
                "-CA",
                str(ca_cert),
                "-CAkey",
                str(ca_key),
                "-CAcreateserial",
                "-CAserial",
                str(ca_serial),
                "-out",
                str(pc_cert),
                "-extfile",
                str(leaf_ext),
            ],
            check=True,
            capture_output=True,
            text=True,
        )


def _candidate_tls_sources() -> list[tuple[str, Path]]:
    sources: list[tuple[str, Path]] = []
    bundled = BUNDLED_TLS_DIR
    if bundled.is_dir():
        sources.append(("bundled development certificates", bundled))
    repo_tls = dev_repo_tls_dir()
    if repo_tls.is_dir() and repo_tls != bundled:
        sources.append((f"repository TLS directory ({repo_tls})", repo_tls))
    return sources


def ensure_tls_materials(config_path: Path, *, force: bool = False) -> Path:
    """Ensure PC server TLS files exist under the configured TLS directory."""
    tls_home = default_ca_cert(config_path).parent
    if tls_materials_ready(tls_home) and not force:
        return tls_home

    tls_home.mkdir(parents=True, exist_ok=True)
    for label, source in _candidate_tls_sources():
        if tls_materials_ready(source):
            _copy_tls_tree(source, tls_home)
            print(f"Installed TLS materials from {label} into {tls_home}", file=sys.stderr)
            return tls_home

    print("Generating new TLS materials with OpenSSL...", file=sys.stderr)
    _generate_tls_with_openssl(tls_home)
    print(
        f"Generated TLS materials in {tls_home}. "
        "If Android clients cannot connect, regenerate matching CA assets with "
        "pc_tools/generate_tls_materials.sh and rebuild the app.",
        file=sys.stderr,
    )
    return tls_home

"""LocalManager PC tools: family bulletin board service, CLI, and file utilities."""
from __future__ import annotations

from pathlib import Path

__all__ = ["__version__"]


def _read_version_from_file() -> str | None:
    for candidate in (
        Path(__file__).resolve().parents[1] / "VERSION",
        Path(__file__).resolve().parents[2] / "VERSION",
    ):
        if candidate.is_file():
            for line in candidate.read_text(encoding="utf-8").splitlines():
                text = line.strip()
                if text and not text.startswith("#"):
                    return text
    return None


def _read_version() -> str:
    from_file = _read_version_from_file()
    if from_file:
        return from_file
    try:
        from importlib.metadata import PackageNotFoundError, version

        return version("platalea")
    except PackageNotFoundError:
        try:
            from importlib.metadata import version as pkg_version

            return pkg_version("lmserver")
        except PackageNotFoundError:
            pass
    except Exception:
        pass
    return "0.0.0"


__version__ = _read_version()

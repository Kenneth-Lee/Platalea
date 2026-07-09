from __future__ import annotations

import json
from pathlib import Path

from .models import ControlState


def load_control_state(path: Path) -> ControlState | None:
    if not path.is_file():
        return None
    raw = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(raw, dict):
        raise ValueError(f"控制面状态文件根节点不是对象: {path}")
    return ControlState.from_json_dict(raw)


def save_control_state(path: Path, state: ControlState) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(state.to_json_dict(), ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )

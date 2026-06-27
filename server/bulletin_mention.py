from __future__ import annotations

from typing import Any

from bulletin_ai_internal import is_ai_status_message

AGENT_DEVICE_PREFIX = "agent:"


def collect_participants(messages: list[Any]) -> list[str]:
    labels: list[str] = []
    seen: set[str] = set()
    for message in messages:
        if getattr(message, "deleted", False):
            continue
        if is_ai_status_message(message):
            continue
        device = (getattr(message, "author_device", None) or "").strip()
        if device.startswith(AGENT_DEVICE_PREFIX):
            continue
        label = (getattr(message, "author_label", "") or "").strip()
        if not label or label in seen:
            continue
        seen.add(label)
        labels.append(label)
    return sorted(labels, key=str.casefold)


def enrich_board_payload(
    payload: dict[str, Any],
    *,
    agents: list[str],
    participants: list[str],
    commands: list[str] | None = None,
) -> dict[str, Any]:
    enriched = dict(payload)
    enriched["agents"] = agents
    enriched["participants"] = participants
    enriched["commands"] = commands if commands is not None else []
    return enriched

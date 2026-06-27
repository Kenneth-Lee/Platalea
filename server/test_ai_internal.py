#!/usr/bin/env python3
from __future__ import annotations

import unittest

from bulletin_ai_internal import (
    MESSAGE_KIND_AI_STATUS,
    format_ai_status_content,
    is_ai_status_message,
    is_conversation_message,
    parse_ai_control_command,
)
from bulletin_store import BulletinBoardStore, BulletinMessage


class AiInternalTest(unittest.TestCase):
    def test_parse_control_commands(self) -> None:
        self.assertEqual(parse_ai_control_command("/stopai"), ("stop", ""))
        self.assertEqual(parse_ai_control_command("/ai status"), ("status", ""))
        self.assertIsNone(parse_ai_control_command("hello"))

    def test_ai_status_message_detection(self) -> None:
        msg = BulletinMessage(
            id="1",
            seq=1,
            author_label="AI-qwen",
            content=format_ai_status_content("正在访问网页…"),
            created_at=1,
            updated_at=1,
            message_kind=MESSAGE_KIND_AI_STATUS,
        )
        self.assertTrue(is_ai_status_message(msg))
        self.assertFalse(is_conversation_message(msg))

    def test_append_ai_status(self) -> None:
        import tempfile
        from pathlib import Path

        with tempfile.TemporaryDirectory() as tmp:
            store = BulletinBoardStore(Path(tmp))
            posted = store.append_ai_status("default", "AI-qwen", "正在思考…")
            assert posted is not None
            snapshot = store.snapshot("default")
            assert snapshot is not None
            self.assertEqual(len(snapshot.messages), 1)
            self.assertTrue(is_ai_status_message(snapshot.messages[0]))
            boards = store.list_boards()
            self.assertEqual(boards[0].message_count, 0)


if __name__ == "__main__":
    unittest.main()

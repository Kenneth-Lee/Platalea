#!/usr/bin/env python3
from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from platalea.bulletin_agent import extract_mention_for_models, format_board_context
from platalea.bulletin_agent_status import AgentRunHandle, AgentStatusReporter
from platalea.bulletin_agent_tools import (
    AgentToolExecutor,
    AgentToolsConfig,
    collect_board_attachments,
    fetch_url_text,
    parse_tool_arguments,
    read_attachment_text,
)
from platalea.bulletin_attachment_store import BulletinAttachmentStore
from platalea.bulletin_store import BulletinBoardStore, BulletinMessage


class AgentToolsTest(unittest.TestCase):
    def setUp(self) -> None:
        self._tmpdir = tempfile.TemporaryDirectory()
        self.root = Path(self._tmpdir.name)
        self.store = BulletinBoardStore(self.root)
        self.board_id = "default"

    def tearDown(self) -> None:
        self._tmpdir.cleanup()

    def _post_with_file_attachment(self, filename: str, content: bytes) -> str:
        att_store: BulletinAttachmentStore = self.store.attachments
        init = att_store.init_file_upload(self.board_id, filename, len(content))
        assert init is not None
        att_store.write_file_chunk(self.board_id, init.attachment_id, 0, content)
        att_store.complete_upload(self.board_id, init.attachment_id)
        message = self.store.append_message(
            self.board_id,
            "tester",
            "请看附件",
            attachments=[
                {
                    "id": init.attachment_id,
                    "kind": "file",
                    "name": filename,
                    "size": len(content),
                }
            ],
        )
        assert message is not None
        return init.attachment_id

    def test_list_and_read_attachment(self) -> None:
        attachment_id = self._post_with_file_attachment("note.txt", b"hello bulletin\nline2")
        snapshot = self.store.snapshot(self.board_id)
        assert snapshot is not None
        executor = AgentToolExecutor(
            self.store,
            self.board_id,
            snapshot.messages,
            AgentToolsConfig(),
        )
        listed = json.loads(executor.execute("list_attachment_files", {}))
        self.assertTrue(listed["ok"])
        self.assertEqual(len(listed["attachments"]), 1)
        self.assertEqual(listed["attachments"][0]["attachment_id"], attachment_id)

        read_back = json.loads(
            executor.execute("read_attachment_file", {"attachment_id": attachment_id})
        )
        self.assertTrue(read_back["ok"])
        self.assertIn("hello bulletin", read_back["content"])

    def test_read_directory_attachment(self) -> None:
        from platalea.bulletin_attachment_store import DirectoryEntry

        att_store = self.store.attachments
        init = att_store.init_directory_upload(
            self.board_id,
            "docs",
            [DirectoryEntry("readme.md", 12), DirectoryEntry("skip.bin", 4)],
        )
        assert init is not None
        slots = {slot.path: slot.file_id for slot in init.directory_files}
        att_store.write_directory_file_chunk(
            self.board_id, init.attachment_id, slots["readme.md"], 0, b"# Title\n"
        )
        att_store.write_directory_file_chunk(
            self.board_id, init.attachment_id, slots["skip.bin"], 0, b"\x00\x01"
        )
        att_store.complete_upload(self.board_id, init.attachment_id)
        self.store.append_message(
            self.board_id,
            "tester",
            "目录附件",
            attachments=[
                {
                    "id": init.attachment_id,
                    "kind": "directory",
                    "name": "docs",
                    "file_count": 2,
                    "total_size": 16,
                }
            ],
        )
        result = read_attachment_text(
            self.store,
            self.board_id,
            init.attachment_id,
            file_path="readme.md",
            max_bytes=10_000,
        )
        self.assertTrue(result["ok"])
        self.assertIn("# Title", result["content"])

        binary = read_attachment_text(
            self.store,
            self.board_id,
            init.attachment_id,
            file_path="skip.bin",
            max_bytes=10_000,
        )
        self.assertFalse(binary["ok"])

    def test_parse_tool_arguments(self) -> None:
        self.assertEqual(parse_tool_arguments('{"url":"https://example.com"}'), {"url": "https://example.com"})
        with self.assertRaises(ValueError):
            parse_tool_arguments("not-json")

    def test_collect_board_attachments(self) -> None:
        messages = [
            BulletinMessage(
                id="1",
                seq=1,
                author_label="a",
                content="x",
                created_at=1,
                updated_at=1,
                attachments=[{"id": "att1", "kind": "file", "name": "a.txt"}],
            )
        ]
        items = collect_board_attachments(messages)
        self.assertEqual(len(items), 1)
        self.assertEqual(items[0]["attachment_id"], "att1")

    def test_format_board_context_shows_attachment_ids(self) -> None:
        messages = [
            BulletinMessage(
                id="1",
                seq=1,
                author_label="u",
                content="总结附件",
                created_at=1,
                updated_at=1,
                attachments=[{"id": "abc", "kind": "file", "name": "doc.md"}],
            )
        ]
        context, omitted = format_board_context(messages, ["qwen2.5"])
        self.assertEqual(omitted, 0)
        self.assertIn("abc", context)
        self.assertIn("doc.md", context)

    def test_format_board_context_trims_to_recent(self) -> None:
        messages = [
            BulletinMessage(
                id=str(i),
                seq=i,
                author_label="u",
                content=f"消息{i} " + ("x" * 50),
                created_at=i,
                updated_at=i,
            )
            for i in range(1, 11)
        ]
        context, omitted = format_board_context(messages, ["qwen2.5"], max_chars=300)
        self.assertGreater(omitted, 0)
        self.assertIn("已省略较早", context)
        self.assertIn("消息10", context)
        self.assertNotIn("消息1 ", context)

    def test_load_agent_config_model_name_list(self) -> None:
        from platalea.bulletin_agent import load_agent_config

        cfg = load_agent_config(
            {
                "enabled": True,
                "model_name": ["gpt-oss:latest", "qwen3.6:27b-coding-nvfp4"],
            }
        )
        assert cfg is not None
        self.assertEqual(
            cfg.model_names,
            ("gpt-oss:latest", "qwen3.6:27b-coding-nvfp4"),
        )

    def test_load_agent_config_models_list(self) -> None:
        from platalea.bulletin_agent import load_agent_config

        cfg = load_agent_config(
            {
                "enabled": True,
                "models": ["a", "b"],
            }
        )
        assert cfg is not None
        self.assertEqual(cfg.model_names, ("a", "b"))

    def test_extract_mention_for_models(self) -> None:
        got = extract_mention_for_models("@gpt-oss:latest 你好", ("qwen2.5", "gpt-oss:latest"))
        self.assertEqual(got, ("gpt-oss:latest", "你好"))

    def test_agent_status_reporter_multiple_updates(self) -> None:
        handle = AgentRunHandle(
            board_id=self.board_id,
            model_name="qwen2.5",
            author_label="AI-qwen2.5",
            author_device="agent:qwen2.5",
        )
        reporter = AgentStatusReporter(self.store, handle, heartbeat_seconds=3600.0)
        reporter.update("第一阶段")
        reporter.update("第二阶段")
        reporter.clear()

        snapshot = self.store.snapshot(self.board_id)
        assert snapshot is not None
        self.assertEqual(len(snapshot.messages), 0)


if __name__ == "__main__":
    unittest.main()

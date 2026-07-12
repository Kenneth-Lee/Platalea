from __future__ import annotations

import json
import tempfile
import unittest
from argparse import Namespace
from pathlib import Path
from unittest import mock

from platalea.board_client import _dispatch_board_client, build_parser, resolve_connection_args


class BoardClientConnectionArgsTest(unittest.TestCase):
    def test_resolve_connection_args_returns_three_values(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            config_path = Path(tmp) / "config.json"
            config_path.write_text(
                json.dumps(
                    {
                        "port": 8765,
                        "roles": {
                            "admin": {
                                "password": "imhost",
                            }
                        },
                    }
                ),
                encoding="utf-8",
            )
            args = Namespace(
                host="klmm.local",
                port=None,
                password="",
                config=str(config_path),
            )

            self.assertEqual(resolve_connection_args(args), ("klmm.local", 8765, "imhost"))

    def test_post_with_attachment_does_not_unpack_four_values(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            config_path = Path(tmp) / "config.json"
            config_path.write_text(
                json.dumps(
                    {
                        "port": 8765,
                        "roles": {
                            "admin": {
                                "password": "imhost",
                            }
                        },
                        "default_board": "default",
                    }
                ),
                encoding="utf-8",
            )
            parser = build_parser(prog="platalea")
            args = parser.parse_args(
                [
                    "--host",
                    "klmm.local",
                    "--password",
                    "imhost",
                    "--config",
                    str(config_path),
                    "post",
                    "--attach",
                    "/tmp/app-release.apk",
                ]
            )

            with mock.patch(
                "platalea.board_client._upload_attachment_refs",
                return_value=[{"id": "att-1", "name": "app-release.apk", "kind": "file"}],
            ), mock.patch(
                "platalea.board_client.request_api",
                return_value=(
                    200,
                    {
                        "ok": True,
                        "message": {
                            "id": "msg-1",
                            "author_label": "pc-cli",
                            "attachments": [{"id": "att-1", "name": "app-release.apk", "kind": "file"}],
                        },
                    },
                ),
            ):
                exit_code = _dispatch_board_client(args, parser)

            self.assertEqual(exit_code, 0)


if __name__ == "__main__":
    unittest.main()
#!/usr/bin/env python3
from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from platalea.bulletin_roles import ADMIN_ROLE_ID, AuthContext, can_access_board
from platalea.bulletin_server import handle_board_request
from platalea.bulletin_store import BulletinBoardStore


class BulletinBoardAccessTests(unittest.TestCase):
    def setUp(self) -> None:
        self._tmpdir = tempfile.TemporaryDirectory()
        self.store = BulletinBoardStore(Path(self._tmpdir.name))
        self.admin = AuthContext(
            role_id=ADMIN_ROLE_ID,
            role_label="管理员",
            is_admin=True,
            can_create_boards=True,
            can_manage_boards=True,
        )
        self.guest = AuthContext(
            role_id="guest",
            role_label="访客",
            is_admin=False,
            can_create_boards=False,
            can_manage_boards=False,
        )

    def tearDown(self) -> None:
        self._tmpdir.cleanup()

    def test_list_boards_filters_guest(self) -> None:
        public = self.store.create_board("公开", role_ids=("guest",))
        private = self.store.create_board("私密", role_ids=())
        assert public is not None
        assert private is not None

        admin_response = handle_board_request(self.store, "GET", "/boards", b"", self.admin, {})
        guest_response = handle_board_request(self.store, "GET", "/boards", b"", self.guest, {})

        admin_ids = {item["id"] for item in admin_response["body"]["boards"]}
        guest_ids = {item["id"] for item in guest_response["body"]["boards"]}
        self.assertIn(public.id, admin_ids)
        self.assertIn(private.id, admin_ids)
        self.assertIn(public.id, guest_ids)
        self.assertNotIn(private.id, guest_ids)

    def test_admin_can_delete_private_board(self) -> None:
        private = self.store.create_board("私密", role_ids=())
        assert private is not None
        response = handle_board_request(
            self.store,
            "DELETE",
            f"/boards/{private.id}",
            b"",
            self.admin,
            {},
        )
        self.assertEqual(response["status"].value, 200)
        self.assertIsNone(self.store.get_board_info(private.id))

    def test_guest_cannot_access_private_messages(self) -> None:
        private = self.store.create_board("私密", role_ids=())
        assert private is not None
        response = handle_board_request(
            self.store,
            "GET",
            f"/boards/{private.id}/messages",
            b"",
            self.guest,
            {},
        )
        self.assertEqual(response["body"]["error"], "board_not_found")

    def test_create_board_rejects_duplicate_name(self) -> None:
        first = self.store.create_board("徐州行")
        second = self.store.create_board("徐州行")
        self.assertIsNotNone(first)
        self.assertIsNone(second)
        self.assertTrue(self.store.is_board_name_taken("徐州行"))

    def test_rename_board_rejects_duplicate_name(self) -> None:
        first = self.store.create_board("A板")
        second = self.store.create_board("B板")
        assert first is not None and second is not None
        updated = self.store.update_board(second.id, name="A板")
        self.assertIsNone(updated)

    def test_create_board_http_duplicate_name(self) -> None:
        self.store.create_board("徐州行")
        response = handle_board_request(
            self.store,
            "POST",
            "/boards",
            json.dumps({"name": "徐州行"}).encode("utf-8"),
            self.admin,
            {},
        )
        self.assertEqual(response["status"].value, 400)
        self.assertEqual(response["body"]["error"], "board_name_duplicate")

    def test_list_boards_includes_role_class(self) -> None:
        response = handle_board_request(self.store, "GET", "/boards", b"", self.guest, {})
        self.assertEqual(response["body"]["role_class"], "user")
        admin_response = handle_board_request(self.store, "GET", "/boards", b"", self.admin, {})
        self.assertEqual(admin_response["body"]["role_class"], "admin")

    def test_create_board_strips_admin_from_role_ids(self) -> None:
        response = handle_board_request(
            self.store,
            "POST",
            "/boards",
            json.dumps({"name": "旅行", "role_ids": ["admin", "guest"]}).encode("utf-8"),
            self.admin,
            {},
        )
        board = response["body"]["board"]
        self.assertEqual(board["role_ids"], ["guest"])
        self.assertTrue(can_access_board(self.guest, ("guest",)))


if __name__ == "__main__":
    unittest.main()

#!/usr/bin/env python3
from __future__ import annotations

import unittest

from platalea.bulletin_roles import (
    ADMIN_ROLE_ID,
    AuthContext,
    can_access_board,
    load_roles_config,
    normalize_stored_role_ids,
)


class BulletinRolesTests(unittest.TestCase):
    def test_admin_always_sees_board_without_role_ids(self) -> None:
        auth = AuthContext(
            role_id=ADMIN_ROLE_ID,
            role_label="管理员",
            is_admin=True,
            can_create_boards=True,
            can_manage_boards=True,
        )
        self.assertTrue(can_access_board(auth, ()))
        self.assertTrue(can_access_board(auth, ("guest",)))
        self.assertTrue(can_access_board(auth, None))

    def test_guest_filtered_by_role_ids(self) -> None:
        auth = AuthContext(
            role_id="guest",
            role_label="访客",
            is_admin=False,
            can_create_boards=False,
            can_manage_boards=False,
        )
        self.assertTrue(can_access_board(auth, ("guest", "family")))
        self.assertFalse(can_access_board(auth, ()))
        self.assertFalse(can_access_board(auth, ("family",)))

    def test_legacy_board_without_role_ids_visible_to_guest(self) -> None:
        auth = AuthContext(
            role_id="guest",
            role_label=None,
            is_admin=False,
            can_create_boards=False,
            can_manage_boards=False,
        )
        self.assertTrue(can_access_board(auth, None))

    def test_normalize_stored_role_ids_strips_admin(self) -> None:
        self.assertEqual(
            normalize_stored_role_ids(["admin", "guest", "guest", ""]),
            ("guest",),
        )

    def test_legacy_password_mapping(self) -> None:
        roles = load_roles_config(
            {},
            guest_password="guest",
            host_password="host",
        )
        admin = roles.resolve({"X-Network-Service-Password": "host"})
        guest = roles.resolve({"X-Network-Service-Password": "guest"})
        self.assertIsNotNone(admin)
        self.assertIsNotNone(guest)
        assert admin is not None
        assert guest is not None
        self.assertTrue(admin.is_admin)
        self.assertFalse(guest.is_admin)

    def test_roles_config_requires_admin(self) -> None:
        with self.assertRaises(ValueError):
            load_roles_config({"roles": {"guest": {"password": "x"}}}, guest_password=None, host_password=None)

    def test_duplicate_password_rejected(self) -> None:
        with self.assertRaises(ValueError):
            load_roles_config(
                {
                    "roles": {
                        "admin": {"password": "same"},
                        "guest": {"password": "same"},
                    }
                },
                guest_password=None,
                host_password=None,
            )

    def test_resolve_prefers_admin_password(self) -> None:
        roles = load_roles_config(
            {
                "roles": {
                    "admin": {"password": "secret", "label": "管理员"},
                    "guest": {"password": "guest", "label": "访客"},
                }
            },
            guest_password=None,
            host_password=None,
        )
        auth = roles.resolve({"X-Network-Service-Password": "secret"})
        assert auth is not None
        self.assertTrue(auth.is_admin)
        self.assertEqual(auth.role_class, "admin")

    def test_auth_session_fields_include_role_class(self) -> None:
        guest = AuthContext(
            role_id="guest",
            role_label="访客",
            is_admin=False,
            can_create_boards=False,
            can_manage_boards=False,
        )
        from platalea.bulletin_roles import auth_session_fields

        fields = auth_session_fields(guest)
        self.assertEqual(fields["role_class"], "user")
        self.assertEqual(fields["role_id"], "guest")


if __name__ == "__main__":
    unittest.main()

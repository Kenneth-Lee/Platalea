from __future__ import annotations

from argparse import Namespace
from unittest import mock

from platalea.board_client import _upload_attachment_refs


def _fake_args(command: str = "post") -> Namespace:
    return Namespace(
        host="127.0.0.1",
        port=8765,
        password="host",
        config="/tmp/does-not-matter.json",
        ca_cert="/tmp/ca.pem",
        tls_fingerprint="",
        json=False,
        command=command,
    )


def test_upload_attachment_refs_post_multi_paths_uses_directory_merge() -> None:
    args = _fake_args("post")
    with mock.patch(
        "platalea.board_attachment_client.upload_paths_as_directory_attachment",
        return_value={"id": "att-dir", "kind": "directory", "name": "attachments"},
    ) as merged_mock, mock.patch(
        "platalea.board_attachment_client.upload_paths",
        return_value=[{"id": "att-file", "kind": "file", "name": "a.txt"}],
    ):
        refs = _upload_attachment_refs(args, "default", ["/tmp/a.txt", "/tmp/b.txt"])

    assert refs == [{"id": "att-dir", "kind": "directory", "name": "attachments"}]
    merged_mock.assert_called_once()


def test_upload_attachment_refs_modify_multi_paths_keeps_individual_uploads() -> None:
    args = _fake_args("modify")
    with mock.patch(
        "platalea.board_attachment_client.upload_paths_as_directory_attachment",
        return_value={"id": "att-dir", "kind": "directory", "name": "attachments"},
    ) as merged_mock, mock.patch(
        "platalea.board_attachment_client.upload_paths",
        return_value=[
            {"id": "att-a", "kind": "file", "name": "a.txt"},
            {"id": "att-b", "kind": "file", "name": "b.txt"},
        ],
    ) as upload_mock:
        refs = _upload_attachment_refs(args, "default", ["/tmp/a.txt", "/tmp/b.txt"])

    assert refs == [
        {"id": "att-a", "kind": "file", "name": "a.txt"},
        {"id": "att-b", "kind": "file", "name": "b.txt"},
    ]
    merged_mock.assert_not_called()
    upload_mock.assert_called_once()


def test_upload_attachment_refs_post_single_path_keeps_individual_upload() -> None:
    args = _fake_args("post")
    with mock.patch(
        "platalea.board_attachment_client.upload_paths_as_directory_attachment",
        return_value={"id": "att-dir", "kind": "directory", "name": "attachments"},
    ) as merged_mock, mock.patch(
        "platalea.board_attachment_client.upload_paths",
        return_value=[{"id": "att-file", "kind": "file", "name": "a.txt"}],
    ) as upload_mock:
        refs = _upload_attachment_refs(args, "default", ["/tmp/a.txt"])

    assert refs == [{"id": "att-file", "kind": "file", "name": "a.txt"}]
    merged_mock.assert_not_called()
    upload_mock.assert_called_once()

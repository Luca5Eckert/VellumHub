"""Offline tests for release metadata verification; no Docker daemon required."""
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

MODULE = Path(__file__).with_name("image_artifact.py")
spec = importlib.util.spec_from_file_location("image_artifact", MODULE)
image_artifact = importlib.util.module_from_spec(spec)
spec.loader.exec_module(image_artifact)

COMMIT = "a" * 40
TAG = f"ghcr.io/luca5eckert/vellumhub-gateway:{COMMIT}"
CONFIG = "sha256:" + "b" * 64


class ImageArchiveEvidenceTests(unittest.TestCase):
    def setUp(self):
        self.work = tempfile.TemporaryDirectory()
        self.addCleanup(self.work.cleanup)
        root = Path(self.work.name)
        self.archive = root / "gateway.tar.zst"
        self.archive.write_bytes(b"this-is-a-test-only-image-archive")
        self.metadata = root / "gateway.json"
        self.payload = {
            "schema_version": 1,
            "source_sha": COMMIT,
            "image_tag": TAG,
            "config_digest": CONFIG,
            "archive_sha256": image_artifact.sha256(self.archive),
            "archive_size_bytes": self.archive.stat().st_size,
        }
        self.metadata.write_text(json.dumps(self.payload), encoding="utf8")

    def test_valid_metadata_is_accepted(self):
        self.assertEqual(
            image_artifact.inspect_metadata(self.metadata, COMMIT, TAG),
            self.payload)

    def test_rejects_different_source_sha(self):
        with self.assertRaisesRegex(ValueError, "mismatch"):
            image_artifact.inspect_metadata(self.metadata, "c" * 40, TAG)

    def test_rejects_different_image(self):
        with self.assertRaisesRegex(ValueError, "mismatch"):
            image_artifact.inspect_metadata(
                self.metadata, COMMIT,
                TAG.replace("gateway", "user"))

    def test_rejects_invalid_config_digest(self):
        self.payload["config_digest"] = CONFIG[:20]
        self.metadata.write_text(json.dumps(self.payload), encoding="utf8")
        with self.assertRaisesRegex(ValueError, "config digest"):
            image_artifact.inspect_metadata(self.metadata, COMMIT, TAG)

    def test_detects_byte_change(self):
        self.archive.write_bytes(b"tampered")
        self.assertNotEqual(
            self.payload["archive_sha256"], image_artifact.sha256(self.archive))

    @patch.object(image_artifact.subprocess, "run")
    def test_docker_config_digest_is_validated(self, run):
        run.return_value.stdout = CONFIG + "\n"
        self.assertEqual(image_artifact.image_id(TAG), CONFIG)
        run.return_value.stdout = "invalid\n"
        with self.assertRaisesRegex(ValueError, "config"):
            image_artifact.image_id(TAG)


if __name__ == "__main__":
    unittest.main()

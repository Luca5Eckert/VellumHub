"""Offline negative tests for immutable six-digest release sets."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

module_path = Path(__file__).with_name("release_manifest.py")
spec = importlib.util.spec_from_file_location("release_manifest", module_path)
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)

SHA = "a" * 40
DIGEST = "sha256:" + "b" * 64


class ReleaseSetTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        for service in release.SERVICES:
            name = f"vellumhub-{service}"
            folder = self.root / f"release-image-reference-{name}"
            folder.mkdir()
            (folder / "image-reference.txt").write_text(
                f"ghcr.io/luca5eckert/{name}@{DIGEST}\n")
            (folder / f"{name}.json").write_text(json.dumps({
                "source_sha": SHA,
                "image_tag": f"ghcr.io/luca5eckert/{name}:{SHA}",
                "config_digest": "sha256:" + "c" * 64
            }))
            (folder / f"{name}.cdx.json").write_text(json.dumps({
                "bomFormat": "CycloneDX", "specVersion": "1.6"}))

    def test_valid_six_services(self):
        data = release.assemble(self.root, SHA)
        self.assertEqual(data["status"], "COMPLETE")
        self.assertEqual(set(data["services"]), set(release.SERVICES))

    def test_missing_one_service_is_rejected(self):
        (self.root / "release-image-reference-vellumhub-auth" /
         "image-reference.txt").unlink()
        with self.assertRaisesRegex(ValueError, "Incomplete"):
            release.assemble(self.root, SHA)

    def test_mixed_source_commit_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "Wrong SHA"):
            release.assemble(self.root, "d" * 40)

    def test_wrong_service_reference_is_rejected(self):
        target = self.root / "release-image-reference-vellumhub-gateway" / "image-reference.txt"
        target.write_text(f"ghcr.io/luca5eckert/vellumhub-auth@{DIGEST}\n")
        with self.assertRaisesRegex(ValueError, "mismatched"):
            release.assemble(self.root, SHA)

    def test_invalid_registry_digest_is_rejected(self):
        target = self.root / "release-image-reference-vellumhub-user" / "image-reference.txt"
        target.write_text("ghcr.io/luca5eckert/vellumhub-user@sha256:not-a-digest\n")
        with self.assertRaisesRegex(ValueError, "digest"):
            release.assemble(self.root, SHA)

    def test_missing_sbom_is_rejected(self):
        (self.root / "release-image-reference-vellumhub-user" /
         "vellumhub-user.cdx.json").unlink()
        with self.assertRaisesRegex(ValueError, "Incomplete"):
            release.assemble(self.root, SHA)

    def test_invalid_sbom_is_rejected(self):
        target = self.root / "release-image-reference-vellumhub-auth" / "vellumhub-auth.cdx.json"
        target.write_text('{"bomFormat":"invalid"}')
        with self.assertRaisesRegex(ValueError, "CycloneDX"):
            release.assemble(self.root, SHA)


if __name__ == "__main__":
    unittest.main()

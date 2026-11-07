import importlib.util
import json
from pathlib import Path
import unittest
from unittest.mock import patch

MODULE = Path(__file__).resolve().parent / "registry_gate.py"
spec = importlib.util.spec_from_file_location("registry_gate", MODULE)
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)


class RegistryGateTest(unittest.TestCase):
    def test_manifest_and_subject_names_are_canonical(self):
        manifest = gate.read_manifest()
        self.assertEqual(manifest["subjects"][0]["compatibility"], "BACKWARD_TRANSITIVE")
        gate.forbid_changed_history(None)

    def test_history_is_detected_as_immutable(self):
        class Result:
            stdout = "integration-tests/schema-evolution-poc/src/test/resources/history/book-v1.avsc\n"
        with patch.object(gate.subprocess, "run", return_value=Result()):
            with self.assertRaises(gate.RegistryError):
                gate.forbid_changed_history("origin/main")

    def test_new_historical_fixture_is_allowed_when_existing_history_is_unchanged(self):
        # The git diff filter intentionally excludes A (added files). A new
        # versioned fixture may be introduced; existing fixtures cannot drift.
        class Result:
            stdout = ""
        with patch.object(gate.subprocess, "run", return_value=Result()) as runner:
            gate.forbid_changed_history("origin/main")
            self.assertIn("--diff-filter=DMRT", runner.call_args.args[0])

    def test_publish_is_not_allowed_without_approval(self):
        class Args:
            approved = False
            ephemeral = False
            url = "https://schema.example.test"
        with self.assertRaises(gate.RegistryError):
            gate.publish(Args(), gate.read_manifest())

    def test_ephemeral_cannot_modify_remote_registry(self):
        class Args:
            approved = False
            ephemeral = True
            url = "https://schema.example.test"
        with self.assertRaises(gate.RegistryError):
            gate.publish(Args(), gate.read_manifest())

    def test_production_requires_token_and_https(self):
        class Args:
            approved = True
            ephemeral = False
            url = "http://localhost:18081"
        with patch.dict(gate.os.environ, {
            "SCHEMA_REGISTRY_PUBLISH_APPROVED": "true",
            "SCHEMA_REGISTRY_AUTH_TOKEN": "fixture-token"
        }):
            with self.assertRaises(gate.RegistryError):
                gate.publish(Args(), gate.read_manifest())

    def test_negative_check_cannot_write_to_stable_url(self):
        class Args:
            approved = True
            url = "https://registry.example.test"
        with self.assertRaises(gate.RegistryError):
            gate.negative_checks(Args(), gate.read_manifest())

    def test_unexpected_invalid_path_rejected(self):
        with self.assertRaises(gate.RegistryError):
            gate.schema_from_file("../../etc/passwd", "com.example.Book")


if __name__ == "__main__":
    unittest.main()

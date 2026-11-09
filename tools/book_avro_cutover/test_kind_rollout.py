#!/usr/bin/env python3
"""Regression: scarce-CPU Kind rollouts must not require a surge replica."""
import importlib.util
import json
from pathlib import Path
import unittest
from unittest.mock import call, patch


SOURCE = Path(__file__).with_name("kind_rehearsal.py")
spec = importlib.util.spec_from_file_location("book_avro_kind_rehearsal", SOURCE)
rehearsal = importlib.util.module_from_spec(spec)
spec.loader.exec_module(rehearsal)


class KindRolloutTest(unittest.TestCase):
    def test_all_book_services_use_no_surge_only_during_rehearsal(self):
        for service in ("recommendation-service", "engagement-service", "catalog-service"):
            with self.subTest(service=service), patch.object(rehearsal, "k") as kubectl:
                rehearsal.stage(service, BOOK_AVRO_CONSUMER_ENABLED="true")
                self.assertEqual(kubectl.call_count, 3)
                patch_args = kubectl.call_args_list[0].args
                self.assertEqual(patch_args[:5], ("patch", "deployment", service, "--type=merge", "-p"))
                self.assertEqual(
                    json.loads(patch_args[5]),
                    {"spec": {"strategy": {"type": "RollingUpdate",
                                           "rollingUpdate": {"maxSurge": 0, "maxUnavailable": 1}}}},
                )
                self.assertEqual(
                    kubectl.call_args_list[1],
                    call("set", "env", f"deployment/{service}", "BOOK_AVRO_CONSUMER_ENABLED=true"),
                )
                self.assertEqual(
                    kubectl.call_args_list[2],
                    call("rollout", "status", f"deployment/{service}", "--timeout=240s", timeout=255),
                )

    def test_other_deployments_are_not_reconfigured(self):
        with patch.object(rehearsal, "k") as kubectl:
            with self.assertRaises(ValueError):
                rehearsal.stage("auth-service", BOOK_AVRO_CONSUMER_ENABLED="true")
            kubectl.assert_not_called()


if __name__ == "__main__":
    unittest.main()

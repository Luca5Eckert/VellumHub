"""Fail-closed operator security for independent DLT replay (no broker needed)."""
import importlib.util
import os
from pathlib import Path
import unittest
from unittest.mock import patch


SPEC = importlib.util.spec_from_file_location(
    "sec14_kafka_recovery", Path(__file__).resolve().parents[3] / "scripts" / "kafka-recovery.py"
)
module = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(module)


class ReplayOperatorSecurityTest(unittest.TestCase):
    def test_missing_protocol_rejected(self):
        with patch.dict(os.environ, {}, clear=True):
            with self.assertRaisesRegex(RuntimeError, "KAFKA_RECOVERY_SECURITY_PROTOCOL"):
                module.recovery_broker_config()

    def test_broker_admin_cannot_replay(self):
        with patch.dict(os.environ, {
            "KAFKA_RECOVERY_SECURITY_PROTOCOL": "SASL_SSL",
            "KAFKA_RECOVERY_USERNAME": "kafka_admin",
            "KAFKA_RECOVERY_PASSWORD": "placeholder"}, clear=True):
            with self.assertRaisesRegex(RuntimeError, "dedicated operator"):
                module.recovery_broker_config()

    def test_insecure_transport_requires_explicit_opt_in(self):
        with patch.dict(os.environ, {
            "KAFKA_RECOVERY_SECURITY_PROTOCOL": "SASL_PLAINTEXT",
            "KAFKA_RECOVERY_USERNAME": "replay_operator",
            "KAFKA_RECOVERY_PASSWORD": "placeholder"}, clear=True):
            with self.assertRaisesRegex(RuntimeError, "disposable-local opt-in"):
                module.recovery_broker_config()

    def test_explicit_tls_operator_credentials(self):
        with patch.dict(os.environ, {
            "KAFKA_RECOVERY_SECURITY_PROTOCOL": "SASL_SSL",
            "KAFKA_RECOVERY_USERNAME": "replay_operator",
            "KAFKA_RECOVERY_PASSWORD": "placeholder",
            "KAFKA_RECOVERY_SSL_CA_LOCATION": "/etc/ssl/certs/ca.pem"}, clear=True):
            cfg = module.recovery_broker_config()
            self.assertEqual(cfg["sasl.username"], "replay_operator")
            self.assertEqual(cfg["security.protocol"], "SASL_SSL")
            self.assertEqual(cfg["ssl.ca.location"], "/etc/ssl/certs/ca.pem")


if __name__ == "__main__":
    unittest.main()

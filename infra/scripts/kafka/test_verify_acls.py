"""SEC-14 smoke assertions must reject outages, not merely return codes."""
import unittest
from unittest.mock import patch
from subprocess import CompletedProcess
import importlib.util
from pathlib import Path

SPEC = importlib.util.spec_from_file_location("sec14_verify_acls",
                                               Path(__file__).with_name("verify-acls.py"))
verify_acls = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(verify_acls)


class KafkaSmokeAssertionsTest(unittest.TestCase):
    @patch.object(verify_acls, "client", return_value="/tmp/disposable.properties")
    @patch.object(verify_acls, "run", return_value=CompletedProcess([], 1, "", "connection refused"))
    def test_producer_network_error_is_not_acl_denial(self, *_):
        with self.assertRaisesRegex(AssertionError, "did not prove ACL denial"):
            verify_acls.publish("user_service", "book-events", permitted=False)

    @patch.object(verify_acls, "client", return_value="/tmp/disposable.properties")
    @patch.object(verify_acls, "run", return_value=CompletedProcess([], 1, "", "TopicAuthorizationException"))
    def test_producer_explicit_acl_denial_is_accepted(self, *_):
        verify_acls.publish("user_service", "book-events", permitted=False)

    @patch.object(verify_acls, "client", return_value="/tmp/disposable.properties")
    @patch.object(verify_acls, "run", return_value=CompletedProcess([], 0, "", ""))
    def test_producer_success_is_not_acl_denial(self, *_):
        with self.assertRaisesRegex(AssertionError, "did not prove ACL denial"):
            verify_acls.publish("user_service", "book-events", permitted=False)

    @patch.object(verify_acls, "client", return_value="/tmp/disposable.properties")
    @patch.object(verify_acls, "run", return_value=CompletedProcess([], 1, "", "timed out"))
    def test_consumer_timeout_is_not_acl_denial(self, *_):
        with self.assertRaisesRegex(AssertionError, "did not prove denial"):
            verify_acls.consume("user_service", "book-events", "engagement-service", permitted=False)


if __name__ == "__main__":
    unittest.main()

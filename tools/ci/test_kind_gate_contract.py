"""No Docker/Kind required: expected failure never silently becomes success."""
import importlib.util
from pathlib import Path
import unittest

SOURCE = Path(__file__).resolve().parents[2] / "deploy/kubernetes/scripts/kind_gate_contract.py"
spec = importlib.util.spec_from_file_location("kind_gate_contract", SOURCE)
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)


class KindGateContractTests(unittest.TestCase):
    def test_three_business_scenarios_and_original_oauth_marker_required(self):
        logs = gate.OAUTH_MARKER + "\n" + gate.JOURNEY_MARKER
        result = gate.inspect_business_job({"succeeded": 1}, logs)
        self.assertEqual(result["status"], "PASSED")
        self.assertEqual(len(result["business_scenarios"]), 3)

    def test_zero_exit_without_second_business_scenario_is_failure(self):
        with self.assertRaisesRegex(RuntimeError, "required scenario"):
            gate.inspect_business_job({"succeeded": 1}, gate.OAUTH_MARKER)

    def test_zero_exit_without_original_oauth_is_failure(self):
        with self.assertRaisesRegex(RuntimeError, "required scenario"):
            gate.inspect_business_job({"succeeded": 1}, gate.JOURNEY_MARKER)

    def test_regression_must_be_detected_when_gateway_is_broken(self):
        logs = "AssertionError: Gateway->User did not accept token HTTP 502"
        result = gate.inspect_business_job(
            {"failed": 1}, logs, expect_gateway_failure=True)
        self.assertEqual(result["status"], "EXPECTED_REGRESSION_DETECTED")

    def test_unexpected_failure_is_not_accepted_as_expected_regression(self):
        with self.assertRaisesRegex(RuntimeError, "Unexpected"):
            gate.inspect_business_job({"failed": 1}, "timed out", expect_gateway_failure=True)

    def test_regression_success_is_a_test_failure(self):
        with self.assertRaisesRegex(RuntimeError, "broken Gateway passed"):
            gate.inspect_business_job(
                {"succeeded": 1}, gate.OAUTH_MARKER + gate.JOURNEY_MARKER,
                expect_gateway_failure=True)

    def test_running_job_returns_no_conclusion(self):
        self.assertIsNone(gate.inspect_business_job({}, ""))


if __name__ == "__main__":
    unittest.main()

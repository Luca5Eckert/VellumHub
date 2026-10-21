"""Negative and positive fixtures for AFF-03 evidence: missing evidence cannot become PASS."""
import json
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

from generate_evidence import collect_junit, evaluate, load_catalog, validate_benchmark


class FitnessEvidenceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.rules = {r["id"]: r for r in load_catalog(Path(__file__).with_name("fitness-catalog.json"))}

    def test_catalog_has_stable_unique_ids_and_reproducible_command(self):
        self.assertEqual(set(self.rules), {
            "AFF-STR-001", "AFF-STR-002", "AFF-BOUND-003", "AFF-DATA-004",
            "AFF-EVT-005", "AFF-EVT-006", "AFF-EVT-007", "AFF-CON-008",
            "AFF-REC-009", "AFF-OPS-010", "AFF-RES-011"
        })
        self.assertTrue(all(rule["command"] for rule in self.rules.values()))

    def test_missing_tests_are_not_green(self):
        for rule in self.rules.values():
            self.assertEqual(evaluate(rule, [], None, None, "abc")["result"], "NOT_RUN")

    def test_failure_and_skipped_tests_cannot_be_green(self):
        rule = self.rules["AFF-REC-009"]
        for outcome in ("FAIL", "SKIPPED"):
            record = [{"suite": "example.DistributedRecommendationBenchmarkIT",
                       "test": "read", "status": outcome, "failure": "injected"}]
            self.assertEqual(evaluate(rule, record, None, None, "abc")["result"], outcome)

    def test_nonapplicable_gateway_is_recorded_without_inflating_coverage(self):
        rule = self.rules["AFF-STR-001"]
        samples = [
            {"suite": "com." + service + ".architecture." + cls + "ArchitectureFitnessTest",
             "test": "domainMustNotDependOnAdaptersOrControllers",
             "status": "PASS", "failure": None}
            for service, cls in (
                ("catalog_service", "Catalog"),
                ("engagement_service", "Engagement"),
                ("user_service", "User"),
                ("recommendation_service", "Recommendation"),
            )
        ]
        gateway = {"suite": "com.gateway_service.architecture.GatewayArchitectureFitnessTest",
                   "test": "domainMustNotDependOnAdaptersOrControllers",
                   "status": "SKIPPED", "failure": None}
        false_positive = {"suite": "com.recommendation_service.architecture."
                                   "RecommendationReadAutonomyArchitectureFitnessTest",
                          "test": "readAutonomy", "status": "PASS", "failure": None}
        report = evaluate(rule, samples + [gateway, false_positive], None, None, "abc")
        self.assertEqual(report["result"], "PASS")
        self.assertEqual(len(report["passedSuites"]), 4)
        self.assertEqual(len(report["skippedCases"]), 1)
        self.assertEqual(evaluate(rule, samples[:3] + [gateway, false_positive],
                                  None, None, "abc")["result"], "SKIPPED")

    def test_junit_parsing_preserves_test_outcomes(self):
        with TemporaryDirectory() as temp:
            folder = Path(temp) / "services" / "service" / "target" / "surefire-reports"
            folder.mkdir(parents=True)
            (folder / "TEST-Fixture.xml").write_text(
                '<testsuite><testcase classname="sample.DistributedRecommendationBenchmarkIT" name="read">'
                '<failure message="unexpected fan-out"/></testcase></testsuite>', encoding="utf-8"
            )
            cases = collect_junit(Path(temp))
            self.assertEqual(len(cases), 1)
            self.assertEqual(cases[0]["status"], "FAIL")

    def test_benchmark_rejects_fanout_wrong_sha_and_missing_projection(self):
        with TemporaryDirectory() as temp:
            root = Path(temp)
            meta = {"commitSha": "abc", "benchmarkProfile": "smoke"}
            raw = {
                "scenarioResults": [{"submittedEvents": 1, "projectedEvents": 1,
                                     "failedEvents": 0, "unexpectedDltEvents": 0}],
                "readAutonomy": {"requestedReads": 1, "successfulReads": 1,
                                 "successRate": 1, "outboundHttpAttempts": 0},
                "readMeasurements": [{"httpStatus": 200, "resultCount": 1, "failure": None}],
                "eventMeasurements": [{"projected": True}],
            }
            (root / "run-metadata.json").write_text(json.dumps(meta))
            (root / "raw-results.json").write_text(json.dumps(raw))
            (root / "recommendation-freshness.json").write_text(json.dumps({
                "commitSha": "abc", "profile": "smoke",
                "scenarioResults": [{"submittedEvents": 1, "observedRecommendations": 1, "failedEvents": 0}],
                "measurements": [{"recommendationReflected": True}],
            }))
            self.assertTrue(validate_benchmark("AFF-REC-009", root, "abc")[0])
            self.assertTrue(validate_benchmark("AFF-OPS-010", root, "abc")[0])
            self.assertFalse(validate_benchmark("AFF-REC-009", root, "wrong-sha")[0])
            raw["readAutonomy"]["outboundHttpAttempts"] = 1
            (root / "raw-results.json").write_text(json.dumps(raw))
            self.assertFalse(validate_benchmark("AFF-REC-009", root, "abc")[0])
            raw["readAutonomy"]["outboundHttpAttempts"] = 0
            raw["scenarioResults"][0]["projectedEvents"] = 0
            (root / "raw-results.json").write_text(json.dumps(raw))
            self.assertFalse(validate_benchmark("AFF-REC-009", root, "abc")[0])
            raw["scenarioResults"][0]["projectedEvents"] = 1
            (root / "raw-results.json").write_text(json.dumps(raw))
            fresh = json.loads((root / "recommendation-freshness.json").read_text())
            fresh["scenarioResults"][0]["observedRecommendations"] = 0
            (root / "recommendation-freshness.json").write_text(json.dumps(fresh))
            self.assertFalse(validate_benchmark("AFF-OPS-010", root, "abc")[0])


if __name__ == "__main__":
    unittest.main()

import json
import tempfile
import unittest
from pathlib import Path
from tools.metrics.analyze_outcomes import analyze, compare_rankers


class OutcomeAnalysisTests(unittest.TestCase):
    def test_paired_ranker_deltas(self):
        rows = [
            {"userId": "u1", "ranker": ranker, "metrics": {"ndcgAt10": value}}
            for ranker, value in (
                ("POPULARITY_ONLY", .1),
                ("SEMANTIC_POPULARITY", .5),
                ("SEMANTIC_ONLY", .8)
            )
        ]
        r = compare_rankers(rows, "SEMANTIC_POPULARITY", "POPULARITY_ONLY")
        self.assertEqual((r["wins"], r["ties"], r["losses"]), (1, 0, 0))
        with self.assertRaisesRegex(ValueError, "incomplete"):
            compare_rankers(rows[:1], "SEMANTIC_POPULARITY", "POPULARITY_ONLY")

    def test_missing_events_and_sha_mismatch_are_fail_closed(self):
        with tempfile.TemporaryDirectory() as directory:
            d = Path(directory) / "distributed"
            q = Path(directory) / "quality"
            d.mkdir()
            q.mkdir()
            (d / "run-metadata.json").write_text(json.dumps({
                "commitSha": "a", "benchmarkProfile": "reference"
            }))
            (q / "run-metadata.json").write_text(json.dumps({
                "commitSha": "a", "datasetVersion": "fixture"
            }))
            (d / "raw-results.json").write_text(json.dumps({
                "eventMeasurements": [],
                "scenarioResults": [{"scenario": "burst", "submittedEvents": 1}],
                "readAutonomy": {}
            }))
            (d / "recommendation-freshness.json").write_text(json.dumps({
                "commitSha": "a", "scenarioResults": [{"scenario": "burst"}]
            }))
            (q / "raw-results.json").write_text(json.dumps({"perUser": []}))
            with self.assertRaisesRegex(ValueError, "SHA mismatch"):
                analyze(d, q, "b")
            with self.assertRaisesRegex(ValueError, "raw event measurements"):
                analyze(d, q, "a")


if __name__ == "__main__":
    unittest.main()

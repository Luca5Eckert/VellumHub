"""AFF-RES-011: guard the flow-specific degradation inventory and negative claims."""
import json
from pathlib import Path
import unittest


POLICY_FILE = Path(__file__).with_name("resilience-policies.json")
ROOT = Path(__file__).resolve().parents[2]
REPLAY_MODES = {
    "not_applicable", "outbox_retry", "review_and_resume_source_offset",
    "manual_reconciliation_only"
}


def validate(doc, root=ROOT):
    if doc.get("schemaVersion") != 1:
        raise ValueError("Unknown resilience policy version")
    ids = set()
    for policy in doc.get("policies", []):
        if policy["id"] in ids:
            raise ValueError("Duplicate policy: " + policy["id"])
        ids.add(policy["id"])
        if not all(policy.get(key) for key in (
                "flow", "fault", "policy", "unavailableData", "verification", "testPath")):
            raise ValueError("Incomplete degradation flow " + policy["id"])
        if policy["replay"] not in REPLAY_MODES:
            raise ValueError("Unsupported replay claim for " + policy["id"])
        if not (root / policy["testPath"]).is_file():
            raise ValueError("Source/test path absent for " + policy["id"])
    if len(ids) < 7:
        raise ValueError("Missing canonical, legacy or local read flow coverage")
    return ids


class ResiliencePolicyInventoryTest(unittest.TestCase):
    def setUp(self):
        self.doc = json.loads(POLICY_FILE.read_text(encoding="utf-8"))

    def test_inventory_references_existing_implementations(self):
        self.assertEqual(len(validate(self.doc)), 7)

    def test_missing_behavior_or_made_up_replay_fails(self):
        doc = json.loads(json.dumps(self.doc))
        doc["policies"][0]["policy"] = ""
        with self.assertRaises(ValueError):
            validate(doc)
        doc = json.loads(json.dumps(self.doc))
        doc["policies"][-1]["replay"] = "universal_exactly_once"
        with self.assertRaises(ValueError):
            validate(doc)

    def test_replay_is_explicitly_bounded(self):
        legacy = next(x for x in self.doc["policies"] if x["id"] == "RES-LEGACY-DLT")
        self.assertEqual(legacy["replay"], "manual_reconciliation_only")
        ordered = [x for x in self.doc["policies"] if x["id"].startswith("RES-ORDERED-")]
        self.assertEqual(len(ordered), 2)
        self.assertTrue(all(x["replay"] == "review_and_resume_source_offset" for x in ordered))


if __name__ == "__main__":
    unittest.main()

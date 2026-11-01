"""SEC-14 static invariants for the broker authorization inventory."""
import json
from pathlib import Path
import unittest

POLICY = Path(__file__).resolve().parents[2] / "kafka" / "acl-policy.json"


class KafkaAclPolicyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.policy = json.loads(POLICY.read_text(encoding="utf-8"))

    def test_all_service_principals_are_distinct_and_explicit(self):
        self.assertEqual(set(self.policy), {
            "catalog_service", "user_service",
            "engagement_service", "recommendation_service"
        })
        for user, rights in self.policy.items():
            self.assertTrue(user.endswith("_service"))
            for key in ("publish", "subscribe", "legacy_retry", "ordered_dlt", "groups"):
                self.assertIn(key, rights)
                self.assertEqual(len(rights[key]), len(set(rights[key])))
                for value in rights[key]:
                    self.assertTrue(value)
                    self.assertNotIn("*", value)
                    self.assertNotIn("jwt", value.lower())
                    self.assertNotIn("token", value.lower())

    def test_recovery_is_limited_to_subscription_sources(self):
        for rights in self.policy.values():
            self.assertTrue(set(rights["legacy_retry"]).issubset(rights["subscribe"]))
            self.assertTrue(set(rights["ordered_dlt"]).issubset(rights["subscribe"]))

    def test_user_and_catalog_cannot_subscribe_to_personalized_events(self):
        for user in ("user_service", "catalog_service"):
            self.assertFalse(self.policy[user]["subscribe"])
            self.assertFalse(self.policy[user]["groups"])
        self.assertNotIn("engagement.ratings.v1", self.policy["user_service"]["publish"])
        self.assertIn("engagement.ratings.v1", self.policy["recommendation_service"]["subscribe"])
        self.assertNotIn("engagement.ratings.v1", self.policy["recommendation_service"]["publish"])

    def test_canonical_dlt_families_are_service_scoped(self):
        for user in ("engagement_service", "recommendation_service"):
            rights = self.policy[user]
            self.assertIn("book-events", rights["ordered_dlt"])
            self.assertIn("reading-progress-events", rights["ordered_dlt"])
            self.assertTrue(rights["groups"])

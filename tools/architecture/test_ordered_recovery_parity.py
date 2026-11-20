"""AFF-RES-011 parity: the ordered Kafka recovery copies must not drift.

Engagement and Recommendation each own a copy of the ordered-consumer quarantine
(`share/kafka/recovery`). The copies are kept per service on purpose for now: each image
builds independently and the DLT producer is part of the per-service Kafka ACL review
(SEC-14). The risk of that choice is a fix landing in only one service, so this check
fails when the copies differ in anything other than the service package and the
`spring.application.name` fallback.
"""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
SERVICES = {
    "engagement": ("services/engagement-service", "engagement_service"),
    "recommendation": ("services/recommendation-service", "recommendation_service"),
}
FILES = (
    "src/main/java/com/vellumhub/{pkg}/share/kafka/recovery/OrderedKafkaRecovery.java",
    "src/main/java/com/vellumhub/{pkg}/share/kafka/recovery/OrderedRecoveryPublisherConfig.java",
    "src/test/java/com/vellumhub/{pkg}/share/kafka/recovery/OrderedKafkaRecoveryTest.java",
)


def normalize(source: str, service: str, package: str) -> str:
    text = source.replace("\r\n", "\n")
    text = text.replace(f"com.vellumhub.{package}.", "com.vellumhub.<service>.")
    return re.sub(rf"\b{service}-service\b", "<service>-service", text)


def drifted_files(root: Path = ROOT) -> list[str]:
    drift = []
    (first, (first_dir, first_pkg)), (second, (second_dir, second_pkg)) = SERVICES.items()
    for template in FILES:
        left = root / first_dir / template.format(pkg=first_pkg)
        right = root / second_dir / template.format(pkg=second_pkg)
        if not left.is_file() or not right.is_file():
            drift.append(f"missing copy: {template}")
            continue
        if normalize(left.read_text(encoding="utf-8"), first, first_pkg) != normalize(
                right.read_text(encoding="utf-8"), second, second_pkg):
            drift.append(Path(template).name)
    return drift


class OrderedRecoveryParityTest(unittest.TestCase):
    def test_service_copies_are_identical_after_normalization(self):
        self.assertEqual(drifted_files(), [],
                         "Ordered Kafka recovery diverged between Engagement and Recommendation; "
                         "apply the change to both services or extract a shared module.")

    def test_negative_fixture_detects_one_sided_change(self):
        source = "package com.vellumhub.engagement_service.share;\nclass A { int retries = 3; }\n"
        changed = source.replace("engagement_service", "recommendation_service").replace("3", "4")
        self.assertNotEqual(normalize(source, "engagement", "engagement_service"),
                            normalize(changed, "recommendation", "recommendation_service"))

    def test_normalization_ignores_only_service_identity(self):
        source = 'package com.vellumhub.engagement_service.x;\n@Value("${a:engagement-service}")\n'
        other = source.replace("engagement_service", "recommendation_service").replace(
            "engagement-service", "recommendation-service")
        self.assertEqual(normalize(source, "engagement", "engagement_service"),
                         normalize(other, "recommendation", "recommendation_service"))


if __name__ == "__main__":
    unittest.main()

"""Static regression contracts for local Compose artifact ownership.

These checks deliberately use only stdlib: the PR verification job runs them
before Maven/Testcontainers. Actual Compose syntax is also validated in CI.
"""

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]


class ComposeInfrastructureContractTest(unittest.TestCase):
    def test_postgres_uses_official_images_without_noop_custom_builds(self):
        compose = (ROOT / "docker-compose.yml").read_text(encoding="utf-8")
        self.assertNotIn("build: ./infra/docker/postgres-", compose)
        for service, image in (
            ("postgres-catalog", "postgres:15-alpine"),
            ("postgres-user", "postgres:15-alpine"),
            ("postgres-recommendation", "pgvector/pgvector:pg15"),
        ):
            self.assertRegex(
                compose,
                f"  {service}:\n    image: {image}\n",
            )
        self.assertFalse(list((ROOT / "infra" / "docker").glob("postgres-*/Dockerfile")))
        self.assertFalse(list((ROOT / "infra" / "docker").glob("postgres-*/init.sql")))

    def test_registry_has_isolated_compose_project_and_network(self):
        base = (ROOT / "docker-compose.yml").read_text(encoding="utf-8")
        registry = (ROOT / "docker-compose.schema-registry.yml").read_text(encoding="utf-8")
        self.assertIn("name: vellum-hub", base)
        self.assertIn("name: vellumhub-schema-registry", registry)
        self.assertIn("name: vellumhub-schema-registry-net", registry)
        self.assertNotIn("name: vellum-hub_vellum-network", registry)


if __name__ == "__main__":
    unittest.main()

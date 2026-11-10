"""Infrastructure topology and documentation regression guards."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]

class InfrastructureLayoutContractTest(unittest.TestCase):
    def test_no_fixed_container_names_and_stable_docker_service_labels(self):
        compose = (ROOT / "docker-compose.yml").read_text(encoding="utf-8")
        alloy = (ROOT / "infra/observability/alloy/config.alloy").read_text(encoding="utf-8")
        self.assertNotIn("container_name:", compose)
        self.assertIn("__meta_docker_container_label_com_docker_compose_service", alloy)
        self.assertNotIn("__meta_docker_container_name", alloy)
        self.assertIn("image: redis:7.4-alpine", compose)

    def test_pg_kubernetes_resources_are_grouped_without_losing_statefulsets(self):
        infra = (ROOT / "deploy/kubernetes/overlays/local/infrastructure.yaml").read_text(encoding="utf-8")
        databases = (ROOT / "deploy/kubernetes/overlays/local/databases.yaml").read_text(encoding="utf-8")
        self.assertNotIn("postgres", infra)
        for database in ("user", "catalog", "engagement", "recommendation", "auth"):
            self.assertIn(f"name: {database}-postgres", databases)
        self.assertIn("image: pgvector/pgvector:pg15", databases)
        self.assertIn("image: redis:7.4-alpine", infra)

    def test_single_root_maven_wrapper_preserves_service_builds(self):
        for name in ("mvnw", "mvnw.cmd", ".mvn/wrapper/maven-wrapper.properties"):
            self.assertTrue((ROOT / name).exists(), name)
        for service in ("user", "catalog", "engagement", "recommendation", "gateway"):
            path = ROOT / f"services/{service}-service"
            self.assertFalse((path / "mvnw").exists())
            self.assertFalse((path / "mvnw.cmd").exists())
            self.assertFalse((path / ".mvn/wrapper/maven-wrapper.properties").exists())
            self.assertIn(f"-pl services/{service}-service -am", (path / "README.md").read_text(encoding="utf-8"))

if __name__ == "__main__":
    unittest.main()

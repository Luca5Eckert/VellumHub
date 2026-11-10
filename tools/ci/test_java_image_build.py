"""Guardrails for the shared Java image build and independent service images."""

from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
EXPECTED_STAGES = {
    "gateway-service": "deps-jwt",
    "auth-service": "deps-jwt",
    "user-service": "deps-kafka",
    "catalog-service": "deps-avro",
    "engagement-service": "deps-avro",
    "recommendation-service": "deps-avro",
}


class JavaImageBuildContractTest(unittest.TestCase):
    def test_shared_dockerfile_and_service_specific_build_args(self):
        dockerfile = ROOT / "infra/docker/java/Dockerfile"
        self.assertTrue(dockerfile.is_file())
        content = dockerfile.read_text(encoding="utf-8")
        for required in ("FROM ${DEPS_STAGE} AS build", "FROM ${RUNTIME_IMAGE} AS runtime",
                         "USER vellumhub", "libstdc++6", "opentelemetry-javaagent.jar"):
            self.assertIn(required, content)
        compose = (ROOT / "docker-compose.yml").read_text(encoding="utf-8")
        workflow = (ROOT / ".github/workflows/verify-images.yml").read_text(encoding="utf-8")
        for name, stage in EXPECTED_STAGES.items():
            self.assertFalse((ROOT / f"services/{name}/Dockerfile").exists())
            self.assertIn(f"SERVICE: {name}", compose)
            self.assertIn(f"DEPS_STAGE: {stage}", compose)
            self.assertIn(f"service: {name}", workflow)
            self.assertIn(f"dep_stage: {stage}", workflow)
        self.assertIn("RUNTIME_IMAGE: eclipse-temurin:21-jre", compose)
        self.assertIn("APP_PORT: \"9000\"", compose)
        self.assertIn("HEALTH_PORT: \"9001\"", compose)
        self.assertIn("build-args: |", workflow)
        self.assertIn("SERVICE=${{ matrix.service }}", workflow)
        self.assertIn("DEPS_STAGE=${{ matrix.dep_stage }}", workflow)
        self.assertIn("RUNTIME_IMAGE=${{ matrix.runtime_image }}", workflow)

    def test_standalone_auth_poc_and_oidc_overlay(self):
        poc = (ROOT / "docker-compose.auth-poc.yml").read_text(encoding="utf-8")
        oidc = (ROOT / "docker-compose.oidc-lote2.yml").read_text(encoding="utf-8")
        self.assertIn("dockerfile: infra/docker/java/Dockerfile", poc)
        self.assertNotIn("dockerfile:", oidc)
        self.assertNotIn("services/auth-service/Dockerfile", poc + oidc)


if __name__ == "__main__":
    unittest.main()

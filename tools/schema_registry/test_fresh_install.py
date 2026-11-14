"""Static and pure-function regression checks for the explicitly selected fresh Avro installation."""
import json
from pathlib import Path
import sys
import unittest
import yaml

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/schema_registry"))
from bootstrap_fresh import REQUIRED, REGISTRY_URL, BootstrapError, verify_config


class FreshInstallTest(unittest.TestCase):
    def read_yaml(self, rel):
        return yaml.safe_load((ROOT / rel).read_text(encoding="utf-8"))

    def test_compose_overlay_matches_all_routes_and_has_no_json_writers(self):
        compose = self.read_yaml("infra/compose/avro-fresh.yaml")
        config = {"services": {name: {"environment": spec["environment"]}
                               for name, spec in compose["services"].items()}}
        self.assertTrue(verify_config(config))
        self.assertEqual(set(config["services"]), set(REQUIRED))
        for name, svc in config["services"].items():
            env = svc["environment"]
            for key, val in env.items():
                if key.endswith("WIRE_FORMAT"):
                    self.assertEqual("avro", val)
                if key.endswith("REGISTRY_URL"):
                    self.assertEqual(REGISTRY_URL, val)
        env = config["services"]["recommendation-service"]["environment"]
        self.assertEqual("true", env["KAFKA_LEGACY_CONSUMERS_ENABLED"])

    def test_composed_configuration_rejects_missing_or_json_route(self):
        current = {"services": {name: {"environment": dict(values)} for name, values in REQUIRED.items()}}
        self.assertTrue(verify_config(current))
        current["services"]["catalog-service"]["environment"]["BOOK_EVENTS_WIRE_FORMAT"] = "json"
        with self.assertRaises(BootstrapError):
            verify_config(current)

    def test_registry_is_still_separate_and_bridge_is_explicit(self):
        base = self.read_yaml("infra/compose/schema-registry.yaml")
        overlay = self.read_yaml("infra/compose/schema-registry-app-network.yaml")
        self.assertEqual("vellumhub-schema-registry", base["name"])
        self.assertEqual(["vellum-network"], base["services"]["schema-registry"]["networks"])
        self.assertNotIn("vellum-app-net", base["networks"])
        self.assertEqual("vellum-hub_vellum-network", overlay["networks"]["vellum-app-net"]["name"])
        self.assertTrue(overlay["networks"]["vellum-app-net"]["external"])
        self.assertEqual(["vellum-network", "vellum-app-net"],
                         overlay["services"]["schema-registry"]["networks"])

    def test_kind_overlay_matches_compose_without_changing_existing_local(self):
        overlay = self.read_yaml("deploy/kubernetes/overlays/local-avro-fresh/kustomization.yaml")
        self.assertIn("../local", overlay["resources"])
        self.assertNotIn("../../schema-registry/overlays/local", overlay["resources"])
        for service in REQUIRED:
            patch = self.read_yaml(f"deploy/kubernetes/overlays/local-avro-fresh/{service.split('-')[0]}.yaml")
            self.assertEqual(service, patch["metadata"]["name"])
            containers = patch["spec"]["template"]["spec"]["containers"]
            self.assertEqual(service, containers[0]["name"])
            env = {e["name"]: e["value"] for e in containers[0]["env"]}
            for key, value in REQUIRED[service].items():
                self.assertEqual(value, env[key], (service, key))
        self.assertNotIn("schema-registry", self.read_yaml(
            "deploy/kubernetes/overlays/local/kustomization.yaml")["resources"])


if __name__ == "__main__":
    unittest.main()

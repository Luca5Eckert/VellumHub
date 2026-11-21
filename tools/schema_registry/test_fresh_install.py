"""Static and pure-function regression checks for the explicitly selected fresh Avro installation."""
import json
from pathlib import Path
import sys
import unittest
import yaml

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/schema_registry"))
from bootstrap_fresh import REQUIRED, REGISTRY_URL, AVRO_TOPICS, BootstrapError, verify_config


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
        self.assertEqual("false", env["KAFKA_LEGACY_CONSUMERS_ENABLED"])
        for name in ("engagement-service", "recommendation-service"):
            self.assertEqual("false", config["services"][name]["environment"]["KAFKA_JSON_LISTENERS_ENABLED"])

    def test_local_bootstrap_uses_only_manifest_approved_avro_topics(self):
        subjects = self.read_yaml("infra/schema-registry/contracts.json")["subjects"]
        self.assertEqual(sorted({item["topic"] for item in subjects}), AVRO_TOPICS)
        self.assertIn("user-preference-events.avro", AVRO_TOPICS)
        self.assertTrue(all(topic.endswith(".avro") for topic in AVRO_TOPICS))

    def test_composed_configuration_rejects_missing_or_json_route(self):
        current = {"services": {name: {"environment": dict(values)} for name, values in REQUIRED.items()}}
        self.assertTrue(verify_config(current))
        current["services"]["catalog-service"]["environment"]["BOOK_EVENTS_WIRE_FORMAT"] = "json"
        with self.assertRaises(BootstrapError):
            verify_config(current)
        current["services"]["catalog-service"]["environment"]["BOOK_EVENTS_WIRE_FORMAT"] = "avro"
        current["services"]["engagement-service"]["environment"]["KAFKA_JSON_LISTENERS_ENABLED"] = "true"
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
        self.assertIn("../../base", overlay["resources"])
        self.assertNotIn("../../schema-registry/overlays/local", overlay["resources"])
        for filename in ("infrastructure.yaml", "databases.yaml", "redis-recommendation-cache.yaml"):
            self.assertIn(filename, overlay["resources"])
            self.assertEqual(
                (ROOT / "deploy/kubernetes/overlays/local" / filename).read_text(),
                (ROOT / "deploy/kubernetes/overlays/local-avro-fresh" / filename).read_text(),
                f"Shared Kind infrastructure drift: {filename}",
            )
        source_patches = (ROOT / "deploy/kubernetes/overlays/local/local-patches.yaml").read_text()
        local_patches = (ROOT / "deploy/kubernetes/overlays/local-avro-fresh/local-patches.yaml").read_text()
        self.assertEqual(source_patches[:source_patches.index("apiVersion: networking.k8s.io/v1")].rstrip().removesuffix("---").rstrip(),
                         local_patches.rstrip())
        self.assertIn("local-allow.yaml", overlay["resources"])
        stable = self.read_yaml("deploy/kubernetes/overlays/local/kustomization.yaml")
        self.assertEqual(stable["images"], overlay["images"])
        for service in REQUIRED:
            patch = self.read_yaml(f"deploy/kubernetes/overlays/local-avro-fresh/{service.split('-')[0]}.json")
            env = {e["value"]["name"]: e["value"]["value"] for e in patch}
            self.assertTrue(all(e["op"] == "add" and e["path"] == "/spec/template/spec/containers/0/env/-" for e in patch))
            for key, value in REQUIRED[service].items():
                self.assertEqual(value, env[key], (service, key))
        self.assertNotIn("schema-registry", self.read_yaml(
            "deploy/kubernetes/overlays/local/kustomization.yaml")["resources"])


if __name__ == "__main__":
    unittest.main()

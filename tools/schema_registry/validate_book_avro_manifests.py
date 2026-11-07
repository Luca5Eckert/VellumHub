#!/usr/bin/env python3
"""Static, fail-closed checks for opt-in Book Avro Kustomize overlays."""
import sys
import yaml

def read(path):
    with open(path, encoding="utf-8") as stream:
        return {(x["kind"], x["metadata"]["name"]): x
                for x in yaml.safe_load_all(stream) if x}

if len(sys.argv) != 3:
    raise SystemExit("Usage: validate_book_avro_manifests.py LOCAL.yaml PROD.yaml")
local, prod = map(read, sys.argv[1:])
for resources, tier in ((local, "local"), (prod, "prod")):
    for service in ("catalog-service", "recommendation-service", "engagement-service"):
        app = resources[("Deployment", service)]["spec"]["template"]["spec"]["containers"][0]
        env = {e["name"]: e for e in app["env"]}
        assert ("BOOK_AVRO_PRODUCER_ENABLED" if service == "catalog-service"
                else "BOOK_AVRO_CONSUMER_ENABLED") in env
        assert env["BOOK_AVRO_PRODUCER_ENABLED" if service == "catalog-service"
                   else "BOOK_AVRO_CONSUMER_ENABLED"]["value"] == "false"
        assert "BOOK_AVRO_REGISTRY_URL" in env
        if tier == "prod":
            assert "secretKeyRef" in env["BOOK_AVRO_REGISTRY_URL"]["valueFrom"]
        else:
            assert env["BOOK_AVRO_REGISTRY_URL"]["value"] == "http://schema-registry:8080/apis/ccompat/v7"
    assert resources[("Deployment", "catalog-service")]["spec"]["template"]["spec"]["containers"][0]["env"]
# Registry is deliberately installed via its own Kustomization rather than
# being silently coupled to every application rollout.
assert ("Deployment", "schema-registry") not in local
assert ("Deployment", "schema-registry") not in prod
print("Opt-in Book Avro: local and prod manifests fail closed with separate Registry client route")

#!/usr/bin/env python3
"""Fail-closed structural checks of rendered Kustomize YAML; not runtime proof."""
import sys
import yaml

if len(sys.argv) != 3:
    raise SystemExit("Usage: validate_manifests.py LOCAL_RENDER PROD_RENDER")

def objects(path):
    with open(path, encoding="utf-8") as f:
        return {(x["kind"], x["metadata"]["name"]): x for x in yaml.safe_load_all(f) if x}

local = objects(sys.argv[1])
prod = objects(sys.argv[2])
for target in (local, prod):
    svc = target[("Service", "schema-registry")]
    dep = target[("Deployment", "schema-registry")]
    assert svc["spec"]["type"] == "ClusterIP"
    assert dep["metadata"]["namespace"] == "vellumhub"
    assert "Ingress" not in {kind for kind, _ in target}
    app = dep["spec"]["template"]["spec"]["containers"][0]
    assert app["image"] == "apicurio/apicurio-registry:3.1.7"
    assert all(app[k]["httpGet"]["port"] == "http"
               for k in ("startupProbe", "readinessProbe", "livenessProbe"))
    assert app["startupProbe"]["httpGet"]["path"] == "/health/ready"
    assert app["readinessProbe"]["httpGet"]["path"] == "/health/ready"
    assert app["livenessProbe"]["httpGet"]["path"] == "/health/live"
    env = {e["name"]: e for e in app["env"]}
    assert env["APICURIO_STORAGE_KIND"]["value"] == "sql"
    assert env["APICURIO_DATASOURCE_URL"]["valueFrom"]["secretKeyRef"]["name"] == "schema-registry-db"
    assert ("NetworkPolicy", "schema-registry-internal-only") in target

def env(target):
    return {e["name"]: e for e in
            target[("Deployment", "schema-registry")]["spec"]["template"]["spec"]["containers"][0]["env"]}

assert env(local)["QUARKUS_OIDC_TENANT_ENABLED"]["value"] == "false"
assert env(prod)["QUARKUS_OIDC_TENANT_ENABLED"]["value"] == "true"
assert "QUARKUS_OIDC_AUTH_SERVER_URL" in env(prod)
assert env(prod)["APICURIO_AUTH_ROLE_BASED_AUTHORIZATION"]["value"] == "true"
assert ("StatefulSet", "registry-postgres") in local
assert ("StatefulSet", "registry-postgres") not in prod
print("Kustomize isolation, secrets, OIDC and probes: PASS")

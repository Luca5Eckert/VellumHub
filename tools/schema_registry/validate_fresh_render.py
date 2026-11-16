#!/usr/bin/env python3
"""Check the ACTUAL rendered Kind overlay; configuration checks are not runtime tests."""
from pathlib import Path
import sys
import yaml
from bootstrap_fresh import REQUIRED

if len(sys.argv) != 2:
    raise SystemExit("Usage: validate_fresh_render.py RENDERED_YAML")
manifest = yaml.safe_load_all(Path(sys.argv[1]).read_text(encoding="utf-8"))
objects = {(doc["kind"], doc["metadata"]["name"]): doc for doc in manifest if doc}
for service, expected in REQUIRED.items():
    deployment = objects[("Deployment", service)]
    containers = deployment["spec"]["template"]["spec"]["containers"]
    container = next(item for item in containers if item["name"] == service)
    actual = {item["name"]: str(item["value"]) for item in container["env"]
              if "value" in item}
    for key, value in expected.items():
        if actual.get(key, "").lower() != value.lower():
            raise SystemExit(f"Rendered {service}: {key} should be {value}")
assert ("Service", "schema-registry") not in objects, "Registry must be bootstrapped separately"
print("Rendered fresh Kind application routes: PASS (Registry is installed and verified separately)")

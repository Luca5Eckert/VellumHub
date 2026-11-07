#!/usr/bin/env python3
"""Structural gate for opt-in prepared -> consumers -> producer release stages."""
import sys
import yaml
assert len(sys.argv) == 4
def load(path):
    with open(path, encoding="utf-8") as source:
        docs = [d for d in yaml.safe_load_all(source) if d]
    entries = {(d["kind"], d["metadata"]["name"]): d for d in docs}
    assert ("Deployment","schema-registry") in entries
    assert ("StatefulSet","registry-postgres") in entries
    assert entries[("Service","schema-registry")]["spec"]["type"] == "ClusterIP"
    states = {}
    for name in ("catalog-service","engagement-service","recommendation-service"):
        e = entries[("Deployment",name)]["spec"]["template"]["spec"]["containers"][0]["env"]
        env = {item["name"]: item.get("value") for item in e}
        assert env["BOOK_AVRO_REGISTRY_URL"] == "http://schema-registry:8080/apis/ccompat/v7"
        states[name] = env
    return states
prepared, consumers, producer = [load(path) for path in sys.argv[1:]]
assert prepared["catalog-service"]["BOOK_EVENTS_WIRE_FORMAT"] == "json"
assert consumers["catalog-service"]["BOOK_EVENTS_WIRE_FORMAT"] == "json"
assert producer["catalog-service"]["BOOK_EVENTS_WIRE_FORMAT"] == "avro"
for service in ("recommendation-service","engagement-service"):
    assert prepared[service]["BOOK_AVRO_CONSUMER_ENABLED"] == "false"
    assert consumers[service]["BOOK_AVRO_CONSUMER_ENABLED"] == "true"
    assert producer[service]["BOOK_AVRO_CONSUMER_ENABLED"] == "true"
print("Kustomize staged release: PASS")

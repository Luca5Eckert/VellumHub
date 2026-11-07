#!/usr/bin/env python3
import subprocess, sys, yaml
assert len(sys.argv)==3
def docs(path):
    with open(path,encoding="utf-8") as file:
        return {(d["kind"],d["metadata"]["name"]):d for d in yaml.safe_load_all(file) if d}
main,registry=docs(sys.argv[1]),docs(sys.argv[2])
for name in ("catalog-service","recommendation-service","engagement-service"):
    assert ("Deployment",name) in main
assert ("StatefulSet","registry-postgres") in registry
assert ("Deployment","schema-registry") in registry
assert registry[("Service","schema-registry")]["spec"]["type"]=="ClusterIP"
def cmds(stage):
    s=subprocess.check_output([sys.executable,"tools/book_avro_cutover/k8s_stage.py",stage],text=True)
    assert all(x.startswith("kubectl -n vellumhub set env deployment/") for x in s.splitlines())
    return s
prepared,consumers,producer,rollback=(cmds(s) for s in ("prepare","consumers","producer","rollback"))
assert "BOOK_EVENTS_WIRE_FORMAT=json" in prepared
assert prepared.count("BOOK_AVRO_CONSUMER_ENABLED=false")==2
assert consumers.count("BOOK_AVRO_CONSUMER_ENABLED=true")==2
assert "BOOK_EVENTS_WIRE_FORMAT=avro" in producer
assert "BOOK_EVENTS_WIRE_FORMAT=json" in rollback
assert "BOOK_AVRO_CONSUMER_ENABLED" not in producer
print("Existing Kind stack, Registry and staged commands: PASS")

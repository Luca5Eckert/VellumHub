#!/usr/bin/env python3
"""Contract-first Registry policy gate. No application topics, consumers or Outbox are modified."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
MANIFEST = ROOT / "infra/schema-registry/contracts.json"
HISTORY = "integration-tests/schema-evolution-poc/src/test/resources/history/"


class RegistryError(RuntimeError):
    pass


def read_manifest():
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    if manifest.get("format") != 1 or not manifest.get("subjects"):
        raise RegistryError("Invalid contract manifest")
    for item in manifest["subjects"]:
        if item.get("compatibility") != "BACKWARD_TRANSITIVE":
            raise RegistryError("Every subject must enforce BACKWARD_TRANSITIVE")
        if item.get("topic", "").strip() != item["topic"] or not item["topic"]:
            raise RegistryError("Missing topic")
        if item["topic"].startswith("book-events.schema-poc"):
            raise RegistryError("A disposable PoC topic is not a release subject")
        expected = item["topic"] + "-" + item["record"]
        if item.get("subject", expected) != expected:
            raise RegistryError("Subject must follow TopicRecordNameStrategy: " + expected)
        if len(item["versions"]) < 2:
            raise RegistryError("Need a historical writer and current writer")
        for version in item["versions"]:
            schema = schema_from_file(version["file"], item["record"])
            if schema.get("type") != "record":
                raise RegistryError("Only Avro record schemas are supported")
        for candidate in ("positive", "negative"):
            schema_from_file(item[candidate], item["record"])
    return manifest


def schema_from_file(rel, record):
    p = (ROOT / rel).resolve()
    if not p.is_relative_to(ROOT) or not p.is_file() or p.suffix != ".avsc":
        raise RegistryError("Unapproved Avro schema path: " + str(rel))
    schema = json.loads(p.read_text(encoding="utf-8"))
    if schema.get("namespace", "") + "." + schema.get("name", "") != record:
        raise RegistryError("Record identity changed: " + rel)
    return schema


def forbid_changed_history(base):
    if not base:
        return
    proc = subprocess.run(
        ["git", "diff", "--name-only", "--diff-filter=DMRT", base + "...HEAD", "--", HISTORY],
        cwd=ROOT, text=True, capture_output=True, check=True,
    )
    if proc.stdout.strip():
        raise RegistryError("Existing historical fixtures may not be changed, removed or renamed: " + proc.stdout.strip())


def request(url, method="GET", body=None, token=None):
    headers = {"Accept": "application/vnd.schemaregistry.v1+json"}
    if body is not None:
        headers["Content-Type"] = "application/vnd.schemaregistry.v1+json"
    if token:
        headers["Authorization"] = "Bearer " + token
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(url, method=method, data=data, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=20) as reply:
            raw = reply.read()
            return reply.status, json.loads(raw.decode("utf-8")) if raw else {}
    except urllib.error.HTTPError as exc:
        raw = exc.read().decode("utf-8", errors="replace")
        try:
            result = json.loads(raw)
        except ValueError:
            result = {"error_code": exc.code}
        return exc.code, result
    except (urllib.error.URLError, TimeoutError) as exc:
        raise RegistryError("Registry unavailable: " + type(exc).__name__) from exc


def root_url(args):
    url = args.url.rstrip("/")
    if not url.startswith(("http://", "https://")):
        raise RegistryError("Registry URL must be HTTP(S)")
    return url + "/apis/ccompat/v7"


def api(args, method, path, body=None):
    token = os.environ.get("SCHEMA_REGISTRY_AUTH_TOKEN") if args.approved else None
    return request(root_url(args) + path, method=method, body=body, token=token)


def assert_status(actual, expected, operation, data):
    if actual != expected:
        # Do not include server response bodies: could contain sensitive data.
        raise RegistryError(operation + " returned HTTP " + str(actual) + "; expected " + str(expected))


def publish(args, manifest):
    if args.approved:
        if not os.getenv("SCHEMA_REGISTRY_PUBLISH_APPROVED") == "true" or not os.getenv("SCHEMA_REGISTRY_AUTH_TOKEN"):
            raise RegistryError("Stable Registry publication requires approved job and scoped token")
        if not args.url.startswith("https://"):
            raise RegistryError("Approved publication requires HTTPS")
    elif args.ephemeral:
        host = urllib.parse.urlparse(args.url).hostname
        if host not in ("127.0.0.1", "localhost", "::1"):
            raise RegistryError("Ephemeral registration is restricted to loopback")
    else:
        raise RegistryError("Explicit --ephemeral or --approved required to publish")
    ids = {}
    for item in manifest["subjects"]:
        subject = item["topic"] + "-" + item["record"]
        path = urllib.parse.quote(subject, safe="")
        status, _ = api(args, "PUT", "/config/" + path,
                        {"compatibility": item["compatibility"]})
        assert_status(status, 200, "Set subject compatibility", None)
        ids[subject] = []
        for version in item["versions"]:
            schema = schema_from_file(version["file"], item["record"])
            status, result = api(args, "POST", "/subjects/" + path + "/versions",
                                 {"schemaType": "AVRO", "schema": json.dumps(schema, separators=(",", ":"))})
            assert_status(status, 200, "Register schema", None)
            if not isinstance(result.get("id"), int) or result["id"] <= 0:
                raise RegistryError("Registry returned invalid schema ID")
            ids[subject].append(result["id"])
    return ids


def verify(args, manifest, original=None):
    ids = {}
    for item in manifest["subjects"]:
        subject = item["topic"] + "-" + item["record"]
        path = urllib.parse.quote(subject, safe="")
        status, config = api(args, "GET", "/config/" + path)
        assert_status(status, 200, "Read effective policy", None)
        if config.get("compatibility", config.get("compatibilityLevel")) != "BACKWARD_TRANSITIVE":
            raise RegistryError("Subject compatibility drift: " + subject)
        status, versions = api(args, "GET", "/subjects/" + path + "/versions")
        assert_status(status, 200, "List historical versions", None)
        if len(versions) != len(item["versions"]):
            raise RegistryError("Unexpected schema version count: " + subject)
        ids[subject] = []
        for index, version in enumerate(item["versions"]):
            status, result = api(args, "GET", "/subjects/" + path + "/versions/" + str(index + 1))
            assert_status(status, 200, "Read registered version", None)
            schema = schema_from_file(version["file"], item["record"])
            if json.loads(result["schema"]) != schema:
                raise RegistryError("Registered schema changed: " + subject)
            sid = result.get("id")
            if not isinstance(sid, int) or sid <= 0:
                raise RegistryError("Invalid schema ID in Registry")
            ids[subject].append(sid)
            status, byid = api(args, "GET", "/schemas/ids/" + str(sid))
            assert_status(status, 200, "Resolve schema by immutable ID", None)
            if json.loads(byid["schema"]) != schema:
                raise RegistryError("Schema ID resolves to different content")
        if original and ids[subject] != original.get(subject):
            raise RegistryError("Persistent ID mismatch after restart: " + subject)
    return ids


def negative_checks(args, manifest):
    # Deliberately incompatible candidates are only safe in disposable labs.
    # Never send a negative registration attempt to a stable Registry.
    if args.approved or urllib.parse.urlparse(args.url).hostname not in ("localhost", "127.0.0.1", "::1"):
        raise RegistryError("Negative schema registration is restricted to local disposable registries")
    for item in manifest["subjects"]:
        subject = item["topic"] + "-" + item["record"]
        path = urllib.parse.quote(subject, safe="")
        status, before = api(args, "GET", "/subjects/" + path + "/versions")
        assert_status(status, 200, "Read existing versions", None)
        incompatible = schema_from_file(item["negative"], item["record"])
        status, _ = api(args, "POST", "/subjects/" + path + "/versions",
                        {"schemaType": "AVRO", "schema": json.dumps(incompatible, separators=(",", ":"))})
        assert_status(status, 409, "Reject incompatible historical evolution", None)
        status, after = api(args, "GET", "/subjects/" + path + "/versions")
        assert_status(status, 200, "Confirm no version added", None)
        if before != after:
            raise RegistryError("Incompatible schema modified registry history")
        compatible = schema_from_file(item["positive"], item["record"])
        status, result = api(args, "POST", "/compatibility/subjects/" + path + "/versions/latest",
                             {"schemaType": "AVRO", "schema": json.dumps(compatible, separators=(",", ":"))})
        assert_status(status, 200, "Check compatible future evolution", None)
        if result.get("is_compatible") is not True:
            raise RegistryError("Compatible candidate rejected")


def main():
    p = argparse.ArgumentParser()
    p.add_argument("action", choices=["check", "publish", "verify", "negative"])
    p.add_argument("--url", default="http://127.0.0.1:18081")
    p.add_argument("--base")
    p.add_argument("--ephemeral", action="store_true")
    p.add_argument("--approved", action="store_true")
    p.add_argument("--state", default="")
    a = p.parse_args()
    try:
        m = read_manifest()
        forbid_changed_history(a.base)
        if a.action == "check":
            print("Manifest, Avro record identity and immutable historical fixtures: PASS")
            return
        if a.action == "publish":
            result = publish(a, m)
            if a.state:
                Path(a.state).write_text(json.dumps(result, sort_keys=True) + "\n", encoding="utf-8")
        elif a.action == "verify":
            previous = json.loads(Path(a.state).read_text(encoding="utf-8")) if a.state else None
            verify(a, m, previous)
        else:
            negative_checks(a, m)
        print(a.action + ": PASS (subject policy and schema IDs checked; no payload logged)")
    except (RegistryError, KeyError, ValueError, OSError, subprocess.CalledProcessError) as exc:
        print("Schema Registry gate failed: " + str(exc), file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()

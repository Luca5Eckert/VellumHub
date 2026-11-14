#!/usr/bin/env python3
"""Fail-closed local Compose bootstrap for a BRAND-NEW Avro installation.

This script cannot migrate existing JSON Outbox rows. Never use it to cut over
a populated deployment. The standalone Registry stays in its own Compose
project and is connected to the app network by an explicit local-only overlay.
"""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[2]
MAIN = ["docker", "compose", "--project-directory", ".", "-f", "compose.yaml"]
APP = MAIN + ["-f", "infra/compose/avro-fresh.yaml"]
REGISTRY = [
    "docker", "compose", "--project-directory", ".",
    "-f", "infra/compose/schema-registry.yaml",
    "-f", "infra/compose/schema-registry-app-network.yaml",
]
REGISTRY_URL = "http://schema-registry:8080/apis/ccompat/v7"
# Contract manifest is the source of truth for the four dedicated Avro topics.
AVRO_TOPICS = sorted({entry["topic"] for entry in json.loads(
    (ROOT / "infra/schema-registry/contracts.json").read_text(encoding="utf-8")
)["subjects"]})
REQUIRED = {
    "catalog-service": {
        "BOOK_EVENTS_WIRE_FORMAT": "avro",
        "READING_PROGRESS_EVENTS_WIRE_FORMAT": "avro",
        "BOOK_AVRO_REGISTRY_URL": REGISTRY_URL,
        "READING_PROGRESS_AVRO_REGISTRY_URL": REGISTRY_URL,
    },
    "engagement-service": {
        "BOOK_AVRO_CONSUMER_ENABLED": "true",
        "READING_PROGRESS_AVRO_CONSUMER_ENABLED": "true",
        "ENGAGEMENT_RATINGS_WIRE_FORMAT": "avro",
        "ENGAGEMENT_REACTIONS_WIRE_FORMAT": "avro",
        "BOOK_AVRO_REGISTRY_URL": REGISTRY_URL,
        "READING_PROGRESS_AVRO_REGISTRY_URL": REGISTRY_URL,
        "ENGAGEMENT_AVRO_REGISTRY_URL": REGISTRY_URL,
    },
    "recommendation-service": {
        "BOOK_AVRO_CONSUMER_ENABLED": "true",
        "READING_PROGRESS_AVRO_CONSUMER_ENABLED": "true",
        "ENGAGEMENT_RATINGS_AVRO_CONSUMER_ENABLED": "true",
        "ENGAGEMENT_REACTIONS_AVRO_CONSUMER_ENABLED": "true",
        "BOOK_AVRO_REGISTRY_URL": REGISTRY_URL,
        "READING_PROGRESS_AVRO_REGISTRY_URL": REGISTRY_URL,
        "ENGAGEMENT_AVRO_REGISTRY_URL": REGISTRY_URL,
        "KAFKA_LEGACY_CONSUMERS_ENABLED": "true",
    },
}


class BootstrapError(RuntimeError):
    pass


def run(args, *, capture=False):
    result = subprocess.run(args, cwd=ROOT, check=True, text=True,
                            capture_output=capture)
    return result.stdout if capture else None


def verify_config(config):
    services = config["services"]
    for service, expected in REQUIRED.items():
        env = services[service]["environment"]
        for key, desired in expected.items():
            if str(env.get(key, "")).lower() != desired.lower():
                raise BootstrapError(f"{service}: {key} is not {desired}")
    return True


def load_env():
    path = ROOT / ".env"
    if not path.is_file():
        raise BootstrapError("Missing .env: copy .env.example and set strong local secrets")
    result = dict(os.environ)
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, value = line.split("=", 1)
        result.setdefault(k.strip(), value.strip().strip("'\""))
    keys = ("POSTGRES_CATALOG_PASSWORD", "POSTGRES_ENGAGEMENT_PASSWORD",
            "POSTGRES_USER_PASSWORD", "POSTGRES_RECOMMENDATION_PASSWORD",
            "POSTGRES_AUTH_PASSWORD", "VELLUM_INTERNAL_PROVISION_KEY",
            "REGISTRY_DB_PASSWORD")
    for key in keys:
        value = result.get(key, "")
        if len(value) < 16 or value.lower().startswith(("change_me", "replace-")):
            raise BootstrapError(f"Set a non-placeholder {key} (at least 16 characters)")
    key_dir = Path(result.get("VELLUM_AUTH_KEY_DIR", ""))
    if not key_dir.is_absolute():
        key_dir = ROOT / key_dir
    for filename in ("signing-private.pem", "signing-public.pem"):
        if not (key_dir / filename).is_file():
            raise BootstrapError(f"Missing Auth signing file: {filename}")
    return result


def preflight_empty():
    volumes = run(["docker", "volume", "ls", "--format", "{{.Name}}"], capture=True).splitlines()
    owned = [v for v in volumes if v.startswith(("vellum-hub_", "vellumhub-schema-registry_"))]
    if owned:
        raise BootstrapError("Existing VellumHub persistent Docker volumes detected. "
                             "This command is ONLY for a clean install; refusing cutover. "
                             "Use --resume only after checking this is the same new install.")
    for cmd in (MAIN, REGISTRY):
        existing = run(cmd + ["ps", "-aq"], capture=True).strip()
        if existing:
            raise BootstrapError("Existing VellumHub containers found. "
                                 "Do not use the fresh-install command to migrate them.")


def main():
    p = argparse.ArgumentParser(description=__doc__)
    mode = p.add_mutually_exclusive_group(required=True)
    mode.add_argument("--check", action="store_true", help="Validate merged Compose Avro wiring (no deployment)")
    mode.add_argument("--new-installation", action="store_true", help="Start on empty volumes only")
    mode.add_argument("--resume", action="store_true", help="Resume an interrupted brand-new installation")
    args = p.parse_args()
    try:
        # Full Compose interpolation must succeed before any infrastructure is mutated.
        config = json.loads(run(APP + ["config", "--format", "json"], capture=True))
        verify_config(config)
        run(REGISTRY + ["config", "--quiet"])
        run([sys.executable, "tools/schema_registry/registry_gate.py", "check"])
        if args.check:
            print("Fresh Avro Compose configuration: PASS (no containers started)")
            return
        load_env()
        if args.new_installation:
            preflight_empty()
        # Creating the application network does not start application producers.
        run(MAIN + ["up", "-d", "--wait", "kafka"])
        # With the network overlay, Registry remains attached after restart/recreate.
        run(REGISTRY + ["up", "-d", "--wait"])
        run([sys.executable, "tools/schema_registry/wait_ready.py",
             "http://127.0.0.1:18081/apis/ccompat/v7/subjects"])
        # No dependency on Kafka auto.create.topics.enable or a first producer send.
        # This local single-broker stack uses replication factor one by design.
        for topic in AVRO_TOPICS:
            command = MAIN + ["exec", "-T", "kafka", "kafka-topics",
                              "--bootstrap-server", "kafka:29092"]
            run(command + ["--create", "--if-not-exists", "--topic", topic,
                           "--partitions", "3", "--replication-factor", "1"])
            run(command + ["--describe", "--topic", topic])
        with tempfile.TemporaryDirectory(prefix="vellum-schema-") as temp:
            state = str(Path(temp) / "registry-ids.json")
            run([sys.executable, "tools/schema_registry/registry_gate.py", "publish",
                 "--ephemeral", "--state", state])
            run([sys.executable, "tools/schema_registry/registry_gate.py", "verify",
                 "--state", state])
            # Only now permit the application processes to start producing Avro.
            run(APP + ["up", "-d", "--build", "--wait"])
            run([sys.executable, "tools/schema_registry/registry_gate.py", "verify",
                 "--state", state])
        print("Fresh Avro Compose bootstrap: PASS. Registry subjects/IDs verified; "
              "application healthchecks reported ready. No business E2E implied.")
    except (BootstrapError, KeyError, ValueError, OSError,
            subprocess.CalledProcessError) as exc:
        print("Fresh Avro bootstrap failed: " + str(exc), file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()

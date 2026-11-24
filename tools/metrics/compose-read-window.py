#!/usr/bin/env python3
"""Measure authenticated read autonomy and API recovery in the disposable audit stack."""
from http.client import HTTPException
import json
import math
import os
from pathlib import Path
import runpy
import subprocess
import sys
import time
from urllib.error import URLError


UPSTREAM = ("user-service", "catalog-service", "engagement-service")
BOOK = "00000000-0000-4000-8000-000000000091"
READ = "/api/v1/recommendations?limit=10&offset=0"


def compose(*args, **options):
    return subprocess.run(["docker", "compose", *args], check=True, **options)


def endpoint_addresses():
    """Read Compose endpoint addresses in memory without exposing them in artifacts."""
    inspect_format = '{{index .Config.Labels "com.docker.compose.service"}} {{json .NetworkSettings.Networks}}'
    try:
        containers = compose("ps", "-q", *UPSTREAM, capture_output=True,
                             text=True).stdout.splitlines()
        if len(containers) != len(UPSTREAM):
            return {}
        entries = subprocess.run(
            ["docker", "inspect", "--format", inspect_format, *containers],
            check=True, capture_output=True, text=True,
        ).stdout.splitlines()
        addresses = {}
        for entry in entries:
            service, data = entry.split(" ", 1)
            for network, endpoint in json.loads(data).items():
                if network.endswith("_vellum-network") and endpoint["IPAddress"]:
                    addresses[service] = endpoint["IPAddress"]
        return addresses
    except (subprocess.CalledProcessError, ValueError):
        return {}


def main():
    output = Path(sys.argv[1])
    if not os.environ.get("COMPOSE_PROJECT_NAME", "").startswith("vh-audit-"):
        raise SystemExit("Run only in the disposable audit Compose project")
    compose(
        "exec", "-T", "postgres-recommendation", "sh", "-ec",
        'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"',
        input=f"""
        INSERT INTO recommendations (book_id, title, description, release_year, cover_url, author)
        VALUES ('{BOOK}', 'Audit fixture', 'Synthetic cold-start book', 2026,
                'https://example.invalid/cover', 'Audit') ON CONFLICT DO NOTHING;
        INSERT INTO book_features (book_id, embedding, popularity_score, last_updated)
        VALUES ('{BOOK}', NULL, 1, now()) ON CONFLICT DO NOTHING;
        """,
        text=True,
    )
    verifier = Path("infra/scripts/security/verify_oidc_lote2_flow.py").resolve()
    sys.path.insert(0, str(verifier.parent))
    os.environ.pop("VELLUM_VERIFY_OFFLINE", None)
    fixture = runpy.run_path(str(verifier))
    data = {"baseline": [], "upstream_stopped": [], "recovery_probes": [], "summary": {}}

    def read():
        start = time.monotonic()
        status = None
        expected_fixture = False
        try:
            with fixture["get"](READ, fixture["access"]) as response:
                status = response.status
                if status == 200:
                    payload = json.load(response)
                    expected_fixture = bool(payload and payload[0]["id"] == BOOK)
        except (URLError, TimeoutError, HTTPException, ValueError, KeyError, TypeError):
            pass
        return {
            "status": status,
            "expected_fixture": expected_fixture,
            "elapsed_ms": (time.monotonic() - start) * 1000,
        }

    def sample_window(window):
        started = time.monotonic()
        rows = data[window]
        for _ in range(30):
            rows.append(read())
        duration = time.monotonic() - started
        successful = sum(row["status"] == 200 and row["expected_fixture"] for row in rows)
        latencies = sorted(row["elapsed_ms"] for row in rows)
        data["summary"][window] = {
            "requests": len(rows),
            "successful": successful,
            "error_rate": 1 - successful / len(rows),
            "duration_seconds": duration,
            "requests_per_second": len(rows) / duration,
            **{f"p{q}_ms": latencies[math.ceil(len(rows) * q / 100) - 1] for q in (50, 95, 99)},
        }

    before = endpoint_addresses()
    try:
        for _ in range(3):
            read()
        sample_window("baseline")
        try:
            compose("stop", *UPSTREAM)
            running = set(compose("ps", "--status", "running", "--services",
                                  capture_output=True, text=True).stdout.splitlines())
            data["upstream_confirmed_stopped"] = not running.intersection(UPSTREAM)
            sample_window("upstream_stopped")
        finally:
            started = time.monotonic()
            compose("start", *UPSTREAM)
            statuses = []
            while time.monotonic() - started < 180:
                statuses = []
                for path in ("/api/v1/users/me", "/api/v1/catalog/books",
                             "/api/v1/engagement/rating/me?pageNumber=0&pageSize=10"):
                    try:
                        with fixture["get"](path, fixture["access"]) as response:
                            statuses.append(response.status)
                    except (URLError, TimeoutError, HTTPException):
                        statuses.append(None)
                data["recovery_probes"].append({
                    "elapsed_seconds": time.monotonic() - started, "statuses": statuses,
                })
                if statuses == [200, 200, 200]:
                    break
                time.sleep(1)
            if statuses == [200, 200, 200]:
                data["after_recovery"] = read()
    finally:
        if data["recovery_probes"] and before:
            previous_owner = {address: service for service, address in before.items()}
            data["restarted_endpoint_previous_owner"] = {
                service: previous_owner.get(address)
                for service, address in endpoint_addresses().items()
            }
        output.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")

    success = all(
        len(data[window]) == 30 and all(row["status"] == 200 and row["expected_fixture"]
                                       for row in data[window])
        for window in ("baseline", "upstream_stopped")
    )
    recovered = data.get("after_recovery", {})
    return 0 if (success and data["upstream_confirmed_stopped"]
                 and recovered.get("status") == 200 and recovered.get("expected_fixture")) else 1


if __name__ == "__main__":
    sys.exit(main())

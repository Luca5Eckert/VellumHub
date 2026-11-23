#!/usr/bin/env python3
"""Verify the local Compose observability profile without printing telemetry payloads."""

import argparse
import base64
from http.client import HTTPException
import json
import os
from pathlib import Path
import secrets
import sys
import time
from urllib.error import HTTPError
from urllib.parse import urlencode
from urllib.request import ProxyHandler, Request, build_opener


SERVICES = (
    "auth-service", "gateway-service", "user-service", "catalog-service",
    "engagement-service", "recommendation-service",
)
ROOT = Path(__file__).resolve().parents[3]
OPENER = build_opener(ProxyHandler({}))


class CheckFailed(Exception):
    pass


def get(port, path, *, query=None, headers=None, expected=200):
    url = f"http://127.0.0.1:{port}{path}"
    if query:
        url += "?" + urlencode(query)
    try:
        with OPENER.open(Request(url, headers=headers or {}), timeout=3) as response:
            status, body = response.status, response.read()
    except HTTPError as error:
        with error:
            status = error.code
        body = b""
    if status != expected:
        raise CheckFailed(f"{port}{path} returned HTTP {status}, expected {expected}")
    return body


def read_json(port, path, **options):
    return json.loads(get(port, path, **options))


def verify_metrics(services):
    targets = read_json(9090, "/api/v1/targets")["data"]["activeTargets"]
    healthy = {
        target["labels"]["job"] for target in targets
        if target["health"] == "up" and target["scrapeUrl"].endswith("/actuator/prometheus")
    }
    missing = sorted(set(services) - healthy)
    if missing:
        raise CheckFailed("scrapes not UP: " + ", ".join(missing))


def verify_service_logs(services, lookback_minutes):
    end_ns = time.time_ns()
    start_ns = end_ns - lookback_minutes * 60 * 1_000_000_000
    missing = []
    for service in services:
        data = read_json(3100, "/loki/api/v1/query_range", query={
            "query": f'{{service="{service}"}}', "start": start_ns, "end": end_ns, "limit": 1,
        })
        if not any(stream["values"] for stream in data["data"]["result"]):
            missing.append(service)
    if missing:
        raise CheckFailed("no recent log lines: " + ", ".join(missing))


def verify_grafana():
    user = os.getenv("GRAFANA_USER", "admin")
    password = os.getenv("GRAFANA_PASSWORD", "admin")
    basic = base64.b64encode(f"{user}:{password}".encode()).decode()
    headers = {"Authorization": f"Basic {basic}"}
    sources = read_json(3002, "/api/datasources", headers=headers)
    invalid = sorted({"prometheus", "loki", "tempo"} - {source["uid"] for source in sources})
    if invalid:
        raise CheckFailed("unprovisioned data sources: " + ", ".join(invalid))
    dashboards = read_json(3002, "/api/search", query={"type": "dash-db"}, headers=headers)
    definitions = ROOT / "infra/observability/grafana/provisioning/dashboards/json"
    expected = {json.loads(path.read_text())["uid"] for path in definitions.glob("*.json")}
    invalid = sorted(expected - {dashboard["uid"] for dashboard in dashboards})
    if invalid:
        raise CheckFailed("unprovisioned dashboards: " + ", ".join(invalid))


class GatewayProbe:
    def __init__(self):
        self.trace_id = secrets.token_hex(16)
        self.parent_id = secrets.token_hex(8)
        self.baseline = None

    def counter(self):
        expression = 'sum(http_server_requests_seconds_count{job="gateway-service",status="401"})'
        result = read_json(9090, "/api/v1/query", query={"query": expression})["data"]["result"]
        return float(result[0]["value"][1]) if result else 0

    def verify(self):
        if self.baseline is None:
            baseline = self.counter()
            get(8080, "/api/v1/users/me", expected=401, headers={
                "traceparent": f"00-{self.trace_id}-{self.parent_id}-01",
            })
            self.baseline = baseline
        trace = read_json(3200, f"/api/traces/{self.trace_id}", headers={"Accept": "application/json"})
        if not trace.get("batches") and not trace.get("resourceSpans"):
            raise CheckFailed("Tempo returned no spans for the Gateway probe")
        if self.counter() <= self.baseline:
            raise CheckFailed("Prometheus has not observed the Gateway 401 counter increase")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--services", choices=SERVICES, nargs="+", default=SERVICES,
                        help="services that must have working scrapes and logs; default: all six")
    parser.add_argument("--timeout", type=int, default=120, help="polling deadline in seconds")
    parser.add_argument("--lookback-minutes", type=int, default=30, help="window for Loki logs")
    args = parser.parse_args()
    if args.timeout <= 0 or args.lookback_minutes <= 0:
        parser.error("--timeout and --lookback-minutes must be positive")

    print("Services in scope: " + ", ".join(args.services), flush=True)
    pending = {
        "Prometheus ready": lambda: get(9090, "/-/ready"),
        "Loki ready": lambda: get(3100, "/ready"),
        "Tempo ready": lambda: get(3200, "/ready"),
        "Alloy ready": lambda: get(12345, "/-/ready"),
        "Grafana ready": lambda: get(3002, "/api/health"),
        "Prometheus application scrapes": lambda: verify_metrics(args.services),
        "Grafana data sources and dashboards": verify_grafana,
        "Loki application logs": lambda: verify_service_logs(args.services, args.lookback_minutes),
    }
    if "gateway-service" in args.services:
        pending["Gateway HTTP trace and metric"] = GatewayProbe().verify

    deadline = time.monotonic() + args.timeout
    while pending:
        failed = {}
        for name, probe in list(pending.items()):
            try:
                probe()
            except (CheckFailed, OSError, HTTPException, KeyError, ValueError) as error:
                failed[name] = str(error)
            else:
                print(f"OK {name}", flush=True)
                del pending[name]
        if not pending:
            return 0
        if time.monotonic() >= deadline:
            for name, reason in failed.items():
                print(f"FAIL {name}: {reason}", file=sys.stderr)
            return 1
        time.sleep(min(3, max(0, deadline - time.monotonic())))


if __name__ == "__main__":
    sys.exit(main())

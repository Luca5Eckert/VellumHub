#!/usr/bin/env python3
"""Collect auditable M07-M10 backend evidence from *executed* local/CI runs.

Does not run tests, infer production availability or turn missing reports into zeros.
Requires an exact source commit SHA, rejects reports from other commits, and
copies no user/event payloads into the summary.
"""
import argparse
import datetime as dt
import json
import pathlib
import sys
import xml.etree.ElementTree as ET

SUITES = {
    "M08": ("ConsumerIdempotencyDistributedTest",),
    "M09": ("OAuthScopeBoundaryTest", "HumanApiAccessTest",
            "AccessTokenClaimsTest", "JwtAccessTokenTest"),
}


def read_json(path):
    if not path.is_file():
        return None
    with path.open(encoding="utf-8") as stream:
        return json.load(stream)


def report(root, rel, sha, key="commitSha"):
    source = root / rel
    data = read_json(source)
    if data is None:
        return {"status": "MISSING", "source": str(rel)}
    if data.get(key) != sha:
        return {"status": "SHA_MISMATCH", "source": str(rel),
                "reported_sha": data.get(key)}
    return {"status": "OBSERVED", "source": str(rel), "data": data}


def suite_evidence(root, names, sha):
    marker = read_json(root / "target/metrics/contract-test-run.json")
    if not marker or marker.get("commit_sha") != sha or marker.get("exit_code") != 0:
        return {"status": "UNBOUND", "limitation": "No successful SHA-bound test-run marker. Existing JUnit files cannot be attributed to this checkout."}
    started = marker["started_epoch"]
    paths = sorted(path for path in root.glob("**/target/surefire-reports/TEST-*.xml")
                   if path.stat().st_mtime >= started)
    results = []
    for path in paths:
        try:
            suite = ET.parse(path).getroot()
        except ET.ParseError as exc:
            raise ValueError(f"Malformed JUnit XML: {path}") from exc
        for element in ([suite] if suite.tag == "testsuite" else suite.findall("testsuite")):
            name = element.get("name", "")
            if not any(name.endswith("." + candidate) or name == candidate for candidate in names):
                continue
            cases = element.findall("testcase")
            failures = sum(bool(case.findall("failure") or case.findall("error")) for case in cases)
            skipped = sum(bool(case.findall("skipped")) for case in cases)
            results.append({
                "suite": name, "source": str(path.relative_to(root)),
                "executed": len(cases) - skipped, "passed": len(cases) - skipped - failures,
                "failed": failures, "skipped": skipped
            })
    return {
        "status": "OBSERVED" if results else "MISSING",
        "suites": results,
        "executed": sum(s["executed"] for s in results),
        "passed": sum(s["passed"] for s in results),
        "failed": sum(s["failed"] for s in results),
        "skipped": sum(s["skipped"] for s in results),
        "limitation": "JUnit counts do not independently establish Kafka fault coverage or number of E2E APIs."
    }


def summarize(root, sha, profile):
    directory = pathlib.Path("services/recommendation-service/target/distributed-benchmark") / profile
    quality = pathlib.Path("services/recommendation-service/target/evaluation-results/reference")
    meta = report(root, directory / "run-metadata.json", sha)
    raw = read_json(root / directory / "raw-results.json") if meta["status"] == "OBSERVED" else None
    freshness = report(root, directory / "recommendation-freshness.json", sha)
    quality_meta = report(root, quality / "run-metadata.json", sha)
    quality_raw = read_json(root / quality / "raw-results.json") if quality_meta["status"] == "OBSERVED" else None

    m07 = {"status": "MISSING", "projection": meta["status"], "freshness": freshness["status"]}
    if raw is not None and freshness["status"] == "OBSERVED":
        scenarios = raw.get("scenarioResults", [])
        fscenarios = freshness["data"].get("scenarioResults", [])
        read = raw.get("readAutonomy", {})
        if scenarios and fscenarios and read:
            m07 = {
                "status": "OBSERVED",
                "profile": profile,
                "projection_submitted": sum(s["submittedEvents"] for s in scenarios),
                "projection_converged": sum(s["projectedEvents"] for s in scenarios),
                "freshness_submitted": sum(s["submittedEvents"] for s in fscenarios),
                "freshness_observed": sum(s["observedRecommendations"] for s in fscenarios),
                "reads_requested": read.get("requestedReads"),
                "reads_successful": read.get("successfulReads"),
                "outbound_http_attempts": read.get("outboundHttpAttempts"),
                "read_p95_ms": read.get("latency", {}).get("p95Millis"),
                "freshness_p95_by_scenario_ms": {
                    s["scenario"]: s["latency"]["p95Millis"] for s in fscenarios
                },
                "sources": [str(directory / p) for p in
                            ("run-metadata.json", "raw-results.json", "recommendation-freshness.json")],
                "limitation": "Materialized read autonomy; local Testcontainers; no production SLA."
            }
    m10 = {"status": "MISSING", "metadata": quality_meta["status"]}
    if quality_raw is not None and quality_raw.get("aggregates"):
        m10 = {
            "status": "OBSERVED",
            "dataset_version": quality_raw.get("datasetVersion"),
            "cutoff": quality_raw.get("cutoff"),
            "rankers": [
                {"ranker": item["ranker"], "metrics": item["metrics"],
                 "catalog_coverage": item.get("catalogCoverage"),
                 "empty_result_rate": item.get("emptyResultRate")}
                for item in quality_raw["aggregates"]
            ],
            "sources": [str(quality / "run-metadata.json"), str(quality / "raw-results.json")],
            "limitation": "Synthetic offline quality; latency/freshness belongs to M07, not the same experiment."
        }
    return {
        "schema_version": 1, "project": "VellumHub", "commit_sha": sha,
        "captured_at_utc": dt.datetime.now(dt.timezone.utc).isoformat(),
        "environment": "local/CI (see original run metadata)",
        "M07": m07, "M08": suite_evidence(root, SUITES["M08"], sha),
        "M09": suite_evidence(root, SUITES["M09"], sha), "M10": m10,
        "M18": {"status": "NOT_RUN",
                "limitation": "Kubernetes requires deploy/kubernetes/scripts/measure-kubernetes.ps1 executed against kind."}
    }


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--root", type=pathlib.Path, default=pathlib.Path("."))
    ap.add_argument("--sha", required=True, help="Exact checkout commit SHA")
    ap.add_argument("--profile", choices=["reference", "smoke"], default="reference")
    ap.add_argument("--output", type=pathlib.Path, default=pathlib.Path("target/metrics/evidence.json"))
    args = ap.parse_args()
    result = summarize(args.root.resolve(), args.sha, args.profile)
    output = args.output if args.output.is_absolute() else args.root / args.output
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"Evidence summary: {output}")
    print(" ".join(f"{metric}={result[metric]['status']}" for metric in
                   ("M07", "M08", "M09", "M10", "M18")))
    # Missing evidence is an explicit gap, not a passing claim. --strict controls gates.
    return 0


if __name__ == "__main__":
    sys.exit(main())

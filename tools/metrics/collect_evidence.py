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

FAILURE_MATRIX = {
    "rollback": ("failedBookProjectionRollsBackBothCheckpointAndProcessedEvent",
                 "checkpointWriteFailureRollsBackRatingProfileAndCanBeRetried",
                 "failedRatingDeltaDoesNotCommitIdentityOrRevisionAndCanBeRetried"),
    "redelivery": ("bookRedeliveryDoesNotRebuildVectorAndPersistsEventIdentity",
                   "readingProgressRedeliveryCannotIncreaseUserProfileTwice",
                   "ratingRedeliveryThroughRealKafkaIsAcknowledgedWithoutAnotherProfileDelta",
                   "reactionRedeliveryPreservesProfileAndSingleCheckpoint"),
    "concurrency": ("concurrentBookRedeliverySerializesOnCheckpoint",),
    "revision_gap": ("bookRevisionGapDoesNotAdvancePostgresCheckpoint",
                     "readingProgressRevisionGapDoesNotChangeProfileOrCheckpoint",
                     "ratingRevisionGapDoesNotChangeProfileCheckpointOrProcessedIdentity",
                     "reactionRevisionGapDoesNotPersistIdentityOrAdvanceOffset"),
    "identity_collision": ("engagementIdentityCollisionDoesNotAcknowledgeSecondAggregate",
                           "reusingBookEventIdForAnotherAggregateRollsBackTheSecondProjection")
}

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
    case_outcomes = {}
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
            for case in cases:
                if case.findall("skipped"):
                    status = "skipped"
                elif case.findall("failure") or case.findall("error"):
                    status = "failed"
                else:
                    status = "passed"
                case_outcomes[case.get("name", "")] = status
            results.append({
                "suite": name, "source": str(path.relative_to(root)),
                "executed": len(cases) - skipped, "passed": len(cases) - skipped - failures,
                "failed": failures, "skipped": skipped
            })
    passed = sum(s["passed"] for s in results)
    failed = sum(s["failed"] for s in results)
    executed = sum(s["executed"] for s in results)
    return {
        "status": ("PASSED" if executed > 0 and failed == 0 else
                   "FAILED" if failed else "MISSING"),
        "suites": results,
        "case_outcomes": case_outcomes,
        "executed": executed,
        "passed": passed,
        "failed": failed,
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
    m08 = suite_evidence(root, SUITES["M08"], sha)
    if m08["status"] in ("PASSED", "FAILED"):
        m08["failure_matrix"] = {
            category: {
                "expected_methods": list(methods),
                "passed_methods": [method for method in methods
                                  if m08["case_outcomes"].get(method) == "passed"],
                "missing_or_failed_methods": [method for method in methods
                                              if m08["case_outcomes"].get(method) != "passed"]
            }
            for category, methods in FAILURE_MATRIX.items()
        }
        m08.pop("case_outcomes", None)
    m09 = suite_evidence(root, SUITES["M09"], sha)
    m09.pop("case_outcomes", None)
    kube = report(root, pathlib.Path("target/metrics/kubernetes.json"), sha, key="commit_sha")
    m18 = {"status": kube["status"], "source": kube["source"]}
    if kube["status"] == "OBSERVED":
        measured = kube["data"]
        # New kind controller test is NOT a deployment of actual Java services.
        if measured.get("schema_version") == 2:
            checks = ("initial_deploy", "rolling_update",
                      "failed_update_rollback", "pod_recovery")
            manifest = measured.get("manifest_validation") or {}
            is_valid = (measured.get("status") == "PASSED" and
                        measured.get("application_deployed") is False and
                        measured.get("workload_type", "").startswith("isolated nginx fixture") and
                        manifest.get("application_deployments_checked") == 6 and
                        manifest.get("deployments_started") == 0 and
                        all(isinstance(measured.get(key), dict) for key in checks) and
                        all(measured[key].get("duration_ms", 0) > 0
                            for key in ("initial_deploy", "rolling_update", "pod_recovery")) and
                        measured["failed_update_rollback"].get("rollback_duration_ms", 0) > 0 and
                        all(measured[key].get("http_successful" if key != "failed_update_rollback"
                            else "http_successful_after_rollback") == 5
                            for key in ("initial_deploy", "rolling_update",
                                        "failed_update_rollback")) and
                        measured["pod_recovery"].get("http_successful_after_recovery") == 5)
            m18 = {
                "status": "PASSED" if is_valid else "FAILED",
                "scope": "kind Kubernetes controller fixture + real VellumHub overlay dry-run",
                "real_application_deployed": False,
                "manifest_validation": manifest,
                "initial_deploy": measured.get("initial_deploy"),
                "rolling_update": measured.get("rolling_update"),
                "failed_update_rollback": measured.get("failed_update_rollback"),
                "pod_recovery": measured.get("pod_recovery"),
                "source": kube["source"],
                "limitations": measured.get("limitations")
            }
        else:
            deployments = measured.get("deployment_results", [])
            ready = sum(1 for item in deployments
                        if item.get("ready_replicas", 0) > 0)
            m18 = {
                "status": "PASSED" if measured.get("passed") and
                          ready == measured.get("expected_deployments") else "FAILED",
                "scope": "existing deployment readiness/smoke observation; no rollout/rollback benchmark",
                "ready_deployments": ready,
                "expected_deployments": measured.get("expected_deployments"),
                "deployment_results": deployments,
                "source": kube["source"],
                "limitation": measured.get("limitations")
            }
    return {
        "schema_version": 1, "project": "VellumHub", "commit_sha": sha,
        "captured_at_utc": dt.datetime.now(dt.timezone.utc).isoformat(),
        "environment": "local/CI (see original run metadata)",
        "M07": m07, "M08": m08, "M09": m09, "M10": m10,
        "M18": m18
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
    # Missing evidence is an explicit gap, not a passing claim.
    return 0


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""Summarize executed M07/M08/M10 evidence from matching-SHA reports; never infer capacity."""
import argparse
import json
import math
import random
from collections import defaultdict
from pathlib import Path


def load(path):
    with Path(path).open(encoding="utf-8") as f:
        return json.load(f)


def quantile(samples, p):
    ordered = sorted(samples)
    if not ordered:
        raise ValueError("empty values")
    i = (len(ordered) - 1) * p
    low = int(i)
    return ordered[low] + (ordered[min(low + 1, len(ordered) - 1)] - ordered[low]) * (i - low)


def compare_rankers(per_user, candidate, baseline):
    users = defaultdict(dict)
    for item in per_user:
        uid, ranker = item["userId"], item["ranker"]
        if ranker in users[uid]:
            raise ValueError("duplicate user/ranker")
        users[uid][ranker] = float(item["metrics"]["ndcgAt10"])
    if not users or any(candidate not in scores or baseline not in scores for scores in users.values()):
        raise ValueError("incomplete paired ranker evaluation")
    deltas = [modes[candidate] - modes[baseline] for modes in users.values()]
    if not all(math.isfinite(x) and -1 <= x <= 1 for x in deltas):
        raise ValueError("invalid ranking delta")
    rng = random.Random(282)
    bootstrap = [sum(rng.choice(deltas) for _ in deltas)/len(deltas) for __ in range(10000)]
    return {
        "paired_profiles": len(deltas),
        "wins": sum(x > 1e-9 for x in deltas),
        "ties": sum(abs(x) <= 1e-9 for x in deltas),
        "losses": sum(x < -1e-9 for x in deltas),
        "mean_paired_ndcg_delta": sum(deltas) / len(deltas),
        "profile_bootstrap_95pct_ci": [quantile(bootstrap, .025), quantile(bootstrap, .975)]
    }


def analyze(distributed_dir, quality_dir, sha, evidence_path=None):
    distributed, quality = Path(distributed_dir), Path(quality_dir)
    meta, raw, freshness, qmeta, qraw = (
        load(distributed / "run-metadata.json"),
        load(distributed / "raw-results.json"),
        load(distributed / "recommendation-freshness.json"),
        load(quality / "run-metadata.json"),
        load(quality / "raw-results.json")
    )
    if any(item.get("commitSha") != sha for item in (meta, freshness, qmeta)):
        raise ValueError("SHA mismatch in reference results")
    scenarios = raw.get("scenarioResults") or []
    visible = freshness.get("scenarioResults") or []
    if not scenarios or not visible or {s["scenario"] for s in scenarios} != {s["scenario"] for s in visible}:
        raise ValueError("incomplete or mismatched benchmark scenarios")
    if len(raw.get("eventMeasurements", [])) != sum(s["submittedEvents"] for s in scenarios):
        raise ValueError("raw event measurements not complete")
    lookup = {s["scenario"]: s for s in visible}
    series = []
    for entry in scenarios:
        v = lookup[entry["scenario"]]
        if entry["projectedEvents"] != entry["submittedEvents"] or v["observedRecommendations"] != v["submittedEvents"]:
            raise ValueError("incomplete projection or visible ranking")
        if entry["failedEvents"] or entry["unexpectedDltEvents"] or v["failedEvents"]:
            raise ValueError("failed propagation scenario")
        series.append({
            "scenario": entry["scenario"],
            "submitted": entry["submittedEvents"],
            "projected": entry["projectedEvents"],
            "visible": v["observedRecommendations"],
            "projection_throughput_events_per_second": entry["observedEventsPerSecond"],
            "projection_p95_ms": entry["projectionLatency"]["p95Millis"],
            "ranking_visible_p95_ms": v["latency"]["p95Millis"],
            "maximum_consumer_lag": entry["maximumConsumerLag"]
        })
    read = raw["readAutonomy"]
    records = raw.get("readMeasurements", [])
    successful = sum(x["httpStatus"] == 200 and x["failure"] is None for x in records)
    if read["requestedReads"] != len(records) or read["successfulReads"] != successful:
        raise ValueError("read summary disagrees with raw data")
    result = {
        "schema_version": 1, "source_sha": sha,
        "distributed": {
            "profile": meta["benchmarkProfile"],
            "scenarios": series,
            "read_autonomy": {
                "requested": read["requestedReads"], "successful": read["successfulReads"],
                "outbound_http_attempts": read["outboundHttpAttempts"],
                "read_p95_ms": read["latency"]["p95Millis"]
            },
            "limitations": "Real Kafka/pgvector in Testcontainers; synthetic events and pre-materialized read state. Observed throughput is not maximum capacity, recovery time, or production SLA."
        },
        "ranking": {
            "dataset_version": qmeta["datasetVersion"],
            "paired": {
                "blend_vs_popularity": compare_rankers(qraw["perUser"], "SEMANTIC_POPULARITY", "POPULARITY_ONLY"),
                "blend_vs_semantic": compare_rankers(qraw["perUser"], "SEMANTIC_POPULARITY", "SEMANTIC_ONLY")
            },
            "limitations": "Synthetic profiles; blend underperforms semantic-only. Bootstrap is descriptive, not an online outcome."
        },
        "outbox_failure_matrix": {"status": "MISSING"}
    }
    if evidence_path and Path(evidence_path).is_file():
        evidence = load(evidence_path)
        if evidence.get("commit_sha") != sha:
            raise ValueError("contract evidence SHA mismatch")
        suite = evidence.get("M08", {})
        matrix = suite.get("failure_matrix", {})
        if suite.get("status") == "PASSED" and matrix:
            result["outbox_failure_matrix"] = {
                "status": "PASSED",
                "executed_tests": suite.get("executed"),
                "categories": {
                    name: {
                        "passed": len(v.get("passed_methods", [])),
                        "expected": len(v.get("expected_methods", [])),
                        "missing_or_failed": v.get("missing_or_failed_methods", [])
                    } for name, v in matrix.items()
                },
                "limitations": "These are distinct assertion scenarios, not a measured fault-injection throughput or exactly-once guarantee."
            }
    return result


def render(result):
    rows = [
        "# VellumHub — measured backend outcomes", "",
        f"Source commit: {result['source_sha']}; executed integration artifacts.", "",
        "| Scenario | Projected | Ranking visible | Observed throughput | Projection p95 | Visible-ranking p95 |",
        "| --- | ---: | ---: | ---: | ---: | ---: |"
    ]
    for s in result["distributed"]["scenarios"]:
        rows.append(
            f"| {s['scenario']} | {s['projected']}/{s['submitted']} | {s['visible']}/{s['submitted']} "
            f"| {s['projection_throughput_events_per_second']:.2f} events/s "
            f"| {s['projection_p95_ms']:.2f} ms | {s['ranking_visible_p95_ms']:.2f} ms |"
        )
    r = result["distributed"]["read_autonomy"]
    rows.extend(["", f"Offline-upstream reads: {r['successful']}/{r['requested']}; "
                 f"{r['outbound_http_attempts']} monitored outbound HTTP attempts; read p95 {r['read_p95_ms']:.2f} ms.", "",
                 "| Paired rankers | Better profiles | Tied | Worse | Mean delta nDCG@10 | Profile-bootstrap 95% CI |",
                 "| --- | ---: | ---: | ---: | ---: | ---: |"])
    for name, entry in result["ranking"]["paired"].items():
        low, high = entry["profile_bootstrap_95pct_ci"]
        rows.append(
            f"| {name} | {entry['wins']}/{entry['paired_profiles']} | {entry['ties']} | {entry['losses']} "
            f"| {entry['mean_paired_ndcg_delta']:.4f} | [{low:.4f}, {high:.4f}] |"
        )
    matrix = result["outbox_failure_matrix"]
    if matrix["status"] == "PASSED":
        rows.append("\nOutbox: " + str(matrix["executed_tests"]) + " executed tests; failure categories: " +
                    ", ".join(f"{name} {v['passed']}/{v['expected']}"
                              for name, v in matrix["categories"].items()) + ".")
    else:
        rows.append("\nOutbox: missing evidence for this source commit.")
    rows.extend(["", result["distributed"]["limitations"], result["ranking"]["limitations"],
                 "The blend is NOT the best evaluated ranker for this synthetic corpus."])
    return "\n".join(rows) + "\n"


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--sha", required=True)
    p.add_argument("--distributed-dir", default="services/recommendation-service/target/distributed-benchmark/reference")
    p.add_argument("--quality-dir", default="services/recommendation-service/target/evaluation-results/reference")
    p.add_argument("--evidence-json", default="target/metrics/evidence.json")
    p.add_argument("--output-dir", default="target/metrics")
    args = p.parse_args()
    result = analyze(args.distributed_dir, args.quality_dir, args.sha, args.evidence_json)
    output = Path(args.output_dir)
    output.mkdir(parents=True, exist_ok=True)
    (output / "achievement-outcomes.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    (output / "achievement-outcomes.md").write_text(render(result), encoding="utf-8")
    print(render(result))


if __name__ == "__main__":
    main()

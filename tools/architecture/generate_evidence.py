#!/usr/bin/env python3
"""AFF-03: reproducible evidence compiler. Reports only observations from this invocation.

No extra framework or network dependencies: stdlib, Maven Surefire XML and #283 JSON.
Missing, skipped and failed suites never become green by catalog declaration.
"""
from __future__ import annotations

import argparse
import fnmatch
import json
import os
import platform
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
CATALOG = Path(__file__).with_name("fitness-catalog.json")
DEFAULT_OUTPUT = ROOT / "target" / "architecture-evidence"


def load_catalog(path: Path) -> list[dict]:
    doc = json.loads(path.read_text(encoding="utf-8"))
    if doc.get("schemaVersion") != 1:
        raise ValueError("Unknown fitness catalog schema")
    rules = doc.get("rules", [])
    ids = [rule["id"] for rule in rules]
    if len(ids) != len(set(ids)) or len(rules) != 11:
        raise ValueError("AFF catalog must contain 11 unique rules")
    for rule in rules:
        if rule["status"] not in {"PROPOSED", "BASELINE", "ENFORCED", "MONITORED"}:
            raise ValueError("Invalid AFF maturity: " + rule["id"])
        if not all(rule.get(k) for k in ("owner", "adr", "guide", "assertion", "command", "scope")):
            raise ValueError("Incomplete AFF traceability: " + rule["id"])
    return rules


def collect_junit(root: Path) -> list[dict]:
    results = []
    for folder in (root / "services", root / "lib", root / "integration-tests"):
        if not folder.exists():
            continue
        for xml_file in folder.glob("**/target/surefire-reports/TEST-*.xml"):
            tree = ET.parse(xml_file)
            for testcase in tree.iter("testcase"):
                classname = testcase.get("classname", xml_file.stem[5:])
                errors = [child for child in testcase if child.tag in ("failure", "error")]
                skipped = any(child.tag == "skipped" for child in testcase)
                results.append({
                    "suite": classname,
                    "test": testcase.get("name", ""),
                    "status": "FAIL" if errors else ("SKIPPED" if skipped else "PASS"),
                    "file": str(xml_file.relative_to(root)),
                    "failure": ((errors[0].get("message") or errors[0].text or "")[:350]
                                if errors else None),
                })
    return results


def run_static(output: Path) -> dict:
    commands = {
        "fixtures": [sys.executable, "-m", "unittest", "discover", "-s",
                     "tools/architecture", "-p", "test_*.py", "-v"],
        "service-boundaries": [sys.executable, "tools/architecture/check_service_boundaries.py"],
        "database-ownership": [sys.executable, "tools/architecture/check_database_ownership.py"],
    }
    observations = {}
    for label, command in commands.items():
        result = subprocess.run(command, cwd=ROOT, text=True, capture_output=True, check=False)
        observations[label] = {
            "exitCode": result.returncode,
            "command": " ".join(command),
            "outputFile": label + ".log",
        }
        (output / (label + ".log")).write_text(
            (result.stdout + "\n" + result.stderr)[-40000:], encoding="utf-8"
        )
    (output / "static-checks.json").write_text(
        json.dumps(observations, indent=2) + "\n", encoding="utf-8"
    )
    return observations


def validate_benchmark(rule_id: str, folder: Path, sha: str) -> tuple[bool, str]:
    try:
        meta = json.loads((folder / "run-metadata.json").read_text(encoding="utf-8"))
        raw = json.loads((folder / "raw-results.json").read_text(encoding="utf-8"))
        if meta["commitSha"] != sha:
            return False, "Benchmark commit SHA does not match workflow HEAD"
        scenarios = raw["scenarioResults"]
        if not scenarios or any(
            s["projectedEvents"] != s["submittedEvents"]
            or s["failedEvents"] != 0 or s["unexpectedDltEvents"] != 0
            for s in scenarios
        ):
            return False, "Projection convergence or DLT invariant violated"
        if rule_id == "AFF-REC-009":
            autonomy = raw["readAutonomy"]
            if not (autonomy["requestedReads"] > 0
                    and autonomy["successfulReads"] == autonomy["requestedReads"]
                    and autonomy["successRate"] == 1
                    and autonomy["outboundHttpAttempts"] == 0
                    and all(r["httpStatus"] == 200 and r["resultCount"] > 0
                            and r.get("failure") is None for r in raw["readMeasurements"])):
                return False, "Materialized read result or zero-outbound invariant violated"
            if len(raw["readMeasurements"]) != autonomy["requestedReads"]:
                return False, "Read measurement count mismatch"
            return True, "Materialized authenticated reads, result IDs and zero outbound attempts"
        if rule_id == "AFF-OPS-010":
            fresh = json.loads((folder / "recommendation-freshness.json").read_text(encoding="utf-8"))
            if fresh["commitSha"] != sha or fresh["profile"] != meta["benchmarkProfile"]:
                return False, "Freshness and propagation metadata do not match"
            cases = fresh["scenarioResults"]
            if not cases or any(c["submittedEvents"] != c["observedRecommendations"]
                                or c["failedEvents"] != 0 for c in cases):
                return False, "Event-to-visible-ranking convergence failure"
            if not fresh.get("measurements") or not raw.get("eventMeasurements"):
                return False, "Missing raw correlated measurements"
            return True, "Projection and visible ranking evidence retained (not an SLA)"
    except (KeyError, TypeError, ValueError, OSError) as exc:
        return False, "Missing or invalid benchmark evidence: " + str(exc)
    return False, "Unsupported benchmark property"


def evaluate(rule: dict, tests: list[dict], static: dict | None,
             benchmark: Path | None, sha: str) -> dict:
    selected = [
        t for t in tests
        if any(fnmatch.fnmatch(t["suite"].split(".")[-1], pat) for pat in rule["suites"])
        and not any(fnmatch.fnmatch(t["suite"].split(".")[-1], pat)
                    for pat in rule.get("excludedSuites", []))
    ]
    suites = sorted(set(t["suite"] for t in selected))
    passed_suites = sorted({t["suite"] for t in selected if t["status"] == "PASS"})
    skipped = [t for t in selected if t["status"] == "SKIPPED"]
    allowed_skips = rule.get("allowedSkippedSuites", [])
    unexpected_skips = [t for t in skipped if not any(fnmatch.fnmatch(t["suite"].split(".")[-1], p) for p in allowed_skips)]
    count = len(passed_suites)
    state = "NOT_RUN"
    reason = "No qualifying executed test evidence in this job"
    if selected:
        if any(t["status"] == "FAIL" for t in selected):
            state, reason = "FAIL", "JUnit regression failed"
        elif count < rule["minSuites"] or unexpected_skips:
            state, reason = "SKIPPED", "Suite coverage incomplete or skipped"
        else:
            state, reason = "PASS", f"{count} qualifying suites passed; {len(skipped)} non-applicable/skipped cases excluded from coverage"
    if static is not None:
        checks = {
            "AFF-BOUND-003": ("fixtures", "service-boundaries"),
            "AFF-DATA-004": ("fixtures", "database-ownership"),
        }.get(rule["id"])
        if checks:
            bad = [x for x in checks if static[x]["exitCode"] != 0]
            if bad:
                state, reason = "FAIL", "Static checks failed: " + ", ".join(bad)
            elif state in ("NOT_RUN", "PASS"):
                state, reason = "PASS", "Executed static check + negative fixtures"
    if benchmark is not None and rule["id"] in ("AFF-REC-009", "AFF-OPS-010"):
        valid, diagnostic = validate_benchmark(rule["id"], benchmark, sha)
        if not valid:
            state, reason = "FAIL", diagnostic
        elif state == "PASS":
            reason = diagnostic
        else:
            reason = "Benchmark JSON is valid, but required JUnit suite is unproven: " + reason
    return {
        "id": rule["id"],
        "maturity": rule["status"],
        "result": state,
        "reason": reason,
        "owner": rule["owner"],
        "adr": rule["adr"],
        "guide": rule["guide"],
        "command": rule["command"],
        "testSuites": suites,
        "passedSuites": passed_suites,
        "skippedCases": [t["suite"] + "#" + t["test"] for t in skipped],
        "executedCases": len(selected),
        "failedCases": [
            {"test": t["suite"] + "#" + t["test"], "reason": t["failure"]}
            for t in selected if t["status"] == "FAIL"
        ],
        "baseline": rule["baseline"],
        "exceptions": rule["exceptions"],
        "cost": rule["cost"],
        "cadence": rule["cadence"],
        "limitations": rule["limitations"],
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--catalog", type=Path, default=CATALOG)
    parser.add_argument("--repo-root", type=Path, default=ROOT)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--sha", default=os.getenv("GITHUB_HEAD_SHA", os.getenv("GITHUB_SHA", "local-uncommitted")))
    parser.add_argument("--run-id", default=os.getenv("GITHUB_RUN_ID", "local"))
    parser.add_argument("--profile", default="unspecified")
    parser.add_argument("--benchmark-dir", type=Path)
    parser.add_argument("--run-static", action="store_true")
    parser.add_argument("--require", action="append", default=[], metavar="AFF-ID")
    args = parser.parse_args()
    rules = load_catalog(args.catalog)
    args.output.mkdir(parents=True, exist_ok=True)
    static = run_static(args.output) if args.run_static else None
    tests = collect_junit(args.repo_root)
    report = {
        "schemaVersion": 1,
        "commitSha": args.sha,
        "workflowRunId": args.run_id,
        "profile": args.profile,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "platform": {"python": platform.python_version(), "os": platform.platform()},
        "observedJUnitCases": len(tests),
        "rules": [evaluate(rule, tests, static, args.benchmark_dir, args.sha)
                  for rule in rules],
    }
    results = {r["id"]: r for r in report["rules"]}
    if unknown := set(args.require) - results.keys():
        parser.error("Unknown required AFF: " + ", ".join(sorted(unknown)))
    report["requiredGates"] = args.require
    report["passedRequiredGates"] = all(results[k]["result"] == "PASS" for k in args.require)
    (args.output / "fitness-evidence.json").write_text(
        json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )
    table = [
        "# Architecture fitness — execution evidence", "",
        f"Commit: `{args.sha}` · Run: `{args.run_id}` · Profile: `{args.profile}`",
        "",
        "A rule's maturity is independent of execution. NOT_RUN is never green.",
        "",
        "| AFF | Maturity | This run | Evidence / diagnostic | ADR / correction guide |",
        "|---|---|---|---|---|",
    ]
    for rule in report["rules"]:
        diagnostic = rule["reason"].replace("|", "/").replace("\n", " ")
        guide_url = (f"https://github.com/{os.getenv('GITHUB_REPOSITORY', 'Luca5Eckert/VellumHub')}"
                     f"/blob/{args.sha}/{rule['guide']}")
        table.append(f"| {rule['id']} | {rule['maturity']} | {rule['result']} | {diagnostic} | {rule['adr']} · [Guide]({guide_url}) |")
    table.extend([
        "", "## Required gates",
        (", ".join(args.require) if args.require else "No additional gates for this evidence-only job"),
        "", "## Scope",
        "Only observed JUnit cases, executed static checks and matching-SHA benchmark JSON",
        "are asserted. Missing suites and untested deployment properties remain unproven.",
        "Do not interpret performance percentiles as production SLA/SLO.",
        "",
    ])
    (args.output / "fitness-evidence.md").write_text("\n".join(table), encoding="utf-8")
    for rule in report["rules"]:
        guide_url = (f"https://github.com/{os.getenv('GITHUB_REPOSITORY', 'Luca5Eckert/VellumHub')}"
                     f"/blob/{args.sha}/{rule['guide']}")
        print(f"{rule['id']}: {rule['result']} ({rule['reason']})"
              f" | {rule['adr']} | Correction guide: {guide_url}")
        if rule["result"] == "FAIL" or (rule["id"] in args.require and rule["result"] != "PASS"):
            diagnostic = rule["reason"].replace("\\n", " ").replace("::", ":")
            print(f"::error title={rule['id']} fitness violation::{diagnostic};"
                  f" decision={rule['adr']}; correction={guide_url}")
    return 1 if (static and any(v["exitCode"] for v in static.values())) or not report["passedRequiredGates"] else 0


if __name__ == "__main__":
    sys.exit(main())

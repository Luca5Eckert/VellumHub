#!/usr/bin/env bash
# M08/M09: test execution with a run marker for SHA-bound JUnit reports.
set -euo pipefail
cd "$(dirname "$0")/../.."
mkdir -p target/metrics
rm -f target/metrics/contract-test-run.json
sha="$(git rev-parse HEAD)"
start="$(date +%s)"
# Run actual Kafka/PostgreSQL tests and HTTP security-chain tests.
# Explicit -Dtest names isolate these suites in a multi-module Maven reactor.
mvn -B -ntp -Dtest=ConsumerIdempotencyDistributedTest,OAuthScopeBoundaryTest,HumanApiAccessTest,AccessTokenClaimsTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
export METRICS_SHA="$sha" METRICS_STARTED="$start"
python3 - <<'PY'
import datetime, json, os, pathlib
path = pathlib.Path("target/metrics/contract-test-run.json")
path.write_text(json.dumps({
    "commit_sha": os.environ["METRICS_SHA"],
    "started_epoch": int(os.environ["METRICS_STARTED"]),
    "finished_utc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
    "exit_code": 0,
    "command": "mvn -B -ntp -Dtest=ConsumerIdempotencyDistributedTest,OAuthScopeBoundaryTest,HumanApiAccessTest,AccessTokenClaimsTest -Dsurefire.failIfNoSpecifiedTests=false test",
    "scope": "JUnit test suites; not an automatic count of fully integrated consumer APIs"
}, indent=2) + "\n", encoding="utf-8")
PY

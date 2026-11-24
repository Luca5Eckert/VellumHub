#!/usr/bin/env bash
# Orchestrate the existing audit suites on the checked-out source SHA.
set -euo pipefail
cd "$(dirname "$0")/../.."
sha=$(git rev-parse HEAD)
output=$(realpath -m "${AUDIT_OUTPUT:-target/backend-audit/$sha}")
maven=${AUDIT_MAVEN:-./mvnw}
profile=${AUDIT_PROFILE:-reference}
case "$profile" in
  smoke|reference) ;;
  *) echo 'AUDIT_PROFILE must be smoke or reference' >&2; exit 2 ;;
esac
mkdir -p "$(dirname "$output")"
mkdir "$output"
printf 'commit_sha=%s\nprofile=%s\nstarted_utc=%s\n' "$sha" "$profile" "$(date -u +%FT%TZ)" > "$output/environment.txt"
{
  uname -smr
  "$maven" --version
  docker info --format 'Docker {{.ServerVersion}} {{.Architecture}} CPUs={{.NCPU}} memory_bytes={{.MemTotal}}'
  for limit in cpu.max memory.max; do
    if [[ -r "/sys/fs/cgroup/$limit" ]]; then
      printf '%s=%s\n' "$limit" "$(cat "/sys/fs/cgroup/$limit")"
    fi
  done
} >> "$output/environment.txt" 2>&1

failures=0
run() {
  local name=$1 status=0 start
  shift
  start=$(date -u +%FT%TZ)
  printf '%q ' "$@" > "$output/$name.command"
  printf '\n' >> "$output/$name.command"
  "$@" > "$output/$name.log" 2>&1 || status=$?
  printf '%s\t%s\t%s\t%s\n' "$name" "$start" "$(date -u +%FT%TZ)" "$status" | tee -a "$output/status.tsv"
  if ((status)); then failures=1; fi
  return "$status"
}

test_suite() {
  local name=$1 module=$2 tests=$3 suite
  shift 3
  local -a suites
  IFS=, read -r -a suites <<< "$tests"
  for suite in "${suites[@]}"; do
    rm -f services/"$module-service"/target/surefire-reports/TEST-*."$suite".xml
  done
  run "$name" "$maven" -B -ntp "$@" -pl "services/$module-service" -am \
    "-Dtest=$tests" -Dsurefire.failIfNoSpecifiedTests=false test
}
record_junit() {
  local module=$1 name
  for name in "${@:2}"; do
    python3 - "$module" "$name" "$output" <<'PY' || return "$?"
import json
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

module, suite, directory = sys.argv[1:]
reports = Path(f"services/{module}-service/target/surefire-reports")
(matches,) = reports.glob(f"TEST-*.{suite}.xml")
root = ET.parse(matches).getroot()
keys = ("tests", "failures", "errors", "skipped", "time")
result = {key: root.attrib[key] for key in keys}
result["cases"] = [case.attrib["name"] for case in root.findall("testcase")]
Path(directory, suite + ".json").write_text(json.dumps(result, indent=2) + "\n")
if (int(result["tests"]) <= int(result["skipped"])
        or int(result["failures"]) or int(result["errors"])):
    sys.exit("No successful test execution")
PY
  done
}
run architecture python3 -m unittest discover -s tools/architecture -p 'test_*.py' || :
run docs python3 tools/architecture/validate_docs.py || :
run ci-structure python3 -m unittest discover -s tools/ci -p 'test_*.py' || :

for suite in DistributedRecommendationBenchmarkIT RecommendationFreshnessBenchmarkIT; do
  test_suite "$suite" recommendation "$suite" "$@" -Dgroups=distributed-benchmark \
    "-Dbenchmark.profile=$profile" "-Dbenchmark.commitSha=$sha" "-Dbenchmark.outputDir=$output/recommendation" &&
    record_junit recommendation "$suite" || failures=1
done
test_suite idempotency recommendation ConsumerIdempotencyDistributedTest "$@" -Dgroups=distributed &&
  record_junit recommendation ConsumerIdempotencyDistributedTest || failures=1
test_suite ranking recommendation RecommendationQualityEvaluationIT "$@" -Dgroups=evaluation \
  "-Devaluation.commitSha=$sha" "-Devaluation.outputDir=$output/ranking" &&
  record_junit recommendation RecommendationQualityEvaluationIT || failures=1

for mode in false true; do
  for module in catalog recommendation; do
    if [[ $module == catalog ]]; then
      suite=CatalogCacheReadBenchmarkIT
      cache_dir=catalog-cache-benchmark
      args=(-Dgroups=catalog-cache-benchmark "-Dcatalog.cache.benchmark.enabled=$mode"
        "-Dcatalog.benchmark.profile=$profile" "-Dcatalog.benchmark.commitSha=$sha")
    else
      suite=RecommendationCacheReadBenchmarkIT
      cache_dir=cache-benchmark
      args=(-Dgroups=cache-benchmark "-Dcache.benchmark.enabled=$mode"
        "-Dbenchmark.profile=$profile" "-Dbenchmark.commitSha=$sha")
    fi
    filename=off.json
    if [[ $mode == true ]]; then filename=on.json; fi
    source="services/$module-service/target/$cache_dir/$sha/$filename"
    rm -f "$source"
    if test_suite "$module-cache-$mode" "$module" "$suite" "$@" "${args[@]}"; then
      cp "$source" "$output/$module-$filename" || failures=1
    fi
  done
done
run cache-comparability python3 - "$output" <<'PY' || failures=1
import json
from pathlib import Path
import sys

directory = Path(sys.argv[1])
services = (
    ("catalog", "enabled", "rows", "rep"),
    ("recommendation", "cache_enabled", "measurements", "repeat"),
)
for service, mode_key, rows_key, repetition in services:
    off, on = [
        json.loads((directory / f"{service}-{mode}.json").read_text())
        for mode in ("off", "on")
    ]
    if off[mode_key] is not False or on[mode_key] is not True:
        sys.exit(f"{service} requires OFF and ON artifacts")
    for key in ("commit_sha", "profile"):
        if off[key] != on[key]:
            sys.exit(f"{service} A/B differs on {key}")
    for baseline, cached in zip(off[rows_key], on[rows_key], strict=True):
        keys = ["scenario", "concurrency", repetition, "requests"]
        if service == "catalog":
            keys.append("response_hash_sum")
            if baseline["errors"] or cached["errors"]:
                sys.exit("Catalog A/B reported request errors")
        for key in keys:
            if baseline[key] != cached[key]:
                sys.exit(f"{service} A/B row differs on {key}")
    requests = sum(row["requests"] for run in (off, on) for row in run[rows_key])
    print(f"{service} A/B comparability checks passed for {requests} GETs")
PY
catalog_correctness=CatalogCacheRedisOutageBootIT,CatalogCacheTwoContextsIT,CatalogCacheNetworkHttpIT
test_suite catalog-cache-correctness catalog "$catalog_correctness" "$@" -Dgroups=catalog-cache-e2e &&
  record_junit catalog CatalogCacheRedisOutageBootIT CatalogCacheTwoContextsIT CatalogCacheNetworkHttpIT || failures=1
test_suite recommendation-cache-freshness recommendation RecommendationCacheFreshnessIT "$@" \
  -Dgroups=distributed-benchmark &&
  record_junit recommendation RecommendationCacheFreshnessIT || failures=1
exit "$failures"

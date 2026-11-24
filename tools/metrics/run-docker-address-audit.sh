#!/usr/bin/env bash
# Check endpoint address reuse without running an application or exposing ports.
set -euo pipefail
prefix="vh-audit-ip-$(date +%s)-$$"
network=$(docker network create "$prefix")
names=("$prefix-a" "$prefix-b" "$prefix-c")
cleanup() {
  docker rm -f "${names[@]}" >/dev/null 2>&1 || true
  docker network rm "$network" >/dev/null 2>&1 || true
}
trap cleanup EXIT
for name in "${names[@]}"; do
  docker run -d --name "$name" --network "$network" --entrypoint /bin/sleep redis:7.4-alpine 180 >/dev/null
done
format='{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}'
before=$(docker inspect --format "$format" "${names[@]}")
docker stop "${names[@]}" >/dev/null
docker start "${names[2]}" "${names[0]}" "${names[1]}" >/dev/null
after=$(docker inspect --format "$format" "${names[@]}")
python3 - "$before" "$after" <<'PY'
import sys

before, after = (arg.splitlines() for arg in sys.argv[1:])
if len(before) != 3 or len(after) != 3 or not all(before) or not all(after):
    sys.exit("Expected three assigned addresses")
for index, address in enumerate(after):
    previous = "ABC"[before.index(address)] if address in before else "none"
    print(f"Container {'ABC'[index]} acquired address formerly held by {previous}")
PY

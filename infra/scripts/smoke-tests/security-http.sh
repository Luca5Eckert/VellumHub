#!/usr/bin/env bash
# SEC-01 smoke test. Real Google success requires SECURITY_TEST_GOOGLE_ID_TOKEN.
set -euo pipefail
gateway="${GATEWAY_URL:-http://localhost:8080}"
failures=0
check() {
  local label="$1" expected="$2" method="$3" path="$4" payload="${5:-}"
  local status
  if [[ -n "$payload" ]]; then
    status=$(curl --silent --output /dev/null --write-out '%{http_code}' -X "$method" \
      -H 'Content-Type: application/json' --data "$payload" "$gateway$path")
  else
    status=$(curl --silent --output /dev/null --write-out '%{http_code}' -X "$method" "$gateway$path")
  fi
  if [[ "$status" == "$expected" ]]; then
    printf 'PASS %s: HTTP %s\n' "$label" "$status"
  else
    printf 'FAIL %s: expected %s got %s\n' "$label" "$expected" "$status" >&2
    failures=$((failures + 1))
  fi
}
check "anonymous protected" 401 GET /api/v1/recommendations/recommendations
check "non-public login method" 401 GET /api/v1/auth/login
check "non-public auth wildcard" 401 GET /api/v1/auth/not-public
check "invalid Google token" 400 POST /api/v1/auth/google '{"externalToken":"invalid-provider-id-token"}'
if [[ -n "${SECURITY_TEST_EMAIL:-}" && -n "${SECURITY_TEST_PASSWORD:-}" ]]; then
  token=$(curl --fail --silent -X POST -H 'Content-Type: application/json' \
    --data "$(printf '{"email":"%s","password":"%s"}' "$SECURITY_TEST_EMAIL" "$SECURITY_TEST_PASSWORD")" \
    "$gateway/api/v1/auth/login")
  if [[ "$token" == *.*.* ]]; then
    echo 'PASS local login -> token returned (redacted)'
  else
    echo 'FAIL local login response is not a JWT' >&2
    failures=$((failures + 1))
  fi
else
  echo 'SKIP positive local login (provide SECURITY_TEST_EMAIL and SECURITY_TEST_PASSWORD)' >&2
fi
if [[ -n "${SECURITY_TEST_GOOGLE_ID_TOKEN:-}" ]]; then
  status=$(curl --silent --output /dev/null --write-out '%{http_code}' \
    -X POST -H 'Content-Type: application/json' \
    --data "$(printf '{"externalToken":"%s"}' "$SECURITY_TEST_GOOGLE_ID_TOKEN")" \
    "$gateway/api/v1/auth/google")
  if [[ "$status" == 200 ]]; then
    echo 'PASS positive Google login: HTTP 200 (token redacted)'
  else
    echo "FAIL positive Google login: expected 200 got $status" >&2
    failures=$((failures + 1))
  fi
else
  echo 'SKIP positive Google login (provide SECURITY_TEST_GOOGLE_ID_TOKEN)' >&2
fi
exit "$((failures > 0))"

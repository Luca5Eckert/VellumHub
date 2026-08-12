#!/usr/bin/env bash

set -uo pipefail

GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"
USER_SERVICE_URL="${USER_SERVICE_URL:-}"
CATALOG_SERVICE_URL="${CATALOG_SERVICE_URL:-}"
ENGAGEMENT_SERVICE_URL="${ENGAGEMENT_SERVICE_URL:-}"
RECOMMENDATION_SERVICE_URL="${RECOMMENDATION_SERVICE_URL:-}"

passed=0
failed=0

check_endpoint() {
  local service_name="$1"
  local service_url="$2"
  local path="$3"

  if curl --fail --silent --show-error "${service_url}${path}" >/dev/null; then
    printf 'PASS %s %s\n' "$service_name" "$path"
    passed=$((passed + 1))
  else
    printf 'FAIL %s %s\n' "$service_name" "$path" >&2
    failed=$((failed + 1))
  fi
}

check_service() {
  local service_name="$1"
  local service_url="$2"

  check_endpoint "$service_name" "$service_url" "/actuator/health"
  check_endpoint "$service_name" "$service_url" "/actuator/health/liveness"
  check_endpoint "$service_name" "$service_url" "/actuator/health/readiness"
  check_endpoint "$service_name" "$service_url" "/actuator/prometheus"
}

check_service "gateway-service" "$GATEWAY_URL"

for service in user catalog engagement recommendation; do
  variable_name="${service^^}_SERVICE_URL"
  variable_name="${variable_name//-/_}"
  service_url="${!variable_name:-}"

  if [[ -n "$service_url" ]]; then
    check_service "${service}-service" "$service_url"
  fi
done

printf '\nSmoke result: %d passed, %d failed\n' "$passed" "$failed"
[[ "$failed" -eq 0 ]]

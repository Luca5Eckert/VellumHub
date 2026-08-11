#!/usr/bin/env bash

set -euo pipefail

COMPOSE_BIN="${COMPOSE_BIN:-docker}"
GATEWAY_SERVICE="${GATEWAY_SERVICE:-gateway-service}"
KAFKA_SERVICE="${KAFKA_SERVICE:-kafka}"

run_in_gateway() {
  "$COMPOSE_BIN" compose exec -T "$GATEWAY_SERVICE" wget --no-verbose --tries=1 --spider "$1"
}

for service in gateway-service user-service catalog-service engagement-service recommendation-service; do
  run_in_gateway "http://${service}:8080/actuator/health/liveness"
  run_in_gateway "http://${service}:8080/actuator/health/readiness"
done

"$COMPOSE_BIN" compose exec -T "$KAFKA_SERVICE" kafka-broker-api-versions --bootstrap-server kafka:29092 >/dev/null

printf 'Compose runtime smoke checks passed.\n'

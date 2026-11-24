#!/usr/bin/env bash
# Isolated real-image smoke against a previously published main SHA.
set -euo pipefail
umask 077
cd "$(dirname "$0")/../.."
audit_scripts="$PWD/tools/metrics"
sha=${1:?Usage: run-compose-audit.sh PUBLISHED_SOURCE_SHA OUTPUT_DIRECTORY}
[[ "$sha" =~ ^[0-9a-f]{40}$ ]]
output=$(realpath -m "${2:?Provide an output directory}")
mkdir -p "$(dirname "$output")"
mkdir "$output"
project="vh-audit-$(date +%s)-$$"
stack=$(mktemp -d "$output/stack.XXXXXX")
git archive "$sha" | tar -x -C "$stack"
cd "$stack"
cp .env.example .env
# Archive extraction under umask 077 otherwise hides public configs from image UIDs.
find infra/observability -type d -exec chmod 755 {} +
find infra/observability -type f -exec chmod 644 {} +
sh infra/scripts/security/generate_auth_poc_keys.sh > "$output/key-generation.log" 2>&1
export VELLUM_AUTH_ISSUER=http://127.0.0.1:9000
export VELLUM_GATEWAY_URL=http://127.0.0.1:8080
export NO_PROXY="localhost,127.0.0.1,${NO_PROXY:-}"
export no_proxy="$NO_PROXY"
export VELLUM_POC_RUN_AS="$(id -u):$(id -g)"
export VELLUM_POC_LOGIN=ci-fixture@example.invalid
export VELLUM_POC_PASSWORD=ci-only-password-not-for-production-1234
export VELLUM_AUTH_KEY_DIR="$stack/.local/auth-poc"
export RECOMMENDATION_CACHE_ENABLED=false
export JAVA_TOOL_OPTIONS="-Xms64m -Xmx512m -XX:ActiveProcessorCount=2 -javaagent:/app/opentelemetry-javaagent.jar"
{
  printf 'services:\n'
  for service in auth user catalog engagement recommendation gateway; do
    printf '  %s-service:\n    image: ghcr.io/luca5eckert/vellumhub-%s:%s\n' "$service" "$service" "$sha"
    printf '    healthcheck:\n      start_period: 240s\n'
    if [[ $service == auth ]]; then
      printf '      test: ["CMD-SHELL", "wget -q -O /dev/null http://127.0.0.1:9001/actuator/health/liveness"]\n'
    fi
    if [[ $service == recommendation ]]; then
      printf '      test: ["CMD", "wget", "--no-proxy", "-q", "-O", "/dev/null", "http://127.0.0.1:8080/actuator/health/liveness"]\n'
    fi
    cat <<'YAML'
    environment:
      HTTP_PROXY: ""
      HTTPS_PROXY: ""
      ALL_PROXY: ""
      http_proxy: ""
      https_proxy: ""
      all_proxy: ""
      NO_PROXY: "localhost,127.0.0.1"
      no_proxy: "localhost,127.0.0.1"
YAML
    if [[ $service == auth ]]; then
      # Keep the base Compose scrape port when combining the OIDC and telemetry profiles.
      printf '      MANAGEMENT_SERVER_PORT: "9001"\n'
    fi
    if [[ $service == gateway ]]; then
      printf '    ports: !override ["127.0.0.1:8080:8080"]\n'
    fi
  done
  for binding in prometheus:9090:9090 grafana:3002:3000 loki:3100:3100 tempo:3200:3200 alloy:12345:12345; do
    IFS=: read -r service host container <<< "$binding"
    printf '  %s:\n    ports: !override ["127.0.0.1:%s:%s"]\n' "$service" "$host" "$container"
  done
} > audit.yaml
export COMPOSE_PROJECT_NAME="$project"
export COMPOSE_FILE=compose.yaml:infra/compose/oidc-local.yaml:audit.yaml
export COMPOSE_PROFILES=observability
cleanup() {
  docker compose ps --all > "$output/compose-ps.txt" 2>&1 || true
  docker compose down -v > "$output/compose-cleanup.log" 2>&1 || true
  rm -rf -- "$stack"
}
trap cleanup EXIT
printf 'commit_sha=%s\nstarted_utc=%s\n' "$sha" "$(date -u +%FT%TZ)" > "$output/compose-environment.txt"
docker compose up -d --no-build --pull missing --wait --wait-timeout 420 \
  gateway-service auth-service redis-recommendation-cache alloy prometheus grafana > "$output/compose-start.log" 2>&1
docker compose images > "$output/compose-images.txt"
status=0
# The existing verifier restarts Auth after exercising warm JWKS.
VELLUM_VERIFY_OFFLINE=1 python3 infra/scripts/security/verify_oidc_lote2_flow.py > "$output/oidc.log" 2>&1 || status=1
python3 infra/scripts/validation/verify-observability.py > "$output/observability.log" 2>&1 || status=1
for attempt in $(seq 1 60); do
  if curl -fsS --noproxy '*' http://127.0.0.1:9000/.well-known/openid-configuration >/dev/null 2>&1; then break; fi
  sleep 2
done
python3 "$audit_scripts/compose-read-window.py" "$output/compose-read-window.json" > "$output/compose-read-window.log" 2>&1 || status=1
exit "$status"

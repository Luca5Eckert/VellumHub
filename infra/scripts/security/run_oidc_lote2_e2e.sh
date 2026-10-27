#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../../.."
umask 077
mkdir -p .local/legacy-oidc-test .local/jwks-public
touch .env
if [ ! -f .local/legacy-oidc-test/private.pem ]; then
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out .local/legacy-oidc-test/private.pem
  openssl pkey -in .local/legacy-oidc-test/private.pem -pubout -out .local/legacy-oidc-test/public.pem
fi
if [ ! -f .local/auth-poc/signing-private.pem ]; then
  sh infra/scripts/security/generate_auth_poc_keys.sh
fi
export VELLUM_POC_RUN_AS="$(id -u):$(id -g)"
export VELLUM_AUTH_DB_PASSWORD=ci-auth-db-only
export VELLUM_AUTH_KEY_DIR="$PWD/.local/auth-poc"
export VELLUM_POC_LOGIN=ci-fixture@example.invalid
export VELLUM_POC_PASSWORD=ci-only-password-not-for-production-1234
export VELLUM_INTERNAL_PROVISION_KEY=ci-internal-key-not-production-at-least-32
export JWT_ISSUER=http://127.0.0.1:9000
export JWT_AUDIENCE=api://vellumhub
export JWT_PUBLIC_KEY_PATH="$PWD/.local/legacy-oidc-test/public.pem"
export JWT_PRIVATE_KEY_PATH="$PWD/.local/legacy-oidc-test/private.pem"
export JWT_EXPIRATION_MS=3600000
export GOOGLE_CLIENT_ID=ci-not-configured
export CORS_ALLOWED_ORIGINS=http://127.0.0.1:9100
export POSTGRES_CATALOG_USER=ci_catalog
export POSTGRES_CATALOG_PASSWORD=ci-catalog-db
export POSTGRES_ENGAGEMENT_USER=ci_engagement
export POSTGRES_ENGAGEMENT_PASSWORD=ci-engagement-db
export POSTGRES_USER_USER=ci_user
export POSTGRES_USER_PASSWORD=ci-user-db
export POSTGRES_RECOMMENDATION_USER=ci_recommendation
export POSTGRES_RECOMMENDATION_PASSWORD=ci-recommendation-db
# Auth and legacy private keys are NOT shared.
chmod 600 .local/legacy-oidc-test/private.pem
sudo chown 10001:10001 .local/legacy-oidc-test/private.pem
chmod 644 .local/legacy-oidc-test/public.pem
cleanup() {
  status=$?
  if [ "$status" -ne 0 ]; then
    docker compose -f docker-compose.yml -f docker-compose.oidc-lote2.yml ps
  fi
  docker compose -f docker-compose.yml -f docker-compose.oidc-lote2.yml down -v --remove-orphans || true
  exit "$status"
}
trap cleanup EXIT
docker compose -f docker-compose.yml -f docker-compose.oidc-lote2.yml config --quiet
docker compose -f docker-compose.yml -f docker-compose.oidc-lote2.yml up -d redis-gateway kafka postgres-user postgres-catalog auth-postgres
docker compose -f docker-compose.yml -f docker-compose.oidc-lote2.yml up -d --build --no-deps user-service auth-service catalog-service gateway-service
for service in auth-service user-service catalog-service gateway-service; do
  ready=0
  for attempt in $(seq 1 75); do
    if [ "$service" = auth-service ]; then
      curl -sf http://127.0.0.1:9000/actuator/health >/dev/null && ready=1
    elif [ "$service" = gateway-service ]; then
      curl -sf http://127.0.0.1:8080/actuator/health >/dev/null && ready=1
    else
      docker compose -f docker-compose.yml -f docker-compose.oidc-lote2.yml exec -T "$service" wget -q -O /dev/null http://127.0.0.1:8080/actuator/health/liveness >/dev/null 2>&1 && ready=1
    fi
    if [ "$ready" = 1 ]; then break; fi
    sleep 3
  done
  if [ "$ready" != 1 ]; then
    echo "Not ready: $service" >&2
    exit 1
  fi
done
VELLUM_VERIFY_OFFLINE=1 python3 infra/scripts/security/verify_oidc_lote2_flow.py
echo "SEC-06/09 distributed E2E passed"

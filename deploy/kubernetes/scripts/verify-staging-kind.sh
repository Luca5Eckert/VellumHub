#!/usr/bin/env bash
# Run from the repository root on a disposable Kind context.
set -euo pipefail
[[ "$(kubectl config current-context)" == kind-vellumhub-staging ]]
work="$(mktemp -d)"
trap 'if [[ -n ${forward_pid:-} ]]; then kill "$forward_pid" 2>/dev/null || true; fi; rm -rf "$work"' EXIT
umask 077

kubectl create namespace vellumhub
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$work/private.pem" 2>/dev/null
openssl pkey -in "$work/private.pem" -pubout -out "$work/public.pem"
openssl req -x509 -newkey rsa:2048 -nodes -days 1 -subj /CN=auth.vellumhub.test \
  -addext 'subjectAltName=DNS:auth.vellumhub.test,DNS:api.vellumhub.test' \
  -keyout "$work/tls.key" -out "$work/tls.crt" 2>/dev/null
kubectl -n vellumhub create secret tls vellumhub-staging-tls --cert="$work/tls.crt" --key="$work/tls.key"
kubectl -n vellumhub create secret generic vellumhub-jwt-private --from-file=private.pem="$work/private.pem"
kubectl -n vellumhub create configmap vellumhub-jwt-public --from-file=public.pem="$work/public.pem"
kubectl -n vellumhub create secret generic vellumhub-secrets \
  --from-literal=POSTGRES_USER=vellumhub --from-literal=POSTGRES_PASSWORD="$(openssl rand -hex 20)" \
  --from-literal=POSTGRES_AUTH_USER=auth_owner --from-literal=POSTGRES_AUTH_PASSWORD="$(openssl rand -hex 20)" \
  --from-literal=VELLUM_INTERNAL_PROVISION_KEY="$(openssl rand -hex 32)" \
  --from-literal=KAFKA_CATALOG_JAAS= --from-literal=KAFKA_USER_JAAS= \
  --from-literal=KAFKA_ENGAGEMENT_JAAS= --from-literal=KAFKA_RECOMMENDATION_JAAS=

kubectl kustomize deploy/kubernetes/overlays/staging >"$work/rendered.yaml"
kubectl apply --dry-run=server -f "$work/rendered.yaml"
kubectl apply -f "$work/rendered.yaml"
kubectl -n vellumhub wait --for=jsonpath='{.status.readyReplicas}'=1 statefulset --all --timeout=8m
kubectl -n vellumhub wait --for=condition=Available deployment --all --timeout=8m

kubectl -n ingress-system port-forward --address 127.0.0.1 service/traefik 8443:443 >"$work/forward.log" 2>&1 &
forward_pid=$!
export SSL_CERT_FILE="$work/tls.crt"
export VELLUM_AUTH_ISSUER=https://auth.vellumhub.test:8443
export VELLUM_GATEWAY_URL=https://api.vellumhub.test:8443
export VELLUM_POC_REDIRECT_URI=https://app.vellumhub.test/oauth/callback
export VELLUM_POC_LOGIN=placeholder@example.invalid VELLUM_POC_PASSWORD=ci-placeholder-only
curl -fsS --retry 20 --retry-all-errors --retry-delay 2 --max-time 5 \
  --cacert "$SSL_CERT_FILE" "$VELLUM_AUTH_ISSUER/.well-known/openid-configuration" >"$work/discovery.json"
jq -e --arg issuer "$VELLUM_AUTH_ISSUER" \
  '.issuer == $issuer and (.authorization_endpoint | startswith($issuer + "/"))' "$work/discovery.json" >/dev/null

python3 deploy/kubernetes/scripts/verify-staging-preference.py "$work/user-id"
user_id="$(cat "$work/user-id")"
for attempt in $(seq 1 60); do
  if kubectl -n vellumhub exec -i statefulset/recommendation-postgres -- sh -ec \
    'psql -At -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v user_id="$1" < /dev/stdin' \
    probe "$user_id" <<'SQL' | grep -qx 1
SELECT count(*) FROM user_profiles WHERE user_id = :'user_id'::uuid;
SQL
  then
    echo "Recommendation projection materialized"
    break
  fi
  [[ "$attempt" -lt 60 ]] || exit 1
  sleep 2
done

gateway=(kubectl -n vellumhub)
original="$("${gateway[@]}" get deployment gateway-service -o jsonpath='{.spec.template.spec.containers[0].image}')"
"${gateway[@]}" set image deployment/gateway-service gateway-service=ghcr.io/luca5eckert/vellumhub-gateway:staging-invalid-image
image_failure_observed=false
for _ in $(seq 1 30); do
  if "${gateway[@]}" get pods -l app.kubernetes.io/name=gateway-service -o json \
    | jq -e '.items[].status.containerStatuses[]?.state.waiting.reason | select(. == "ErrImagePull" or . == "ImagePullBackOff")' >/dev/null; then
    image_failure_observed=true
    break
  fi
  sleep 2
done
[[ "$image_failure_observed" == true ]]
"${gateway[@]}" rollout undo deployment/gateway-service
"${gateway[@]}" rollout status deployment/gateway-service --timeout=3m
[[ "$("${gateway[@]}" get deployment gateway-service -o jsonpath='{.spec.template.spec.containers[0].image}')" == "$original" ]]
python3 infra/scripts/security/verify_oidc_lote2_flow.py

#!/usr/bin/env sh
set -eu
issuer="${VELLUM_AUTH_ISSUER:-http://127.0.0.1:9000}"
echo "Checking OIDC discovery and public JWKS on $issuer"
discovery="$(curl --fail --silent --show-error "$issuer/.well-known/openid-configuration")"
echo "$discovery" | grep -q '"issuer"'
echo "$discovery" | grep -q '"jwks_uri"'
keys="$(curl --fail --silent --show-error "$issuer/oauth2/jwks")"
echo "$keys" | grep -q '"kid"'
if echo "$keys" | grep -Eq '"(d|p|q|dp|dq|qi)"[[:space:]]*:'; then
  echo "CRITICAL: JWKS contains private RSA parameters" >&2
  exit 1
fi
status="$(curl -s -o /dev/null -w '%{http_code}' -X POST "$issuer/oauth2/token" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'grant_type=authorization_code' \
  --data-urlencode 'client_id=vellumhub-spa-poc' \
  --data-urlencode 'code=invalid-reused-code' \
  --data-urlencode 'redirect_uri=http://127.0.0.1:9100/index.html' \
  --data-urlencode 'code_verifier=invalid')"
if [ "$status" != 400 ]; then
  echo "Expected invalid authorization code -> 400, received $status" >&2
  exit 1
fi
echo "Discovery, public-only JWKS and invalid-code denial: OK"
echo "This smoke does NOT prove login, DB restart, API authorization, Google or CI; see services/auth-service/README.md."

# The browser client sends a CORS preflight when posting an authorization code.
headers="$(curl --silent --show-error --include -X OPTIONS "$issuer/oauth2/token" \
  -H 'Origin: http://127.0.0.1:9100' \
  -H 'Access-Control-Request-Method: POST' \
  -H 'Access-Control-Request-Headers: content-type')"
printf '%s' "$headers" | grep -iq '^access-control-allow-origin: http://127.0.0.1:9100' || {
  echo "SPA token endpoint preflight lacks explicit allowed origin" >&2
  exit 1
}
echo "SPA token CORS preflight: OK"

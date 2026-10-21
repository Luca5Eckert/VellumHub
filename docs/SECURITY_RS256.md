# SEC-03: JWT RSA/RS256 cutover (issue #163)

**Status:** Implemented in merged PR #320 (merge commit `a34fd10cca9a0a7f3414e2e3d5849e864662807b`, 2026-10-19); production deployment not evidenced. JWKS/rotation (#304) and issuer/audience/profile changes (#305) are being implemented separately in draft PR #321; see docs/SECURITY_JWKS_RFC9068.md. OAuth/OIDC Authorization Server and permissions remain outside #163.

## Contract and trust boundary

The User Service remains the only access-token issuer and signs JWTs with `RS256` using a 2048-bit-or-stronger RSA private key. It also verifies bearer tokens locally using the matching X.509 public key. The Gateway, Catalog, Engagement, and Recommendation services verify the same RS256 signatures independently with **only the public key**. Spring Security Nimbus decoders are configured to permit `RS256` only; `HS256`, unsigned/altered JWTs, tokens signed by another key, and expired tokens must fail closed. The existing `sub=email`, `user_id`, `roles`, `iat` and `exp` claims remain unchanged. A signed JWT is not encrypted and does not conceal claims.

No service calls the User Service to validate each token. No symmetric signing material is distributed to resource servers. No JWKS endpoint, key rotation, dual-algorithm fallback, or automatic downgrade is introduced here.

## Provision RSA keys

Use an **unencrypted PKCS#8 PEM** private key and a matching **X.509 SubjectPublicKeyInfo PEM** public key. For local development, from the repository root:

```bash
mkdir -p .local/keys
umask 077
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out .local/keys/private.pem
openssl pkey -in .local/keys/private.pem -pubout -out .local/keys/public.pem
chmod 600 .local/keys/private.pem
openssl pkey -in .local/keys/private.pem -check -noout
openssl pkey -pubin -in .local/keys/public.pem -noout
```

Use a trusted secret manager for production key generation and custody; **never commit or upload private keys to GitHub, CI logs, images, public ConfigMaps, tickets, or chat**. Local `.local/keys/` is gitignored. Public PEM may be distributed separately. Treat the private PEM as a deployable credential; its disclosure requires emergency replacement and session invalidation. Do not generate new keys on each app restart or each replica.

## Docker Compose

`cp .env.example .env` and set:

```dotenv
JWT_PUBLIC_KEY_PATH=./.local/keys/public.pem
JWT_PRIVATE_KEY_PATH=./.local/keys/private.pem
JWT_EXPIRATION_MS=3600000
```

Compose mounts `JWT_PUBLIC_KEY_PATH` read-only at `/run/keys/public.pem` in **all five** containers, and `JWT_PRIVATE_KEY_PATH` read-only at `/run/keys/private.pem` in **User Service only**. The images run as non-root UID **10001**, so the signing PEM must be readable by UID 10001 even when the host created it as another user. On Linux, use a narrowly scoped ACL (`setfacl -m u:10001:r .local/keys/private.pem`) or change only the private key's owner to UID 10001 (`sudo chown 10001:10001 .local/keys/private.pem; sudo chmod 600 .local/keys/private.pem`). Never change the private file to world-readable as a workaround. The corresponding application properties are `jwt.public-key-location=file:/run/keys/public.pem` and (User Service only) `jwt.private-key-location=file:/run/keys/private.pem`. A missing/invalid key prevents secure startup; it must not fall back to HMAC or generate a fresh key automatically.

Start with `docker compose up -d --build`. Mounts require readable host file permissions for the container runtime. Verify the User Service replicas use the same key pair; do not mount any private PEM into Gateway, Catalog, Engagement, or Recommendation.

## Kubernetes

The manifests expect **two independently provisioned objects** in namespace `vellumhub`:

- ConfigMap `vellumhub-jwt-public` with data key `public.pem`. Mounted read-only in all five services.
- Secret `vellumhub-jwt-private` with data key `private.pem`. Mounted read-only **only in User Service**.

Provision these outside version control, preferably using an external secret manager/CSI integration. A local demonstrative bootstrap:

```bash
kubectl -n vellumhub create configmap vellumhub-jwt-public \
  --from-file=public.pem=.local/keys/public.pem --dry-run=client -o yaml | kubectl apply -f -
kubectl -n vellumhub create secret generic vellumhub-jwt-private \
  --from-file=private.pem=.local/keys/private.pem --dry-run=client -o yaml | kubectl apply -f -
kubectl -n vellumhub apply -k deploy/kubernetes/base
```

Never put the private PEM in `deploy/kubernetes/base/config/secret.example.yaml`, ConfigMap or Git. Kubernetes Secret is not a substitute for an encrypted-at-rest secret manager. Check `kubectl describe pod` if keys are missing, but do not print secret contents. The rolling manifests use two User Service replicas: **both must share a single issuer key pair**.

## Deployment sequence: coordinated cutover

**This issue does not implement coexistence.** New RS256-only validators reject already-issued HS256 access tokens; legacy validators reject newly issued RS256 tokens. Mixing old and new binaries in a rolling cluster can therefore interrupt authenticated traffic, even if all have valid keys.

1. Inventory clients, ingress, instances, token TTL and readiness; plan a maintenance window or explicit temporary request drain. Notify clients that **reauthentication is required**.
2. Generate/provision the matching RSA pair to the secure store. Mount the public PEM into every validator and the private PEM **only** into the issuer. Validate that no resource server has the private key or `JWT_KEY`.
3. Drain authentication traffic during the version transition; replace all Gateway/Resource Server replicas and User Service issuers in one coordinated cutover, **not** an unsynchronized rolling update. Confirm all new instances load their PEMs and are ready before reopening.
4. Force new login or token renewal through the existing login flow. Existing HS256 tokens fail; do not silently retain HS256 acceptance.
5. Validate positive login and authenticated resource requests as well as negative cases: expired, malformed, tampered, wrong-key, and HS256 JWTs must result in 401 from protected HTTP routes. Confirm `ROLE_*` mapping, user ownership behavior, observability without token/key exposure, Google login, and cross-service calls.
6. Monitor rejection rates, errors and availability during rollout. The deployment manifest's generic `RollingUpdate` settings do **not** provide a safe multi-service atomic rollout; orchestration must enforce the drain and global transition.

Do not claim cutover from a passing unit suite alone. Collect application commit SHA, CI run, integration test results and deployment evidence.

## Rollback and incident response

If deployment fails, restore the previous, known-good **set of binaries and associated legacy HMAC configuration** in a coordinated rollback under a controlled traffic drain. Tokens minted by the new RS256 issuer will not verify on the legacy stack and users may need to sign in again. Evaluate whether to revoke sessions/tokens and restore the previous JWT expiration policy. Do not bypass signature validation, dynamically choose token algorithms, or expose the RSA private key to consumers for compatibility. After rollback, report the incident and retry only with corrected key provisioning/tests.

A compromised private key requires key replacement, rejection of old tokens and explicit reauthentication; automatic RSA key rotation and dual-key verification belong to #304.

## Verification checklist

```bash
# Unit and narrow context tests for the five affected modules:
mvn -f services/user-service/pom.xml test
mvn -f services/gateway-service/pom.xml test
mvn -f services/catalog-service/pom.xml test
mvn -f services/engagement-service/pom.xml test
mvn -f services/recommendation-service/pom.xml test

# With a running Compose stack, exercise real login and signed JWT verification:
python3 infra/scripts/security/verify_rs256_compose.py

# Compose config verifies mount resolution after setting .env:
docker compose config --quiet
```

Run project-root reactor/CI workflows when dependency artifacts (e.g. `kafka-contracts`) require local installation. Security regression coverage should include: issuance `alg=RS256`, public-only verifier, tampered payload, alternate RSA key, HS256, malformed JWT, key parsing failure, and minimum RSA strength. These tests do not replace end-to-end authenticated HTTP and Kubernetes provisioning checks.

## Troubleshooting

| Symptom | Verify |
|---|---|
| Startup fails because PEM cannot be read | Mount paths, file existence, permissions, namespace/ConfigMap/Secret keys; no permissive fallback |
| RSA public key malformed | `openssl pkey -pubin -in public.pem -noout`; X.509 `BEGIN PUBLIC KEY` required |
| RSA private key malformed | Unencrypted PKCS#8 `BEGIN PRIVATE KEY` and correct owner/permission; do not log key bytes |
| All freshly issued JWTs get 401 | Confirm exactly matching public/private pair in each deployment and no stale Gateway/Resource Server replicas |
| Only old browser sessions fail | Expected HS256-to-RS256 break; force login rather than enabling HS256 |
| Some requests fail intermittently | Old/new pods overlap or inconsistent key provisioning; drain and coordinate rollout |
| Public-key changes not visible | ConfigMap refresh into mounted files does not hot-reload the decoder; restart consumers as one controlled release (automated rotation remains #304) |

## Decisions and limitations

- **RSA/RS256 vs HS256:** removes forging capability from consumers, but introduces key lifecycle, secure distribution and coordinated deployment.
- **Files/secret mounts vs JWKS:** no network dependency for verification and respects #163 scope; manual public-key rollout is more operationally expensive. JWKS with `kid`, caching, rotation and overlap belong to #304.
- **Strict cutover vs dual-acceptance:** fail closed with simpler attack surface, but causes planned reauthentication and requires avoiding mixed deployments.
- **Claims and HTTP contracts:** untouched; migrating issuer, audience, immutable subject and OAuth2/OIDC token profile belongs to #305 and later issues.

**Obsidian source of truth:** [Segurança – Fonte da Verdade](https://github.com/Luca5Eckert/obsidian-vault/blob/main/projects/vellum-hub/architecture/Seguranca%20-%20Fonte%20da%20Verdade.md), ADR-016 and Roadmap must be updated with merge SHA, CI proof and this runbook **after** PR merge. Until then, record #163 as **proposed implementation**, not deployed or verified.

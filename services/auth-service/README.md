# Auth Service — canonical Authorization Server

[Architecture card · VH-AUT](../../docs/architecture/services/auth.md) · [C4 containers](../../docs/architecture/diagrams/c4-containers.md) · [All service codes](../../docs/architecture/services/README.md).

**Current contract:** Auth is the sole identity and access-token issuer, implemented in the repository. Live Google/HTTPS/proxy rollout requires separate verification.

The Auth Service is VellumHub's single authentication and identity authority and the only issuer/JWKS source for API access tokens. It is part of the default Maven reactor, Compose runtime and Kubernetes topology.

## Ownership

Auth owns:

- authenticatable identities and the canonical UUIDv7 subject;
- local credentials;
- Google OIDC external identities;
- browser sessions and logout;
- OAuth/OIDC clients, grants, authorizations and consents;
- minimum USER/ADMIN authorities used by current API tokens;
- RS256 signing material and the public JWKS.

User Service owns profile and preferences only. Auth provisions a User profile idempotently with the same UUID and never copies a password, external identity or signing material into User.

Gateway, User, Catalog, Engagement and Recommendation are Resource Servers. They validate Auth-issued access tokens locally through the configured issuer, audience and Auth JWKS. No domain request performs a synchronous identity lookup merely to validate a bearer.

## Local credentials

New local passwords are encoded with Argon2id through Spring Security's Password4j-backed encoder.

Runtime parameters are configurable with:

- VELLUM_AUTH_ARGON2_MEMORY_KIB
- VELLUM_AUTH_ARGON2_ITERATIONS
- VELLUM_AUTH_ARGON2_PARALLELISM

The application fails configuration below the enforced floor of 19 MiB memory, 2 iterations and parallelism 1. New registrations do not encode with BCrypt. There is no password migration path because the project has no production users or production credential data to preserve.

## OAuth/OIDC contract

The Authorization Server exposes the standard Spring Authorization Server endpoints, including discovery, authorization, token and JWKS endpoints.

Access tokens:

- are signed with RS256;
- use `typ=at+jwt`;
- carry the stable UUIDv7 identity as `sub`;
- require the configured issuer and API audience;
- contain `client_id`, `jti`, temporal claims and persisted identity roles;
- are distinct from OIDC ID Tokens.

The built-in public PKCE client is optional. The server can start without a SPA/client registration. Test environments may explicitly enable the client used by the PKCE regression harness. No frontend is created or required by the Auth runtime.

## Registration and profile provisioning

When identity registration is enabled, `POST /v1/identity/register` validates the request, creates a pending Auth identity, hashes the local credential with Argon2id and generates the UUIDv7 once.

Auth then invokes User's private idempotent profile-provisioning endpoint with only UUID, display name and email. The identity becomes active only after the profile is confirmed. Provisioning failures remain durable as pending identities and are retried with the original UUID.

Auth-to-User provisioning uses the existing private shared-secret boundary and private networking. Client Credentials was deliberately not introduced just to satisfy this integration.

## Google federation

Google is an upstream OIDC provider; VellumHub Auth remains the issuer seen by the APIs.

External bindings are keyed by verified issuer + subject. Email is profile data and is not used as an automatic account-link key. Google ID Tokens are never accepted as VellumHub API bearer tokens.

Live Google credentials and provider callbacks remain environment-level validation; CI uses controlled provider fixtures for the protocol and session lifecycle.

## Sessions and logout

Spring Session JDBC persists browser sessions. The current policy keeps the existing idle and absolute session limits. Logout invalidates the browser session; already-issued API access tokens remain valid until their expiry.

The Authorization Server and session lifecycle tests cover persistence, logout, expiry and reauthorization. The local loopback harness is test-only and is not evidence of HTTPS/proxy behavior in a deployed environment.

## Signing keys and runtime

The private signing key is mounted only into Auth and is never committed. Auth fails startup when required signing material is missing or invalid. Public keys are exposed through the Auth JWKS endpoint.

Default runtime topology:

- `auth-postgres` stores Auth/OAuth/session state;
- `auth-service` is the canonical issuer;
- Resource Servers use the Auth JWKS;
- Prometheus scrapes the Auth management endpoint;
- Alloy collects Auth logs.

`infra/compose/tests/auth-poc.yaml`, `infra/compose/oidc-local.yaml` and `infra/compose/google-federation.yaml` are regression harnesses only, not activation switches for the authentication architecture.

## Local Google federation

Diagram: [Google sign-in and link sequence](../../docs/architecture/flows/google-federation.md). Register the loopback redirect URI `http://127.0.0.1:9000/login/oauth2/code/google` on a Google *Web application* client and supply secrets outside Git:

```sh
export VELLUM_GOOGLE_CLIENT_ID='<Google OIDC client ID>'
export VELLUM_GOOGLE_CLIENT_SECRET='<Google OIDC client secret>'
export VELLUM_AUTH_DB_PASSWORD='<local DB password>'
export VELLUM_INTERNAL_PROVISION_KEY='<random 32+ character key>'
sh infra/scripts/security/generate_auth_poc_keys.sh
docker compose -f compose.yaml -f infra/compose/oidc-local.yaml -f infra/compose/google-federation.yaml --profile oidc-lote2 up --build
```

Never use loopback HTTP settings on a shared host. Behind HTTPS, verify forwarded-header trust, issuer/callback equality and Secure/HttpOnly/SameSite cookies.

## Alerts

Prometheus scrapes Auth on the private management port 9001 (`infra/observability/prometheus/rules/auth-oidc-alerts.yml`).

- **AuthOidcUnavailable:** check Auth health, database connectivity and recent deploys.
- **AuthAuthenticationThrottled:** the shared PostgreSQL window allows 20 attempts per minute per remote address (`vellum.auth.attempts-per-minute`). Tell abuse apart from shared NAT before changing it; it uses the servlet remote address, never an untrusted forwarded header.
- **AuthFederationResolutionFailures:** check Google reachability, client ID/issuer configuration and User provisioning. The counter only covers resolution after protocol validation.

For a compromised account, deactivate the Auth identity; its sessions end immediately, while issued JWTs remain valid until `exp` (no token blacklist).

## Verification

Primary checks are:

```sh
mvn -B -ntp -pl services/auth-service -am clean verify
bash infra/scripts/security/run_oidc_lote2_e2e.sh
```

The full pull-request gate also runs the reactor, architecture fitness functions, Postman/OpenAPI contract checks, CodeQL, image validation and the federated/session suites.

Do not infer production rollout from CI. Live Google credentials, HTTPS/proxy/cookie behavior and any real-client deployment remain environment-specific evidence.

## OAuth capabilities

Domain HTTP operations require an Auth-issued human access token with an explicit
scope and the required USER/ADMIN role. ADMIN never implies scopes, and machine
grants cannot impersonate a human. See [the endpoint matrix, configuration and
rollback guide](../../docs/SECURITY_SCOPES_RBAC.md). OpenAPI documents each scope.
Fresh tokens must request the relevant capabilities; `openid profile` alone is
insufficient. Ownership rules remain separate.

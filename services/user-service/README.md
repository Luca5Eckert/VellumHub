# User Service

[Architecture card · VH-USR](../../docs/architecture/services/user.md) · [C4 containers](../../docs/architecture/diagrams/c4-containers.md) · [All service codes](../../docs/architecture/services/README.md).

**Current contract:** User owns profiles and preferences, **not** login, credentials, signing keys or OAuth. Auth is the canonical issuer; earlier User-as-issuer notes are historical. No production deployment is implied by the repository state.

[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.x-green)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-blue)](https://www.postgresql.org/)
[![Kafka](https://img.shields.io/badge/Kafka-producer-black)](https://kafka.apache.org/)
[![OpenAPI](https://img.shields.io/badge/OpenAPI-Springdoc-brightgreen)](https://springdoc.org/)

The User Service owns profiles, preferences and cold-start facts; Auth owns identity and authentication.

User is authoritative for profile data only. It is not authoritative for credentials, books, ratings, reactions or recommendation rankings.

## Why this service exists

- Store profiles and preferences keyed by Auth's canonical user UUID.
- Provision profiles idempotently through a private Auth-to-User call.
- Publish `created-user-preference` for recommendation cold start.
- Verify Auth-issued access tokens; never sign one.

## Ownership and boundaries

| Domain | Canonical owner |
| --- | --- |
| OAuth/OIDC, Google, credentials, sessions and JWT issuance | [VH-AUT Auth](../../docs/architecture/services/auth.md) |
| Profiles and preferences | VH-USR (this service) |
| Profile database | `user_db` |
| Reading, book catalog, feedback and ranking | Catalog, Engagement and Recommendation |

## Domain modules

| Module | Responsibility |
| --- | --- |
| `user` | Profile CRUD, ownership, private idempotent provisioning |
| `user_preference` | Genre preferences, persistence, preference Kafka fact |

## HTTP API surface

The current [UserController](src/main/java/com/vellumhub/user_service/module/user/application/controller/UserController.java) exposes:

```http
GET    /users/me
GET    /users/{id}
GET    /users               (ADMIN)
PUT    /users/{id}          (ADMIN)
DELETE /users/{id}          (ADMIN)
```

Gateway prefix: `/api/v1/users/**`. **There is no User-backed `/api/v1/auth/**` route** in the current Gateway configuration. Register/login/oauth with Auth directly.

The [profile-provisioning adapter](src/main/java/com/vellumhub/user_service/module/user/infrastructure/provisioning/ProfileProvisioningController.java) can expose `POST /internal/identity/profiles` when configured. It validates the private Auth shared secret, the UUIDv7 subject and the profile payload, and recognizes idempotent retries. The private route is **not forwarded by Gateway**.

## Event contract and security

| Topic | Producer | Consumer |
| --- | --- | --- |
| `created-user-preference` | User preferences | Recommendation cold-start projection |

User verifies signed Auth access tokens using the Auth JWKS and enforces per-user ownership plus scopes/roles. It contains no active `auth` module that signs tokens and does not store credentials in User PostgreSQL.

## Data ownership

Primary database: `user_db` for profiles and preferences. Other services consume the preference fact asynchronously; they do not join `user_db`. Auth stores canonical identities, OAuth grants and passwords in a separate database.


## Observability and API Docs

| Resource | Path |
|---|---|
| Swagger UI | `/swagger-ui/index.html` |
| OpenAPI JSON | `/v3/api-docs` |
| Health | `/actuator/health` |
| Metrics | `/actuator/metrics` |
| Prometheus | `/actuator/prometheus` |

## Run Locally

Run the following commands from the **repository root**, where the single Maven Wrapper and reactor POM live.

Standalone:

```bash
./mvnw -pl services/user-service -am -DskipTests install
./mvnw -f services/user-service/pom.xml spring-boot:run
```

Windows PowerShell:

```powershell
.\mvnw.cmd -pl services/user-service -am -DskipTests install
.\mvnw.cmd -f services/user-service/pom.xml spring-boot:run
```

With Docker Compose from the repository root:

```bash
docker compose up -d user-service postgres-user kafka
```

## Verify

```powershell
.\mvnw.cmd -pl services/user-service -am test
```

For gateway routes and full system flow, see the [root README](../../README.md).

## SEC-01 HTTP security baseline

The API remains JWT bearer-based; issue #163 migrates the signature to RS256 with independent public-key validation on this branch (coordinated cutover required). Service security continues to validate tokens independently of the Gateway. Swagger/OpenAPI is opt-in through `VELLUM_DOCS_ENABLED=true` in local Compose; default deployment configuration denies documentation. Health and Prometheus remain unauthenticated at the service HTTP layer for **internal** probe/scrape traffic, so network and Ingress must keep these ports private. CORS uses explicit `CORS_ALLOWED_ORIGINS` and preserves credentialed browser requests for compatibility. JWT Bearer remains the authenticator; any future cookie-session authentication requires separate CSRF review. See [SEC-01 HTTP boundary contract](../../docs/SECURITY_HTTP_BOUNDARY.md) for policy matrix, tests and rollback.

POST /auth/google is intentionally public and continues to validate the Google ID token in the existing verification adapter. Account linking by issuer/subject is tracked separately as SEC-02 (#303); this issue does not claim to address takeover risk caused by email-based linkage.

## SEC-02: Google identity binding and legacy account recovery

The Google ID token is validated for signature, issuer, audience, expiration, verified email and non-empty subject. Identity ownership is **never** determined by matching email. Verified `issuer + subject` is unique and bound to an immutable local user UUID. Existing password-based accounts must use `POST /auth/google/link` with Bearer JWT, `externalToken` and `currentPassword`; unlink with `DELETE /auth/google/link` and the current password. Both return 204 when successful. Conflicts return 409, insufficient account proof 403.

New Google users have `password=NULL`. Passwordless users cannot unlink their only sign-in method; users whose previous Google identity cannot be established through a linked subject require authenticated local-password association or manual verified recovery. No automatic email-based backfill is performed.

Flyway `V2__external_identity_binding.sql` creates `external_identities` and `external_identity_audit` and makes local passwords nullable. PostgreSQL advisory transaction locks serialize first-login/link/unlink across replicas. H2 local profile lacks distributed locks and must not be used to claim concurrency guarantees. Read the [SEC-02 runbook](../../docs/SECURITY_GOOGLE_IDENTITY.md) before deployment.


### Security JWT/JWKS transition (#304, #305)

Gateway and every domain Resource Server validate only RS256 access tokens issued by `auth-service`, using `JWT_JWKS_URI=http://auth-service:9000/oauth2/jwks` inside the runtime network plus the configured issuer/audience. Unknown `kid`, invalid signature/type/issuer/audience and malformed identity claims fail closed. Only Auth owns signing material; User, Catalog, Engagement, Recommendation and Gateway hold no private JWT key and perform no per-request identity lookup. Read [docs/SECURITY_JWKS_RFC9068.md](../../docs/SECURITY_JWKS_RFC9068.md) for configuration, key rotation, outage behavior and safe cutover. Not yet deployed.

## SEC-06 — Auth-owned profile provisioning

Opt-in `VELLUM_IDENTITY_PROVISIONING_ENABLED=true` enables `POST /internal/identity/profiles`; only the Auth workload with a 32+-character `VELLUM_INTERNAL_PROVISION_KEY` may invoke it on the **internal network**. Gateway does not proxy the route. The command accepts a UUIDv7 `userId`, name and email, returns 201 for creation, 204 for exact replay and 409 on collision. DB migration `V3__auth_owned_profile.sql` makes password nullable for accounts with credentials **owned only by Auth**, with no hash replicated. User is profile-only: legacy login/JWT/JWKS and credential ownership have been removed; authentication is exclusively owned by Auth. Configure and validate mTLS/workload identity before production.

[Runbook](../../docs/SECURITY_OIDC_LOTE2.md) · [#307](https://github.com/Luca5Eckert/VellumHub/issues/307).


## Google identity ownership during Lote 3

Google federation and external-identity bindings are owned only by Auth. User retains profile ownership and the private idempotent provisioning command; the legacy Google login/link/unlink handlers are no longer part of the User runtime. The Lote 3 Compose file is retained only as a regression harness. See [Lote 3 runbook](../../docs/SECURITY_OIDC_LOTE3.md) for setup and rollback limits.

## SEC-10 — explicit OAuth capabilities

Domain HTTP operations require an Auth-issued human access token with an explicit
scope and the required USER/ADMIN role. ADMIN never implies scopes, and machine
grants cannot impersonate a human. See [the endpoint matrix, configuration and
rollback guide](../../docs/SECURITY_SCOPES_RBAC.md). OpenAPI documents each scope.
Fresh tokens must request the relevant capabilities; `openid profile` alone is
insufficient. Ownership rules remain separate.


### SEC-11 profile ownership

`GET /users/me` resolves the JWT subject. `GET /users/{id}` exposes private profile data only to that subject; other IDs return a generic 404. `GET /users` requires ADMIN and `user:read`. See [SEC-11](../../docs/SECURITY_OWNERSHIP_SEC11.md).


### SEC-14 Kafka producer identity

User publishes `created-user-preference` using the dedicated `user_service`
broker identity. Its manually configured `ProducerFactory` inherits the
Spring Kafka SASL/TLS properties, not just `bootstrap.servers`.
See [SEC-14](../../docs/KAFKA_SERVICE_IDENTITIES_SEC14.md).

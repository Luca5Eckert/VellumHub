# User Service

**SEC-16 migration context:** This branch makes User profile-only and moves authentication, credentials and JWT signing exclusively to Auth. The earlier merged OIDC work (#334 and prerequisites) verified an opt-in integration path before this backend cutover. PR #336 remains subject to validation and has not established production deployment; no real frontend or user-data migration is needed.

[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.x-green)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-blue)](https://www.postgresql.org/)
[![Kafka](https://img.shields.io/badge/Kafka-producer-black)](https://kafka.apache.org/)
[![OpenAPI](https://img.shields.io/badge/OpenAPI-Springdoc-brightgreen)](https://springdoc.org/)

The User Service exists to own identity, authentication, user profiles, and the first preference signal used to bootstrap recommendations.

It is the system of record for who the user is. It is not the system of record for books, ratings, reactions, or recommendation rankings.

## Why This Service Exists

- Register users and authenticate them with email/password or Google token flow.
- Issue JWTs consumed by the gateway and downstream services.
- Own user records and user preference data.
- Publish `created-user-preference` so recommendation can build a cold-start profile vector.

## What It Owns

| Concern | Owned here |
|---|---|
| Authentication | Register, login, Google login, token issuing |
| User profile | User CRUD and `/users/me` lookup |
| User preferences | Genres and free-text `about` seed data |
| Recommendation bootstrap | Kafka publication of user preference event |
| Database | `user_db` |

## What It Does Not Own

- Book catalog data.
- Ratings, reactions, or reading progress.
- Recommendation ranking or vector search.
- Gateway-level routing or rate limiting.

## Domain Modules

| Module | Responsibility |
|---|---|
| `auth` | Register/login flows, Google token verification, JWT generation |
| `user` | User CRUD, user lookup, and authenticated profile endpoint |
| `user_preference` | Preference aggregate, persistence, and Kafka publication |

## HTTP API Surface

Base paths:

- `/auth`
- `/users`

Representative endpoints:

```http
POST   /auth/register
POST   /auth/login
POST   /auth/google
POST   /auth/google/link    (JWT + current local password + Google ID token)
DELETE /auth/google/link    (JWT + current local password)

POST   /users
GET    /users
GET    /users/{id}
PUT    /users/{id}
DELETE /users/{id}
GET    /users/me
```

Through the gateway, these routes are exposed under:

```http
/api/v1/auth/**
/api/v1/users/**
```

## Event Contract

Produced topic:

| Topic | Trigger | Consumer |
|---|---|---|
| `created-user-preference` | User preference creation/update during onboarding | `recommendation-service` |

Recommendation consumes this topic to create or adjust `user_profiles` for cold-start recommendations. The shared payload and topic contract live in `lib/kafka-contracts`.

## Authentication Model

The User Service is the **only JWT signer** in issue #163. It reads an RSA private key (unencrypted PKCS#8 PEM) and, as a resource server, reads the matching public key (X.509 PEM). The gateway and business services receive **only the public key**. All JWTs use RS256; HS256 JWTs are rejected after cutover.

| Setting | Meaning |
|---|---|
| `JWT_PRIVATE_KEY_LOCATION` | Spring Resource URI of the private RSA signing key; User Service only |
| `JWT_PUBLIC_KEY_LOCATION` | Spring Resource URI of the public RSA verification key; all five services |
| `JWT_EXPIRATION_MS` | JWT lifetime in milliseconds (unchanged in #163) |
| `GOOGLE_CLIENT_ID` | Google ID token audience/client ID (unchanged) |

In Docker Compose, configure host paths `JWT_PRIVATE_KEY_PATH` and `JWT_PUBLIC_KEY_PATH` in `.env`; read-only mounts map them into the containers. Never commit the private key, place it in a ConfigMap, or provide it to other services. For key generation, Kubernetes provisioning, coordinated rollout, rollback, and troubleshooting, see [RS256 operations](../../docs/SECURITY_RS256.md).

## Data Ownership

Primary database: `user_db`.

Main persisted concepts:

- users;
- roles;
- authentication-facing user details;
- user preference data used by recommendation bootstrap.

Other services should treat this service as the source of truth for user identity and consume preference bootstrap data through Kafka, not through shared tables.

## Observability and API Docs

| Resource | Path |
|---|---|
| Swagger UI | `/swagger-ui/index.html` |
| OpenAPI JSON | `/v3/api-docs` |
| Health | `/actuator/health` |
| Metrics | `/actuator/metrics` |
| Prometheus | `/actuator/prometheus` |

## Run Locally

Standalone:

```bash
cd user-service
./mvnw spring-boot:run
```

Windows PowerShell:

```powershell
cd user-service
.\mvnw.cmd spring-boot:run
```

With Docker Compose from the repository root:

```bash
docker-compose up -d user-service postgres-user kafka
```

## Verify

```powershell
cd user-service
.\mvnw.cmd test
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

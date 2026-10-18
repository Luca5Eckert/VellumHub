# User Service

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

| Setting | Meaning |
|---|---|
| `JWT_KEY` | Base64-encoded signing/verification secret |
| `JWT_EXPIRATION_MS` | Token expiration in milliseconds |
| `GOOGLE_CLIENT_ID` | Google token audience/client id |

Generate a local Base64 key:

```bash
openssl rand -base64 32
```

Windows PowerShell:

```powershell
$bytes = New-Object byte[] 32; $rng = New-Object System.Security.Cryptography.RNGCryptoServiceProvider; $rng.GetBytes($bytes); [Convert]::ToBase64String($bytes); $rng.Dispose()
```

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

The API remains JWT bearer-based (HS256 until issue #163). Service security continues to validate tokens independently of the Gateway. Swagger/OpenAPI is opt-in through `VELLUM_DOCS_ENABLED=true` in local Compose; default deployment configuration denies documentation. Health and Prometheus remain unauthenticated at the service HTTP layer for **internal** probe/scrape traffic, so network and Ingress must keep these ports private. CORS uses explicit `CORS_ALLOWED_ORIGINS` and preserves credentialed browser requests for compatibility. JWT Bearer remains the authenticator; any future cookie-session authentication requires separate CSRF review. See [SEC-01 HTTP boundary contract](../../docs/SECURITY_HTTP_BOUNDARY.md) for policy matrix, tests and rollback.

POST /auth/google is intentionally public and continues to validate the Google ID token in the existing verification adapter. Account linking by issuer/subject is tracked separately as SEC-02 (#303); this issue does not claim to address takeover risk caused by email-based linkage.

## SEC-02: Google identity binding and legacy account recovery

The Google ID token is validated for signature, issuer, audience, expiration, verified email and non-empty subject. Identity ownership is **never** determined by matching email. Verified `issuer + subject` is unique and bound to an immutable local user UUID. Existing password-based accounts must use `POST /auth/google/link` with Bearer JWT, `externalToken` and `currentPassword`; unlink with `DELETE /auth/google/link` and the current password. Both return 204 when successful. Conflicts return 409, insufficient account proof 403.

New Google users have `password=NULL`. Passwordless users cannot unlink their only sign-in method; users whose previous Google identity cannot be established through a linked subject require authenticated local-password association or manual verified recovery. No automatic email-based backfill is performed.

Flyway `V2__external_identity_binding.sql` creates `external_identities` and `external_identity_audit` and makes local passwords nullable. PostgreSQL advisory transaction locks serialize first-login/link/unlink across replicas. H2 local profile lacks distributed locks and must not be used to claim concurrency guarantees. Read the [SEC-02 runbook](../../docs/SECURITY_GOOGLE_IDENTITY.md) before deployment.

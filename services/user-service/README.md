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
- Publish `create_user_preference` so recommendation can build a cold-start profile vector.

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
| `create_user_preference` | User preference creation/update during onboarding | `recommendation-service` |

The topic intentionally uses `snake_case` to preserve the current producer/consumer contract. Recommendation consumes it to create or adjust `user_profiles` for cold-start recommendations.

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

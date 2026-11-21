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

## Security

Auth is the only issuer. This service validates RS256 `at+jwt` access tokens locally against `JWT_JWKS_URI` (issuer, audience, `typ`, `kid`; `JWT_CLOCK_SKEW_SECONDS` defaults to 30) and never calls Auth or User per request. Each operation needs an explicit OAuth scope plus the USER/ADMIN role ([scope matrix](../../docs/SECURITY_SCOPES_RBAC.md)); ADMIN never implies scopes. Swagger is opt-in (`VELLUM_DOCS_ENABLED`), and health/Prometheus endpoints must stay on the private network. Request path: [authorization sequence](../../docs/architecture/flows/api-request-authorization.md).

- **Profile ownership:** `GET /users/me` resolves the JWT `sub`; `GET /users/{id}` returns private data only to that subject and a generic 404 otherwise; `GET /users` requires ADMIN and `user:read`.
- **Provisioning:** `POST /internal/identity/profiles` (enabled by `VELLUM_IDENTITY_PROVISIONING_ENABLED=true`) accepts only Auth with a 32+ character `VELLUM_INTERNAL_PROVISION_KEY` on the internal network; the Gateway denies the path. 201 creates, 204 is an exact replay, 409 is a collision ([identity states](../../docs/architecture/states/auth-identity-lifecycle.md)).
- **No credentials here:** login, passwords, Google bindings and signing keys belong to Auth; User Flyway V4 removed the former credential tables.
- **Kafka identity:** `user_service` produces `created-user-preference` only ([Kafka identities](../../docs/KAFKA_SERVICE_IDENTITIES.md)).

### Preferências de gênero

| Método e rota no Gateway | Escopo | Resultado |
| --- | --- | --- |
| `GET /api/v1/users/genres` | `user:read` | Nomes canônicos dos gêneros de onboarding do Catalog |
| `GET /api/v1/users/me/preferences` | `user:read` | Preferências do titular ou uma lista vazia |
| `PUT /api/v1/users/me/preferences` | `user:write` | Substitui os gêneros do titular e retorna a seleção |

O corpo de `PUT` é um objeto como `{"genres":["Fantasy","Science Fiction"]}`.
A lista deve ter de 1 a 10 nomes distintos retornados por `GET /users/genres`.
A identidade vem do `sub` autenticado. O Catalog mantém a taxonomia padrão
em um contrato compartilhado com o User para evitar divergência e chamadas
síncronas entre os serviços. Cada substituição grava a próxima revisão e
um snapshot no outbox dentro da mesma transação do PostgreSQL. O relay usa
`created-user-preference` com a chave do usuário, remove o registro após o
ACK do Kafka e tenta novamente em caso de falha, respeitando a ordem de
revisão por usuário. A entrega permanece ao menos uma vez. O evento JSON
mantém o contrato atual do Recommendation durante a transição Avro da #374.

# 👤 User Service

[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.x-green)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-blue)](https://www.postgresql.org/)
[![Kafka](https://img.shields.io/badge/Kafka-Event--Driven-black)](https://kafka.apache.org/)
[![Swagger](https://img.shields.io/badge/OpenAPI-3.0-brightgreen)](https://swagger.io/specification/)

The **User Service** is the identity and access boundary of VellumHub, covering registration, login, and user management.

It also emits user preference seed events used by Recommendation Service in cold-start profile initialization.

---

## Table of Contents

- [Service Role in VellumHub](#service-role-in-vellumhub)
- [Domain Modules](#domain-modules)
- [HTTP API Surface](#http-api-surface)
- [Authentication Model](#authentication-model)
- [Event Contract](#event-contract)
- [Data Ownership](#data-ownership)
- [Observability and API Docs](#observability-and-api-docs)
- [Quick Start](#quick-start)

---

## Service Role in VellumHub

- User identity source of truth
- JWT issuing authority for downstream authorization
- Preference bootstrap publisher for recommendation cold-start

---

## Domain Modules

| Module | Responsibility |
|---|---|
| `auth` | Register/login flows (email+password and Google token flow) |
| `user` | User CRUD, lookup, and `/users/me` profile endpoint |
| `user_preference` | Preference aggregate and Kafka publication |

---

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

---

## Authentication Model

- JWT-based stateless authentication
- Spring Security authorization by role (`ADMIN` and authenticated user flows)
- Google login endpoint (`/auth/google`) integrated into auth service layer

Config keys:

- `JWT_KEY`
- `JWT_EXPIRATION`
- `GOOGLE_CLIENT_ID`

---

## Event Contract

Produced topic:

- `create_user_preference`

Consumer impact:

- Recommendation Service consumes this event to create/adjust `user_profiles` vectors for cold-start recommendations.

---

## Data Ownership

Primary DB: `user_db`.

Main domains include user records (`tb_users`) and user preference data handled by `user_preference` module.

---

## Observability and API Docs

- Actuator: `/actuator/health`, `/actuator/metrics`, `/actuator/prometheus`
- Swagger UI: `/swagger-ui/index.html`
- OpenAPI: `/v3/api-docs`

---

## Quick Start

### Run locally

```bash
cd user-service
./mvnw spring-boot:run
```

### Run via Docker Compose

```bash
docker-compose up -d user-service
```

### Configuration highlights

- `SPRING_DATASOURCE_*`
- `KAFKA_BOOTSTRAP_SERVERS`
- `JWT_KEY`, `JWT_EXPIRATION`
- `GOOGLE_CLIENT_ID`

---

See [root README](../README.md) for gateway and end-to-end flow.

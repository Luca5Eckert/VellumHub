# 👤 User Service

[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.x%20%2F%204.0.x-green)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-blue)](https://www.postgresql.org/)
[![Kafka](https://img.shields.io/badge/Kafka-Event--Driven-black)](https://kafka.apache.org/)
[![Swagger](https://img.shields.io/badge/OpenAPI-3.0-brightgreen)](https://swagger.io/specification/)

The **User Service** is the identity boundary of VellumHub: registration, authentication, account management, and user preference bootstrap.

It issues JWTs used across the platform and publishes preference seed events for cold-start recommendation initialization.

---

## Table of Contents

- [Service Role in VellumHub](#service-role-in-vellumhub)
- [Module Map](#module-map)
- [HTTP API Surface](#http-api-surface)
- [Event Contract](#event-contract)
- [Data Ownership](#data-ownership)
- [Security and Observability](#security-and-observability)
- [Quick Start](#quick-start)

---

## Service Role in VellumHub

- Identity provider for microservice authorization
- JWT token issuance and validation primitives
- User preference event source for recommendation cold-start

---

## Module Map

| Module | Responsibility |
|---|---|
| `auth` | Register/login flows (email+password, Google login) |
| `user` | User CRUD, profile endpoints, role-based administration |
| `user_preference` | Preference model and `create_user_preference` event publication |

---

## HTTP API Surface

Base paths exposed by this service:

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

## Event Contract

Produced topic:

- `create_user_preference`

This event is consumed by Recommendation Service to initialize/update user profile vectors in the cold-start path.

---

## Data Ownership

Main database: `user_db`.

Core entities include:

- `tb_users`
- user preference aggregate (`user_preference` module)

This service is authoritative for account identity and user-level preference declarations.

---

## Security and Observability

- Spring Security + JWT-based authentication
- Role-aware authorization (`USER`/`ADMIN`)
- Password validation and secure hashing
- Actuator enabled: `/actuator/health`, `/actuator/metrics`, `/actuator/prometheus`
- Swagger/OpenAPI: `/swagger-ui/index.html`, `/v3/api-docs`

---

## Quick Start

### Run locally

```bash
cd user-service
./mvnw spring-boot:run
```

### Run via Docker Compose (from repo root)

```bash
docker-compose up -d user-service
```

### Default local access

- Service: `http://localhost:8084`
- Swagger UI: `http://localhost:8084/swagger-ui/index.html`
- OpenAPI: `http://localhost:8084/v3/api-docs`

---

For end-to-end platform architecture (Gateway, ECST, recommendation pipeline), see [VellumHub root README](../README.md).

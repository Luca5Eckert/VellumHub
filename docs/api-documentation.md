# API Documentation

VellumHub exposes one Swagger UI through the gateway while keeping each domain service responsible for generating its own OpenAPI contract.

This design centralizes discovery without creating a shared or merged API schema. The gateway only proxies the service-owned `/v3/api-docs` documents.

## Central access

With the default Docker Compose stack running, open:

```text
http://localhost:8080/docs
```

The Swagger UI selector exposes:

- User Service
- Catalog Service
- Engagement Service
- Recommendation Service

The gateway resolves each option through a stable documentation route:

| Service | Gateway OpenAPI URL | Internal target |
|---|---|---|
| User | `/docs/user/v3/api-docs` | `${USER_SERVICE_URL}/v3/api-docs` |
| Catalog | `/docs/catalog/v3/api-docs` | `${CATALOG_SERVICE_URL}/v3/api-docs` |
| Engagement | `/docs/engagement/v3/api-docs` | `${ENGAGEMENT_SERVICE_URL}/v3/api-docs` |
| Recommendation | `/docs/recommendation/v3/api-docs` | `${RECOMMENDATION_SERVICE_URL}/v3/api-docs` |

The same service URL variables used by normal gateway routing are reused for documentation routing. There is no second discovery mechanism to configure.

## Postman

The repository also versions a Postman collection for manual exploration and authenticated cross-service workflows:

- `postman/VellumHub.postman_collection.json`
- `postman/VellumHub.local.postman_environment.json`
- `postman/README.md`

Postman is not an independent API specification. `postman/generate.py` reads the four service-owned OpenAPI documents through the gateway and deterministically regenerates the per-service request folders. Only orchestration that OpenAPI cannot represent well, such as the login-to-recommendation workflow and state capture between requests, is maintained manually in `postman/workflows.json`.

The local environment template contains no credentials or real tokens. The login workflow stores the returned JWT in the local `token` variable so protected requests can inherit Bearer authentication without repeatedly copying tokens.

After changing an HTTP contract, start the local stack and regenerate:

```bash
python postman/generate.py
```

To verify the committed artifact without rewriting it:

```bash
python postman/generate.py --check
```

See [`postman/README.md`](../postman/README.md) for import instructions, variables, workflow behavior, regeneration rules, and secret-handling guidance.

## Local workflow

From the repository root:

```bash
docker-compose up -d
```

Then:

1. Open `http://localhost:8080/docs`.
2. Select a service in the Swagger UI definition selector.
3. The browser requests `/docs/{service}/v3/api-docs` from the gateway.
4. Spring Cloud Gateway removes `/docs/{service}` and forwards `/v3/api-docs` to that service.
5. The selected service generates and returns its own OpenAPI document.

Only the gateway is published to the host in the default Compose topology. The central UI therefore avoids exposing additional service ports solely for documentation.

## Direct service access

When a domain service is executed directly and its port is reachable, its documentation remains available independently:

| Resource | Path |
|---|---|
| Swagger UI | `/swagger-ui/index.html` |
| OpenAPI JSON | `/v3/api-docs` |

Centralization does not change the service-local contract or remove these endpoints.

## Ownership model

Each domain service owns:

- controller annotations and API descriptions;
- schemas derived from its request/response models;
- its `/v3/api-docs` document;
- its service-local Swagger UI.

The gateway owns only:

- the `/docs` discovery UI;
- documentation proxy routes;
- public access policy for documentation paths.

The Postman generator consumes those contracts but does not own endpoint definitions. If a generated request is wrong, the corresponding OpenAPI contract should be corrected first and the collection regenerated.

The gateway does not merge contracts, copy JSON files, or depend on domain DTOs. A service can evolve its API documentation without modifying another service.

## Availability behavior

The central Swagger UI can remain available when one domain service is unavailable. In that case, only the selected service's OpenAPI document fails to load.

This is intentional. The gateway does not cache a potentially stale copy of a service contract.

## Security

The gateway permits unauthenticated access only to the documentation surface required by Swagger UI:

- `/docs`
- `/docs/**`
- `/swagger-ui/**`
- `/v3/api-docs/swagger-config`

Existing `/api/v1/**` authentication behavior is unchanged.

Domain services continue applying their existing security configuration and independently expose their own Swagger/OpenAPI endpoints for development and inspection.

The committed Postman environment keeps `password` and `token` empty. CI rejects non-empty committed values for those variables and JWT-like values in versioned Postman artifacts.

## Springdoc compatibility

The repository currently contains both Spring Boot 3.5.x and Spring Boot 4.0.x services. Springdoc versions follow the corresponding compatibility line:

| Service | Spring Boot | Springdoc |
|---|---|---|
| Gateway | 4.0.6 | 3.1.0 WebFlux UI |
| User | 4.0.6 | 3.1.0 WebMVC UI |
| Catalog | 3.5.14 | 2.8.5 WebMVC UI |
| Engagement | 3.5.14 | 2.8.5 WebMVC UI |
| Recommendation | 4.0.6 | 3.1.0 WebMVC UI |

The version alignment is documentation infrastructure only; it does not change REST endpoint semantics.

## Validation

`services/gateway-service/src/test/java/com/vellumhub/gateway_service/config/OpenApiDocumentationConfigurationTest.java` validates that all four service names, gateway aliases, and route IDs remain present in `application.yml`.

The Postman validation workflow adds two layers:

1. static validation compiles the generator, parses all versioned JSON artifacts, and checks secret hygiene;
2. live contract validation starts the Docker Compose stack, waits for all four gateway OpenAPI documents, regenerates the collection, and fails if the committed artifact differs.

The drift check is intentionally read-only. CI never rewrites the collection or pushes commits. Changes to the Postman artifacts, service application sources, relevant service POMs, Docker Compose inputs, or the gateway route configuration trigger the validation, so an API change cannot silently leave the versioned collection stale.

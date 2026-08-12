# VellumHub operational baseline

Each application service is deployed as an immutable Java 21 container image. Images are built from the repository root so services that consume `kafka-contracts` compile against the same source revision as the reactor build.

## Runtime contract

- Application containers listen on port `8080`, run from `/app`, and execute as the `vellumhub` non-root user.
- The OpenTelemetry Java agent is bundled in every image and enabled through `JAVA_TOOL_OPTIONS`.
- The default JVM settings use container-aware percentages (`InitialRAMPercentage=25`, `MaxRAMPercentage=75`) and exit on out-of-memory errors. Override `JAVA_TOOL_OPTIONS` only when an environment has measured resource requirements.
- Spring uses graceful shutdown with a default phase timeout of `30s`; set `SHUTDOWN_TIMEOUT` when a deployment needs a different window.
- `recommendation-service` intentionally uses a Debian runtime image because its embedded embedding model requires `libstdc++`.

## Configuration and health

Production configuration is external. `JWT_KEY`, datasource credentials, Kafka bootstrap servers, user-service Google client ID, and CORS origins must be supplied by the deployment environment. Flyway owns schema evolution in production and Hibernate validates rather than changes the schema.

Every service exposes these unauthenticated operational endpoints:

- `/actuator/health`
- `/actuator/health/liveness`
- `/actuator/health/readiness`
- `/actuator/prometheus`

Liveness contains only Spring's process state and is safe for restart decisions. Readiness includes the process state plus the service's required runtime dependencies: `db` and `kafka` for the domain services, and `redis` for the gateway. A dependency outage therefore removes an instance from traffic without causing a liveness restart loop.

## Smoke checks

`infra/scripts/smoke-tests/smoke-http.sh` is runtime-neutral. Supply reachable URLs to use it against Compose, a test environment, or Kubernetes:

```bash
GATEWAY_URL=http://gateway.example \
USER_SERVICE_URL=http://user.example \
CATALOG_SERVICE_URL=http://catalog.example \
ENGAGEMENT_SERVICE_URL=http://engagement.example \
RECOMMENDATION_SERVICE_URL=http://recommendation.example \
bash infra/scripts/smoke-tests/smoke-http.sh
```

`infra/scripts/smoke-tests/test-compose-runtime.sh` is the Compose adapter. It uses Compose service names, validates internal liveness/readiness endpoints, and checks Kafka without relying on container IDs or `docker inspect`.

## Local reproducible verification

```bash
mvn -B -ntp clean verify
docker compose build
docker compose up -d
docker compose ps
bash infra/scripts/smoke-tests/test-compose-runtime.sh
docker compose down
```

Use an isolated Compose project name when running disposable validation stacks so application data in another local stack is not affected.

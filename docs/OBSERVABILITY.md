# Observability Stack

VellumHub ships a local observability profile for development. It collects application metrics, container logs, and OTLP traces without depending on external infrastructure.

## Start The Stack

Render the Compose model before starting it:

```bash
docker compose --profile observability config

```

Start the default application stack plus observability services:

```bash
docker compose --profile observability up -d --build
```

Stop the stack:

```bash
docker compose --profile observability down
```

## Local Endpoints

| Component | URL | Notes |
|---|---|---|
| Grafana | `http://localhost:3002` | Login with `admin` / `admin` locally. |
| Prometheus | `http://localhost:9090` | Scrapes the five application services. |
| Loki | `http://localhost:3100` | Receives Docker container logs through Alloy. |
| Tempo | `http://localhost:3200` | Stores traces sent through Alloy OTLP receivers. |
| Alloy | `http://localhost:12345` | Collector UI and pipeline status. |
| Kafka UI | `http://localhost:8090` | Topic and consumer-group inspection. |

Grafana datasources are provisioned from `docker/observability/grafana/provisioning/datasources/datasources.yml`:

- `Prometheus`
- `Loki`
- `Tempo`

## Metrics

Prometheus scrapes these targets on the Docker network:

- `gateway-service:8080/actuator/prometheus`
- `user-service:8080/actuator/prometheus`
- `catalog-service:8080/actuator/prometheus`
- `engagement-service:8080/actuator/prometheus`
- `recommendation-service:8080/actuator/prometheus`

Validate target health in Prometheus:

1. Open `http://localhost:9090/targets`.
2. Confirm the jobs `gateway-service`, `user-service`, `catalog-service`, `engagement-service`, and `recommendation-service` are present.
3. When the application services are healthy, each target should show `UP`.

## Logs

The five Java services emit structured JSON console logs. Alloy discovers Docker containers, keeps only VellumHub application services, extracts low-cardinality labels, and sends logs to Loki.

Loki labels are intentionally limited to:

- `service`
- `level`
- `environment`

Useful Grafana Explore queries:

```logql
{service="gateway-service"}
```

```logql
{service="catalog-service", level="ERROR"}
```

```logql
{environment="local"} |= "DLT"
```

Dead Letter Topic logs include topic names, exception message, and payload byte length. They do not print raw Kafka payloads by default.

## Traces

The five Java service images include the OpenTelemetry Java Agent `v2.28.1`. Docker Compose enables the agent through `JAVA_TOOL_OPTIONS` while preserving the existing heap flags.

Each service sets:

- `OTEL_SERVICE_NAME` to the Docker service name.
- `OTEL_RESOURCE_ATTRIBUTES=service.namespace=vellumhub,deployment.environment=local`.
- `OTEL_EXPORTER_OTLP_ENDPOINT=http://alloy:4318`.
- `OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf`.
- `OTEL_METRICS_EXPORTER=none`.
- `OTEL_LOGS_EXPORTER=none`.
- `OTEL_TRACES_SAMPLER=parentbased_traceidratio`.
- `OTEL_TRACES_SAMPLER_ARG=1.0`.

Metrics and logs export through the OpenTelemetry agent is intentionally disabled. Prometheus/Micrometer remains the metrics path, and structured Docker logs continue to flow through Alloy to Loki.

Services running inside Docker send OTLP to Alloy:

- `http://alloy:4318` for OTLP HTTP
- `alloy:4317` for OTLP gRPC

Alloy forwards traces to Tempo over OTLP gRPC. Grafana provisions Tempo as the `Tempo` datasource.

To generate a minimal gateway span:

```bash
curl http://localhost:8080/actuator/health
```

To generate a gateway-to-service trace, call one of the public gateway routes, for example:

```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"OTel Local","email":"otel-local@example.com","password":"StrongPass123!"}'
```

Then open Grafana Explore at `http://localhost:3002`, select `Tempo`, and search by service name such as `gateway-service` or `catalog-service`. Tempo can also be queried directly:

```bash
curl "http://localhost:3200/api/search?tags=service.name%3Dgateway-service&limit=20"
```

HTTP server/client spans and JDBC spans are expected when the Java agent supports the Spring, Reactor Netty, servlet, JDBC, Hikari, and PostgreSQL versions in the running service. Kafka producer-to-consumer correlation is best-effort with automatic instrumentation; if a Kafka flow appears as separate traces, keep that as a follow-up for manual propagation or targeted Kafka instrumentation.

## Sensitive Data Checks

When validating logs, avoid relying on visual spot checks only. Search representative service logs for common sensitive terms after generating traffic:

```bash
docker compose logs gateway-service user-service catalog-service engagement-service recommendation-service
```

The expected behavior is:

- no complete JWT values;
- no passwords or secrets;
- no raw Kafka DLT payloads;
- event logs identify topic, event type, operation, user/book IDs, and counts rather than full payload objects.

## Configuration Files

| File | Purpose |
|---|---|
| `docker/observability/prometheus/prometheus.yml` | Prometheus scrape jobs. |
| `docker/observability/grafana/provisioning/datasources/datasources.yml` | Grafana datasource provisioning. |
| `docker/observability/loki/loki.yml` | Local single-node Loki storage. |
| `docker/observability/tempo/tempo.yml` | Local Tempo trace storage and OTLP receiver config. |
| `docker/observability/alloy/config.alloy` | Docker log collection, Loki forwarding, and OTLP-to-Tempo forwarding. |

Dashboards, alerts, business metrics, and manual domain spans are intentionally outside this issue.

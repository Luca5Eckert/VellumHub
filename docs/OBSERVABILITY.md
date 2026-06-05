# Observability Stack

VellumHub ships a local observability profile for development. It collects application metrics, container logs, and future OTLP traces without depending on external infrastructure.

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

Tempo is provisioned in Grafana and Alloy includes OTLP receivers that forward traces to Tempo. The Java services are not deeply instrumented in this issue, so `trace_id` and `span_id` appear in logs only when future tracing instrumentation populates MDC or structured log key-value data.

Services running inside Docker can send OTLP to:

- `http://alloy:4318` for OTLP HTTP
- `alloy:4317` for OTLP gRPC

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

Dashboards, alerts, business metrics, and deep tracing instrumentation are intentionally outside this issue.

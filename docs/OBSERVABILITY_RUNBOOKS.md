# Observability Runbooks

These runbooks are initial local-development guides for the VellumHub observability stack. Thresholds are intentionally conservative and should be tuned after real traffic baselines exist.

## Gateway Unavailable Or 5xx High

**Symptom:** `GatewayUnavailable`, `GatewayHigh5xxRate`, or `GatewayHighP95Latency` fires. Users may see failed requests or slow API responses at `localhost:8080`.

**Impact:** The gateway is the public entry point, so a gateway outage or high 5xx ratio can affect every user-facing workflow.

**Look First:** Open `VellumHub Overview` for target health and 5xx by service, then `Gateway and HTTP` for route, status, latency, rate-limit signals, and gateway error logs.

**Useful Commands:**

```bash
docker compose ps gateway-service redis-gateway
docker compose logs --tail=200 gateway-service redis-gateway
curl http://localhost:8080/actuator/health
curl http://localhost:9090/api/v1/query?query=up%7Bjob%3D%22gateway-service%22%7D
```

**Likely Causes:** Gateway container restart loop, Redis unavailable for rate limiting, downstream service unavailable, invalid JWT configuration, route mismatch, or database/Kafka pressure surfacing through downstream responses.

**First Actions:** Confirm gateway health, check recent gateway `ERROR` logs in Loki, identify whether 5xx is concentrated on a single route, then inspect the downstream service shown by the route.

**Escalate When:** Gateway cannot start after a rebuild, multiple services are failing simultaneously, or traces show repeated failures across gateway and downstream services.

## Recommendation Slow Or Empty

**Symptom:** `RecommendationEmptyResultsHigh` fires, recommendation routes are slow, or users receive empty recommendation responses.

**Impact:** Discovery quality drops and cold-start users may not receive useful book suggestions.

**Look First:** Open `Recommendation Health` for recommendation HTTP latency, empty-result counters when available, DB pool state, and recommendation-service errors.

**Useful Commands:**

```bash
docker compose ps recommendation-service postgres-recommendation kafka
docker compose logs --tail=200 recommendation-service
curl http://localhost:9090/api/v1/query?query=up%7Bjob%3D%22recommendation-service%22%7D
```

**Likely Causes:** Missing projection data from Kafka, pgvector query slowness, exhausted Hikari pool, embedding/profile data drift, or future business metrics not yet instrumented.

**First Actions:** Check recommendation-service health, DB pool pending connections, recommendation errors, and Kafka Flow for missed or failed consumer signals. If business metrics are absent, inspect logs and Kafka UI.

**Escalate When:** Empty results persist after Kafka consumers catch up, database queries remain slow, or user/profile/book projection data appears corrupted.

## Kafka Consumer Lag High

**Symptom:** `KafkaConsumerLagHigh` fires or Kafka UI shows consumer groups falling behind.

**Impact:** Engagement, catalog, and preference events may not update recommendation or engagement projections quickly.

**Look First:** Open `Kafka Flow` for consumer lag, consumed-event counters, failure counters, and DLT logs. Use Kafka UI at `http://localhost:8090` for topic and group details.

**Useful Commands:**

```bash
docker compose ps kafka zookeeper recommendation-service engagement-service
docker exec kafka kafka-consumer-groups --bootstrap-server kafka:29092 --all-groups --describe
docker compose logs --tail=200 recommendation-service engagement-service kafka
```

**Likely Causes:** Consumer restart loop, poison event retries, topic contract drift, slow database writes, Kafka broker pressure, or metrics not yet exposed for some consumer groups.

**First Actions:** Identify the lagging group/topic, compare service logs with Kafka UI, inspect retry/DLT logs, and check Hikari pending connections for the consumer service.

**Escalate When:** Lag grows after restart, DLT entries increase, or topic contract mismatches are suspected.

## Event Sent To DLT

**Symptom:** `KafkaDltEventsDetected` fires or Loki query `{environment="local"} |= "DLT"` returns recent events.

**Impact:** Some state transition failed after retries. Recommendation or engagement projections may be stale for the affected entity.

**Look First:** Open `Kafka Flow`, especially `DLT Logs` and `DLT Metrics`. Then inspect the service-specific error logs.

**Useful Commands:**

```bash
docker compose logs --tail=200 engagement-service recommendation-service
docker exec kafka kafka-topics --bootstrap-server kafka:29092 --list
docker exec kafka kafka-console-consumer --bootstrap-server kafka:29092 --topic <topic-name>-dlt --from-beginning --max-messages 5
```

**Likely Causes:** Invalid event shape, schema/topic drift, missing referenced entity, database constraint failure, or an application bug in the consumer.

**First Actions:** Capture original topic, DLT topic, exception message, event type, and entity IDs from logs. Avoid dumping full payloads into issue comments or logs.

**Escalate When:** The same event type repeatedly reaches DLT, DLT volume increases, or replay would require manual data repair.

## Database Saturated Or Hikari Exhausted

**Symptom:** `HikariPendingConnections` or `JvmHeapNearLimit` fires, DB-backed routes slow down, or service logs mention connection acquisition timeouts.

**Impact:** Reads/writes may slow down or fail across catalog, user, engagement, or recommendation workflows.

**Look First:** Open `Services JVM and DB` for Hikari active/idle/pending, acquire time, heap, GC, and CPU. Cross-check `Recommendation Health` for recommendation-specific DB pool pressure.

**Useful Commands:**

```bash
docker compose ps postgres-user postgres-catalog postgres-engagement postgres-recommendation
docker compose logs --tail=200 user-service catalog-service engagement-service recommendation-service
docker compose logs --tail=100 postgres-user postgres-catalog postgres-engagement postgres-recommendation
```

**Likely Causes:** Slow SQL, migration issue, too many concurrent requests, exhausted pool size, PostgreSQL restart, or memory pressure causing long pauses.

**First Actions:** Identify which service has pending connections, check DB container health, inspect recent service errors, and compare request latency with Hikari acquire time.

**Escalate When:** Pending connections persist, PostgreSQL restarts, Flyway fails, or heap/GC pressure remains high after traffic drops.

## Prometheus Target Down

**Symptom:** `PrometheusTargetDown` fires or `VellumHub Overview` shows fewer than five application targets UP.

**Impact:** Metrics for the affected service are missing; the service may also be unavailable.

**Look First:** Open `VellumHub Overview`, then Prometheus `http://localhost:9090/targets`.

**Useful Commands:**

```bash
docker compose ps
docker compose logs --tail=150 <service-name>
curl http://localhost:9090/targets
curl http://localhost:8080/actuator/health
```

**Likely Causes:** Service container unhealthy, wrong network/service name, Actuator endpoint not exposed, application startup failure, or Prometheus scrape config mismatch.

**First Actions:** Confirm the container is running, inspect its healthcheck, call `/actuator/prometheus` from inside the Docker network if needed, and compare service names with `docker/observability/prometheus/prometheus.yml`.

**Escalate When:** The service starts but Prometheus still cannot scrape it, or multiple services disappear at once.

## Logs Missing In Loki

**Symptom:** Grafana Explore or dashboard log panels show no logs for a service while Docker logs exist.

**Impact:** Investigations lose structured log context and trace-to-logs navigation becomes weaker.

**Look First:** Open `VellumHub Overview` recent errors, then Grafana Explore with `{environment="local"}` and `{service="<service-name>"}`.

**Useful Commands:**

```bash
docker compose ps alloy loki
docker compose logs --tail=200 alloy loki
docker compose logs --tail=50 gateway-service user-service catalog-service engagement-service recommendation-service
curl http://localhost:3100/ready
curl http://localhost:12345/-/ready
```

**Likely Causes:** Alloy cannot read Docker socket, Loki is unhealthy, service logs are not JSON, service label extraction changed, or the container name is not covered by Alloy discovery.

**First Actions:** Confirm Alloy and Loki readiness, check Alloy logs for Docker discovery errors, verify the app emits JSON logs, and compare container names with `docker/observability/alloy/config.alloy`.

**Escalate When:** Loki receives no logs from any service, Alloy cannot access Docker socket, or labels are missing after a logging configuration change.

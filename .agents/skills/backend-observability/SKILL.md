---
name: backend-observability
description: Add or review VellumHub telemetry — OpenTelemetry traces through the Java agent and Alloy/Tempo, Micrometer/Prometheus metrics and alert rules, structured logs in Loki — so a request or Kafka flow can be investigated without leaking personal data. Use it when instrumenting a new HTTP or event path, adding a metric, counter or alert, investigating latency or error rates, or reviewing log statements, label cardinality or Prometheus rules.
---

# Backend observability (VellumHub)

Instrument for the questions an on-call engineer will ask, not for completeness. Every signal costs storage and attention, and a careless label or log line can leak user data.

## Procedure
1. Name the operation, service, failure modes and the questions to answer before adding signals.
2. Propagate trace context across HTTP and Kafka; span meaningful boundaries only. Kafka producer-to-consumer correlation through auto-instrumentation is best-effort.
3. Use RED metrics (rate, errors, duration) plus saturation where justified. Define units and histograms, keep label values bounded: topic, consumer group, outcome or category, never user IDs, e-mails, tokens or raw addresses. Existing names follow `vellumhub.*` (for example `vellumhub.kafka.recovery.quarantined`).
4. Logs keep `trace_id`/`span_id`, stable keys (`operation=… topic=…`) and no secrets or personal values. Database exception messages count as personal data because they contain the conflicting values.
5. Alerts need a runbook link that exists in the repo (for example `services/auth-service/README.md#alerts`) and should state the first checks to make.
6. A dashboard is not proof of end-to-end collection; verify the collector path, sampling and what happens when telemetry delivery fails.
7. Performance comparisons need baseline, environment, data, load, repetitions and p50/p95/p99.

## Output
Signal → purpose → emitter → validation path → open questions.

Adapted from github/awesome-copilot `spring-observability`.

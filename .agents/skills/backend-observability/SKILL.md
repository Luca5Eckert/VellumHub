---
name: backend-observability
description: Define and validate useful OpenTelemetry logs, metrics and traces for backend changes without inventing SLOs or measurements.
---

# backend-observability

## Trigger
New request/event flow, troubleshooting, latency/availability work, instrumentation or telemetry pipeline changes.

## Procedure
1. Identify the user operation, service/component name, error modes and investigation questions before adding signals.
2. Propagate trace context across HTTP and message boundaries; instrument spans at meaningful boundaries, without over-instrumentation.
3. Use RED metrics where appropriate (rate, errors, duration), and saturation/resource signals when justified. Define units, histogram aggregation and bounded label cardinality.
4. Preserve trace_id/span_id in structured logs, with privacy-safe redaction and stable field names. Never add account IDs or credentials as high-cardinality metric labels.
5. Check collector/exporter protocol compatibility, backpressure, filtering, sampling and failure to deliver telemetry; a dashboard does not prove end-to-end collection.
6. For performance changes, record baseline, environment, test data, load, repetitions and p50/p95/p99 as applicable; compare like with like.
7. Add regression checks for telemetry contract or documented operational queries when possible.

## Output
Signal -> purpose -> emitter -> validation path -> remaining unknowns.

## Reference
Adapted from https://github.com/github/awesome-copilot/tree/main/skills/spring-observability/SKILL.md.

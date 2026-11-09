---
name: event-driven-reliability
description: Use for Kafka contracts, transactional outbox, projections, ordered streams, retries, idempotency or CDC/schema evolution work.
---
# Event-driven reliability

1. Identify the source of truth, event owner, consumers, aggregate key, schema revision and expected delivery/ordering guarantees.
2. Trace domain write -> outbox insert -> commit -> dispatch -> broker ACK -> consumer projection/checkpoint; enumerate every failure window.
3. Require transactional atomicity of domain change and outbox intent where promised; publishing is not exactly-once application.
4. Check duplicate event IDs, revision gaps, reordering, stale leases/fencing, ambiguous ACK, replay and poison-message policy. Preserve event identity and monotonic update semantics.
5. Check backward/forward schema compatibility, canonical fixtures, headers and consumer migration order; never assume registry configuration proves runtime enforcement.
6. Design tests at the right fidelity: SQL rollback, real broker/storage integration and consumer retry/idempotency. Include negative scenarios, not only happy-path publication.
7. Discuss latency, operational maintenance, retention and recovery costs before adding machinery; some events may intentionally have weaker guarantees.
8. Reconcile test evidence with docs/ARCHITECTURE_FITNESS_FUNCTIONS.md and the actual CI run for this SHA.

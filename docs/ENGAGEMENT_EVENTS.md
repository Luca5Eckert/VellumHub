# Engagement — durable domain facts

**Scope:** [#202](https://github.com/Luca5Eckert/VellumHub/issues/202), [PR #297](https://github.com/Luca5Eckert/VellumHub/pull/297).

The Engagement bounded context owns Rating and Reaction mutations. Published events are **facts in past tense**, not commands to update Recommendation. Type names: `RatingCreatedEvent`, `RatingUpdatedEvent`, `RatingDeletedEvent`, and `ReactionChangedEvent` (creation means oldTypeReaction=null). Baseline records are *technical migration checkpoints*, not user interactions.

| Stream | Kafka key | Revision identity | Consumers |
| --- | --- | --- | --- |
| `engagement.ratings.v1` | userId | RATING + ratingId | Recommendation UserProfile |
| `engagement.reactions.v1` | userId | REACTION + reactionId | Recommendation UserProfile |

Every live fact contains immutable eventId, occurredAt, the persisted aggregate ID, userId, bookId and optimistic revision. Rating updates carry old/new stars; Reaction transitions carry old/new reaction types.

## Delivery and consumption

- Business state and an Outbox JSONB row commit in the same PostgreSQL transaction. The asynchronous publisher may resend a fact if an ACK was received but the status update was interrupted.
- Claim rows with `SKIP LOCKED`, short leases, token fencing and predecessor revision checks. Mark PUBLISHED **only** after a broker ACK. Broker outages keep events durable, with retry and lastError.
- Recommendation persists `processed_engagement_events` and per-aggregate `engagement_event_offsets` within the UserProfile transaction. Already-applied revisions are no-ops; reusing an event ID on an unapplied revision fails the transaction instead of silently acknowledging it. Missing BookFeature is a retryable error, not a successful skip. See [Consumer Idempotency](CONSUMER_IDEMPOTENCY.md).
- New aggregates start at revision 0; preexisting aggregates receive migration-only RatingBaselineEvent/ReactionBaselineEvent at their persisted revision. Baselines only set checkpoints and **do not** reapply historic weights.
- Kafka key userId preserves order *within each topic*, not a global order across rating and reaction topics. `UserProfile.@Version` guards concurrent updates across partitions.
- Strict retry favors ordering over liveness; poison messages can block a partition. Coordinate DLT/replay design with #272.

## Rollout checklist

1. Freeze Engagement rating/reaction writers; drain legacy created-rating, updated-rating and user-reaction-changed topics.
2. Apply Recommendation V4 for processed_engagement_events and engagement_event_offsets. Deploy new consumers and Kafka topic aliases.
3. Provision engagement.ratings.v1 and engagement.reactions.v1; keep partition count stable during cutover.
4. Check pre-existing duplicate (userId,bookId) ratings; resolve before applying the unique index in Engagement V8.
5. Apply Engagement V8, recording baselines of preexisting Rating and Reaction. Enable the relay after consumers are ready. Reopen writers.
6. Disable Recommendation legacy rating/reaction listeners via `KAFKA_LEGACY_CONSUMERS_ENABLED=false` after the drain; verify baselines, live CREATE/UPDATE/DELETE, duplicate redelivery, outbox backlog and consumer lag. Retire legacy code only after checking convergence.

**ReadingSessionEntry** is a read projection of a Catalog-owned Reading Progress event already protected by the Catalog Outbox. It must not be republished as a redundant Engagement event absent a distinct downstream use case.

HTTP Swagger/OpenAPI endpoints report *local* database success and eventual downstream convergence. Log eventId, aggregate type/revision/topic/attempt, but not full payloads. Metrics `vellumhub.outbox.pending/published/failed` must avoid IDs as high-cardinality tags.

Canonical consumers expose `vellumhub.kafka.events.duplicate`, retaining event IDs in logs but never in metric labels. The legacy listeners remain enabled by default for pre-cutover deployments and do not share the new identity checkpoint; do not process the same logical mutation from both versions.

Physical delivery remains at-least-once; even a compensated RatingDeleted effect cannot perfectly invert a normalized historical embedding vector. Production guarantees require monitored rollout and failure/recovery testing.


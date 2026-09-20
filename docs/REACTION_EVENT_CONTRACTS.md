# Reaction Lifecycle Kafka Contract

The Engagement Service publishes reaction lifecycle facts on the `user-reaction-changed` topic. Recommendation consumes those facts to update its local user profile without calling Engagement synchronously.

## Payload

The enriched payload contains:

| Field | Type | Meaning |
|---|---|---|
| `eventId` | UUID | Unique identifier for this event occurrence |
| `occurredAt` | Instant | Time when the reaction mutation occurred |
| `reactionId` | Long | Source reaction identifier in Engagement |
| `userId` | UUID | User that owns the reaction |
| `bookId` | UUID | Book related to the reaction |
| `oldTypeReaction` | String, nullable | Previous reaction type; `null` for creation |
| `newTypeReaction` | String | Resulting reaction type |
| `typeReaction` | String | Legacy compatibility alias for the resulting type |

New producers must use `oldTypeReaction` and `newTypeReaction`. The legacy `typeReaction` field remains temporarily so older payloads and consumers can migrate without requiring a topic reset.

## Lifecycle Semantics

Creation is represented as a transition from no previous value:

```json
{
  "eventId": "11111111-1111-1111-1111-111111111111",
  "occurredAt": "2026-09-19T12:00:00Z",
  "reactionId": 42,
  "userId": "22222222-2222-2222-2222-222222222222",
  "bookId": "33333333-3333-3333-3333-333333333333",
  "oldTypeReaction": null,
  "newTypeReaction": "POSITIVE",
  "typeReaction": "POSITIVE"
}
```

An update carries both sides of the transition:

```json
{
  "eventId": "44444444-4444-4444-4444-444444444444",
  "occurredAt": "2026-09-19T12:10:00Z",
  "reactionId": 42,
  "userId": "22222222-2222-2222-2222-222222222222",
  "bookId": "33333333-3333-3333-3333-333333333333",
  "oldTypeReaction": "POSITIVE",
  "newTypeReaction": "VERY_POSITIVE",
  "typeReaction": "VERY_POSITIVE"
}
```

A same-value update remains an explicit occurrence:

```text
POSITIVE -> POSITIVE
```

Recommendation calculates a zero adjustment for that transition instead of treating it as another positive interaction.

## Recommendation Semantics

Reaction weights currently are:

| Reaction | Weight |
|---|---:|
| `VERY_POSITIVE` | 3.0 |
| `POSITIVE` | 1.5 |
| `NEGATIVE` | -0.5 |

Recommendation applies:

```text
adjustment = weight(new) - weight(old)
```

For creation or a legacy event without an old value, `weight(old) = 0`.

Examples:

| Transition | Adjustment |
|---|---:|
| `null -> POSITIVE` | +1.5 |
| `POSITIVE -> VERY_POSITIVE` | +1.5 |
| `VERY_POSITIVE -> POSITIVE` | -1.5 |
| `POSITIVE -> NEGATIVE` | -2.0 |
| `POSITIVE -> POSITIVE` | 0.0 |

## Audit Timestamps

The `reactions` table stores:

- `created_at`: original creation occurrence time;
- `updated_at`: latest reaction mutation occurrence time.

Creation publishes `occurredAt = created_at`. Updates publish `occurredAt = updated_at`.

Rows that existed before the audit migration receive the migration timestamp as their initial backfill value. Historical timestamp precision therefore starts with the migration for those existing rows.

Updates acquire a database write lock on the source reaction until the transaction completes. Concurrent updates therefore capture the last committed reaction type, rather than publishing transitions from a stale state. This does not make database and Kafka publication atomic.

The follow-up V4 migration supplies database defaults for inserts from older application instances during rollout, without changing the V3 migration checksum. New instances still write explicit occurrence times. Old instances do not maintain `updated_at` on mutation; complete audit semantics require finishing the producer rollout. Deploy the transition-aware consumer before the enriched producer, since an old consumer still treats updates as additive interactions.

## Compatibility

An event produced with the old payload shape:

```json
{
  "userId": "...",
  "bookId": "...",
  "typeReaction": "POSITIVE"
}
```

is interpreted by the updated consumer as:

```text
oldTypeReaction = null
newTypeReaction = typeReaction
```

Legacy events do not have `eventId`, `occurredAt`, or `reactionId`. Consumer idempotency for enriched events belongs to issue #200, which must define the final handling policy for those legacy messages.

## Delivery Guarantee Boundary

This contract makes the event self-contained but does not provide atomic publication or idempotent consumption by itself.

- transactional outbox for Engagement belongs to #202;
- idempotent Recommendation consumers belong to #200;
- retry, DLT, and safe replay belong to #272.

This separation keeps lifecycle semantics independent from delivery mechanics.

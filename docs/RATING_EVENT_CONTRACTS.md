# Rating Lifecycle Kafka Contracts

The Engagement Service owns rating and review state. Kafka events expose the rating lifecycle so downstream consumers can distinguish creation from updates without calling Engagement synchronously.

## Topics

| Topic | Trigger | Meaning |
|---|---|---|
| `created-rating` | Successful `POST /rating` | First persisted rating for a user/book pair |
| `updated-rating` | Successful `PUT /rating/{ratingId}` | Transition of an existing persisted rating |

Topic names and Java payloads are defined in `lib/kafka-contracts`.

## Shared payload fields

Both events expose the same lifecycle metadata:

| Field | Type | Semantics |
|---|---|---|
| `eventId` | UUID | Unique identifier for this event occurrence |
| `occurredAt` | Instant | UTC timestamp at event construction |
| `ratingId` | Long | Engagement-owned persisted rating identifier |
| `userId` | UUID | User who owns the rating |
| `bookId` | UUID | Rated book |
| `oldStars` | Integer | Previous persisted stars; `null` for creation |
| `newStars` | int | Persisted stars after the command |
| `reviewChanged` | boolean | Whether an update changed the persisted review text |

The review body is intentionally not carried by these events. The `rating` row in `engagement_db` remains the source of truth for the rating and review content.

## `created-rating`

`created-rating` is emitted only after a new rating has been persisted successfully.

Example semantic payload:

```json
{
  "eventId": "c0b9bb39-b13f-4da3-93d5-b34031576d23",
  "occurredAt": "2026-09-14T12:00:00Z",
  "ratingId": 42,
  "userId": "9cbddacb-d843-44d9-9a90-74dfa9731891",
  "bookId": "7aacac13-e30f-44aa-bcf7-5da4002b88a8",
  "oldStars": null,
  "newStars": 5,
  "reviewChanged": false
}
```

`oldStars` is `null` rather than `0`. Zero is a valid rating value in the Engagement domain and therefore cannot safely represent "no previous rating".

`reviewChanged` is `false` for creation because there is no previous review state to compare against.

## `updated-rating`

`updated-rating` is emitted only after an existing rating has been updated and persisted successfully.

The producer captures the previous stars and review before mutating the entity, persists the new state, then publishes the resulting transition.

Examples:

| Update | `oldStars` | `newStars` | `reviewChanged` |
|---|---:|---:|---|
| 4 stars -> 5 stars, same review | 4 | 5 | false |
| 2 stars -> 4 stars, review changed | 2 | 4 | true |
| Review-only update at 4 stars | 4 | 4 | true |
| Same review submitted with star change | previous value | new value | false |

A review-only update does not invent a star transition: `oldStars` and `newStars` remain equal.

## Ordering key

Both topics use `userId.toString()` as the Kafka message key. This keeps events for the same user consistently keyed within each topic.

## Consumer boundary

Issue #188 establishes the producer-side lifecycle contract. The Recommendation Service currently receives the enriched `created-rating` payload but retains its previous profile-update semantics for compatibility.

Consumption of `updated-rating`, correct first-rating semantics, and use of real `oldStars` / `newStars` deltas belong to issue #189.

## Delivery guarantee boundary

The current producer persists state and publishes through `KafkaTemplate`; transactional outbox publication is not yet an implemented guarantee. This contract change improves event semantics and payload quality but does not claim atomic database-and-Kafka publication.

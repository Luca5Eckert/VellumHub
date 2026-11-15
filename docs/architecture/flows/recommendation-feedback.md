---
id: vh-recommendation-feedback
slug: recommendation-feedback
type: sequence-diagram
system: vellumhub
status: implemented-in-repository
related: [vh-service-gateway, vh-service-engagement, vh-service-recommendation]
---

# Sequence — Reader feedback to independent recommendation read

```mermaid
sequenceDiagram
    autonumber
    actor Reader
    participant G as VH-GTW Gateway
    participant E as VH-ENG Engagement
    participant ED as Engagement PostgreSQL
    participant K as Kafka (engagement.ratings.v1 / reactions.v1)
    participant R as VH-REC Recommendation
    participant RD as Recommendation PostgreSQL + pgvector
    participant Cache as Domain-cache Redis
    Reader->>G: POST rating or reaction with access token
    G->>E: Authorized feedback mutation
    E->>ED: Commit feedback + Outbox in one transaction
    E-->>Reader: Local write committed
    E->>K: Outbox relay after commit
    K->>R: Canonical interaction event
    R->>RD: Check event/revision; update profile delta and checkpoint atomically
    R->>Cache: Invalidate affected profile generation (best effort)
    Reader->>G: GET /api/v1/recommendations/recommendations
    G->>R: Relay authenticated request
    R->>Cache: Lookup user/params/generation DTO
    alt Cache hit
        Cache-->>R: Serialized ranking DTO
    else Miss / unavailable
        R->>RD: pgvector candidate retrieval + 70/30 ranking (or popularity fallback)
        RD-->>R: Local result; no User/Catalog/Engagement fan-out
        R->>Cache: Optional TTL write
    end
    R-->>Reader: Recommendation response
    Note over Reader,R: Result may precede Kafka consumer convergence; eventual consistency
```

Read model and ranker: [VH-REC](../services/recommendation.md); owner of rating/reaction state: [VH-ENG](../services/engagement.md). The cache does not store canonical identities, event checkpoints or Kafka schema definitions. Reference: [Recommendation cache](../../RECOMMENDATION_REDIS_CACHE.md), [Engagement contracts](../../ENGAGEMENT_EVENTS.md), [consumer idempotency](../../CONSUMER_IDEMPOTENCY.md).

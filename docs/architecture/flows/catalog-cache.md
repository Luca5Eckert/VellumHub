---
id: vh-catalog-cache
slug: catalog-cache
type: sequence-diagram
system: vellumhub
status: implemented-in-repository
related: [vh-service-catalog, vh-c4-catalog-components, vh-cache-rollout]
---

# Sequence — Catalog cache-aside and after-commit invalidation

JWT authorization occurs before any Redis cache lookup. Cache data is disposable JSON DTO; PostgreSQL remains canonical. Generation keys isolate new readers from an older in-flight fill; they do **not** guarantee linearizable responses for requests already executing.

```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant S as Catalog SecurityFilterChain
    participant H as Book GET handler
    participant C as BookReadCache
    participant Redis as Domain-cache Redis
    participant PG as Catalog PostgreSQL
    participant W as Book mutation + Outbox
    Client->>S: GET /books/{id} with JWT
    alt Invalid token / insufficient scope
        S-->>Client: 401 / 403 (no cache access)
    else Authorized
        S->>H: Continue
        H->>C: detail(id) / page(params)
        C->>Redis: Read generation, read versioned JSON key
        alt Valid HIT
            Redis-->>C: DTO
            C-->>Client: 200 response
        else MISS / Redis failure
            C->>PG: Bounded coalesced read (max 64 loads per instance)
            PG-->>C: Canonical Book DTO
            C->>Redis: Best-effort SET with TTL / size cap
            C-->>Client: Book DTO
        else Saturated or leader timeout
            C-->>Client: 503 + Retry-After: 1
        end
    end
    opt Separate authorized write committed
        Client->>W: Create / update / delete / cover
        W->>PG: Persist Book + Outbox atomically
        PG-->>W: COMMIT
        W->>Redis: afterCommit bump book + page generations (best effort)
        W-->>Client: Local write result
    end
```

**Config and capacity:** [Catalog Redis runbook](../../CATALOG_REDIS_CACHE.md). In local Compose/Kind both read and invalidation flags are ON; production flags remain OFF because the configured external host is a placeholder. In a staged rollout first enable **writer invalidation on every pod while reads are OFF**, then enable read cache. See [state machine](../states/cache-rollout.md) and [C4 L3](../diagrams/c4-catalog-components.md). The implementation skips 404s/empty pages, caps JSON at 128 KiB and avoids creating one Redis generation key per unknown UUID.

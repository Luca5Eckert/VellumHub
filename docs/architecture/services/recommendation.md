---
id: vh-service-recommendation
code: VH-REC
slug: recommendation
type: c4-container
c4_level: 2
system: vellumhub
status: implemented-in-repository
source_path: services/recommendation-service
data_store: "Recommendation PostgreSQL + pgvector (derived book features, user profiles and checkpoints)"
related:
  - vh-service-engagement
  - vh-service-catalog
  - vh-service-user
  - vh-recommendation-feedback
---

# VH-REC — Recommendation

**Container boundary:** book embedding features, user profile vectors, pgvector/HNSW retrieval, popularity fallback and 70/30 reranking.

| Contract | Implemented role |
| --- | --- |
| Owns | book embedding features, user profile vectors, pgvector/HNSW retrieval, popularity fallback and 70/30 reranking |
| Source of truth | Recommendation PostgreSQL + pgvector (derived book features, user profiles and checkpoints) |
| Inbound | Authorized GET /recommendations via Gateway; consumes Catalog, User preference and Engagement integration facts |
| Outbound | Reads its own derived tables and optional domain Redis response cache; it does not call source services during a recommendation GET. |
| Security invariant | Derives profile UUID from validated JWT subject and scope; cache keys include user, pagination, ranker version and generations. |
| Failure / consistency boundary | Consumer checkpoints and revisions prevent duplicate projection application in canonical streams; ranker falls back to popularity when no profile exists. Cache fail-open to local PostgreSQL; cold cache can regress p95. |

## Source and neighboring documents

- Implementation: [`services/recommendation-service/`](../../../services/recommendation-service/) and [service README](../../../services/recommendation-service/README.md).
- Diagrams and flows: [recommendation-feedback](../flows/recommendation-feedback.md) · [c4-containers](../diagrams/c4-containers.md).
- Registry: [all six stable service codes](README.md); [architecture home](../README.md).
- Relevant runtime truth: [Main README](../../../README.md), [security policies](../../SECURITY_SCOPES_RBAC.md), [distributed test evidence](../../DISTRIBUTED_TESTING.md).

**Scope of claim:** this document describes the repository's container and contract, not proof of a running production deployment. Verify the actual routes and event topic names in source/config when changing this service.

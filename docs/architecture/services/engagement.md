---
id: vh-service-engagement
code: VH-ENG
slug: engagement
type: c4-container
c4_level: 2
system: vellumhub
status: implemented-in-repository
source_path: services/engagement-service
data_store: "Engagement PostgreSQL (ratings, reactions and derived history)"
related:
  - vh-service-catalog
  - vh-service-recommendation
  - vh-book-outbox
  - vh-recommendation-feedback
---

# VH-ENG — Engagement

**Container boundary:** canonical ratings and reactions, replicated Book snapshots, Kafka-derived reading-session history.

| Contract | Implemented role |
| --- | --- |
| Owns | canonical ratings and reactions, replicated Book snapshots, Kafka-derived reading-session history |
| Source of truth | Engagement PostgreSQL (ratings, reactions and derived history) |
| Inbound | Authorized /rating and /reactions API via Gateway; consumes Catalog Book and reading-progress events |
| Outbound | Persists feedback plus Outbox facts; publishes engagement.ratings.v1 and engagement.reactions.v1 to Kafka for VH-REC |
| Security invariant | Per-user ownership and required human OAuth scopes; does not own book metadata or current reading progress. |
| Failure / consistency boundary | Ordered canonical facts are checkpointed; legacy listeners and retry/DLT paths have separate compatibility and rollout limits. |

## Source and neighboring documents

- Implementation: [`services/engagement-service/`](../../../services/engagement-service/) and [service README](../../../services/engagement-service/README.md).
- Diagrams and flows: [recommendation-feedback](../flows/recommendation-feedback.md) · [book-outbox](../flows/book-outbox.md).
- Registry: [all six stable service codes](README.md); [architecture home](../README.md).
- Relevant runtime truth: [Main README](../../../README.md), [security policies](../../SECURITY_SCOPES_RBAC.md), [distributed test evidence](../../DISTRIBUTED_TESTING.md).

**Scope of claim:** this document describes the repository's container and contract, not proof of a running production deployment. Verify the actual routes and event topic names in source/config when changing this service.

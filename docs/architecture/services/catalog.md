---
id: vh-service-catalog
code: VH-CAT
slug: catalog
type: c4-container
c4_level: 2
system: vellumhub
status: implemented-in-repository
source_path: services/catalog-service
data_store: "Catalog PostgreSQL (canonical books, progress, Outbox)"
related:
  - vh-c4-catalog-components
  - vh-book-outbox
  - vh-catalog-cache
  - vh-outbox-delivery
  - vh-cache-rollout
---

# VH-CAT — Catalog

**Container boundary:** Book/genre metadata, community book requests, covers, lists/memberships, current reading status and progress.

| Contract | Implemented role |
| --- | --- |
| Owns | Book/genre metadata, community book requests, covers, lists/memberships, current reading status and progress |
| Source of truth | Catalog PostgreSQL (canonical books, progress, Outbox) |
| Inbound | Authorized /books, /book-requests, /book-list, /book-progress API; Google Books ISBN lookup for import |
| Outbound | Persists Book and Outbox atomically; dispatches book-events and reading-progress-events to Kafka. Catalog read-cache uses isolated domain Redis, not Gateway Redis. |
| Security invariant | Authorizes JWT/scopes before GET cache lookup; mutation and owner checks remain in API/service boundaries. |
| Failure / consistency boundary | Outbox retries Kafka failure. Catalog uses cache-aside DTOs with versioned generations, after-commit invalidation and bounded PostgreSQL fallback. Local cache flags ON; production overlay OFF. |

## Source and neighboring documents

- Implementation: [`services/catalog-service/`](../../../services/catalog-service/) and [service README](../../../services/catalog-service/README.md).
- Diagrams and flows: [c4-catalog-components](../diagrams/c4-catalog-components.md) · [book-outbox](../flows/book-outbox.md) · [catalog-cache](../flows/catalog-cache.md) · [outbox-delivery](../states/outbox-delivery.md) · [cache-rollout](../states/cache-rollout.md).
- Registry: [all six stable service codes](README.md); [architecture home](../README.md).
- Relevant runtime truth: [Main README](../../../README.md), [security policies](../../SECURITY_SCOPES_RBAC.md), [distributed test evidence](../../DISTRIBUTED_TESTING.md).

**Scope of claim:** this document describes the repository's container and contract, not proof of a running production deployment. Verify the actual routes and event topic names in source/config when changing this service.

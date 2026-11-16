---
id: vh-services
slug: services
type: service-registry
system: vellumhub
status: implemented-in-repository
related: [vh-architecture, vh-c4-containers]
---

# Service registry — stable codes and bounded contexts

| Code | Slug | Deployable | Primary responsibility | Authoritative store |
| --- | --- | --- | --- | --- |
| `VH-AUT` | `auth` | [Auth](auth.md) | Identity, OAuth/OIDC, sessions, token issuance | Auth PostgreSQL |
| `VH-GTW` | `gateway` | [Gateway](gateway.md) | Routing, bearer validation/relay, API docs, rate limiting | No business database; Gateway Redis limiter |
| `VH-USR` | `user` | [User](user.md) | User profiles and preferences, cold-start preference facts | User PostgreSQL |
| `VH-CAT` | `catalog` | [Catalog](catalog.md) | Books, requests, lists, current reading progress, canonical Outbox | Catalog PostgreSQL |
| `VH-ENG` | `engagement` | [Engagement](engagement.md) | Ratings/reactions and replicated reading history | Engagement PostgreSQL |
| `VH-REC` | `recommendation` | [Recommendation](recommendation.md) | Book features, pgvector ranking and personalized serving | Recommendation PostgreSQL + pgvector |

Codes identify **logical deployed containers**, not changing team names or container image tags. Slugs identify page paths and metadata. An API or entity may cross a workflow but has **one owner**: PostgreSQL writes stay in the owning service. Kafka carries committed facts, not shared table access. Recommendation reads its own projections without synchronous User/Catalog/Engagement fan-out.

Graph: [C4 container diagram](../diagrams/c4-containers.md) · [Auth sequence](../flows/identity-onboarding.md) · [Book Outbox](../flows/book-outbox.md) · [Feedback](../flows/recommendation-feedback.md) · [Catalog cache](../flows/catalog-cache.md) · [Architecture home](../README.md).

The source-level READMEs under `services/<slug>-service/README.md` contain endpoint and implementation detail; some pre-Auth migration sections are **historical**. The `VH-AUT` ownership invariant and current security contract take precedence over old descriptions of User as JWT issuer. Each service card links directly to its implementation.

---
id: vh-architecture
slug: architecture
type: architecture-index
system: vellumhub
status: implemented-in-repository
c4_levels: [context, container, component]
related:
  - vh-services
  - vh-c4-context
  - vh-c4-containers
  - vh-c4-catalog-components
  - vh-outbox-delivery
  - vh-cache-rollout
---

# VellumHub — architecture knowledge base

**Start here.** This is a navigable architecture map, not an alternative source of truth for runtime behavior. Code, contracts and CI are authoritative; the diagrams describe the repository's implemented topology and explicitly distinguish deployment from implementation. VellumHub is an event-driven social reading platform with **six Java deployables**, Auth-owned identity, service-owned PostgreSQL data, Kafka integration facts and optional domain Redis caches.

## Navigate by level

| Level / view | Diagram | Purpose |
| --- | --- | --- |
| C4 L1 — context | [System context](diagrams/c4-context.md) | Who uses VellumHub; which external systems it depends on |
| C4 L2 — containers | [Application and data containers](diagrams/c4-containers.md) | Six services, owned stores, Kafka and isolated Redis |
| C4 L3 — components | [Catalog and Outbox](diagrams/c4-catalog-components.md) | Request → cache → repository and mutation → Outbox boundaries |
| Ownership | [Service catalog](services/README.md) | Stable codes, slugs, contracts and sources per deployable |
| Sequence | [Auth registration](flows/identity-onboarding.md) | Pending identity, idempotent User provisioning, login boundaries |
| Sequence | [Book fact propagation](flows/book-outbox.md) | Canonical Book commit, relay, Kafka, consumer checkpoints |
| Sequence | [Feedback → ranking](flows/recommendation-feedback.md) | Engagement writes and locally served recommendations |
| Sequence | [Catalog cache-aside](flows/catalog-cache.md) | Authorized HIT/MISS, generation and after-commit invalidation |
| State machine | [Outbox delivery](states/outbox-delivery.md) | Real persisted PENDING / IN_FLIGHT / PUBLISHED transitions |
| State machine | [Cache rollout flags](states/cache-rollout.md) | Operational two-phase activation, explicit rollback |
| Visualization | [HTML diagram explorer](architecture-explorer.html) | Optional browser renderer of **these same Markdown Mermaid blocks** |
| Convention | [Documentation contract](CONTRIBUTING.md) | Machine-readable frontmatter, naming, validation and lifecycle |

## Entity and link contract

Every service has a stable **`code`** such as `VH-CAT` and a filesystem-safe **`slug`** such as `catalog`. Diagram pages have a globally stable `id` (`vh-c4-containers`). YAML frontmatter stores `type`, `status`, `related`, and source ownership; ordinary Markdown links connect the same pages for humans, Obsidian and LLM agents. A relationship in frontmatter **never replaces** an explicit link or evidence in prose. Use the `id` as a stable retrieval key and the relative file path as the actual navigation target.

```text
docs/architecture/
├── README.md                  # entrypoint + graph
├── CONTRIBUTING.md            # metadata/schema and maintenance
├── services/README.md         # service registry
├── services/{auth,gateway,user,catalog,engagement,recommendation}.md
├── diagrams/{c4-context,c4-containers,c4-catalog-components}.md
├── flows/{identity-onboarding,book-outbox,recommendation-feedback,catalog-cache}.md
├── states/{outbox-delivery,cache-rollout}.md
└── architecture-explorer.html # optional Markdown-backed renderer
```

**Facts vs evidence:** all six deployables and their code paths exist. A green GitHub workflow is a controlled verification, **not** evidence of deployment or actual production latency. Cache reads are **ON in local Compose/Kind configuration, OFF in the production overlay**. Kafka legacy consumers and Schema Registry migration have separate cutover conditions. For deployment details see [Kubernetes](../../deploy/kubernetes/README.md) and [operations](../OPERATIONS.md).

**Follow an edge:** [VH-AUT](services/auth.md) → [VH-USR](services/user.md) for private profile provisioning; [VH-CAT](services/catalog.md) → [Kafka](flows/book-outbox.md) → [VH-ENG](services/engagement.md) / [VH-REC](services/recommendation.md) for asynchronous facts. [VH-GTW](services/gateway.md) owns the external API edge but does not own authentication issuance or domain data.

**Source docs:** [README](../../README.md) · [Book contract](../BOOK_EVENTS.md) · [Engagement facts](../ENGAGEMENT_EVENTS.md) · [Consumer checkpoints](../CONSUMER_IDEMPOTENCY.md) · [Catalog Redis](../CATALOG_REDIS_CACHE.md) · [Recommendation Redis](../RECOMMENDATION_REDIS_CACHE.md) · [OAuth/RBAC](../SECURITY_SCOPES_RBAC.md).

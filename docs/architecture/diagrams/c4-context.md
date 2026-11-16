---
id: vh-c4-context
slug: c4-context
type: c4-context
c4_level: 1
system: vellumhub
status: implemented-in-repository
related: [vh-architecture, vh-c4-containers, vh-service-auth, vh-service-catalog]
---

# C4 L1 — System context

The system boundary includes the six VellumHub backend deployables. The reader signs in through **Auth**, then accesses domain APIs via **Gateway**. Google is an optional upstream OIDC provider; Google Books provides external ISBN metadata. These are external systems, not additional VellumHub databases.

```mermaid
C4Context
    title VellumHub — system context (implemented repository topology)
    Person(reader, "Reader", "Registers, tracks reading and asks for recommendations")
    Person(operator, "Developer / operator", "Inspects contracts, metrics and deployments")
    System(vh, "VellumHub backend", "Six deployables: identity, API, catalog, feedback and recommendation")
    System_Ext(google, "Google Identity Provider", "Optional federated OIDC for Auth")
    System_Ext(books, "Google Books API", "Optional book metadata from ISBN")
    Rel(reader, vh, "Uses registration, OAuth and reading APIs", "HTTPS")
    Rel(operator, vh, "Builds, tests and observes", "GitOps / metrics")
    Rel(vh, google, "Verifies federated identity when configured", "OIDC")
    Rel(vh, books, "Looks up ISBN metadata", "HTTPS")
```

| Actor or external system | Trust boundary | Source |
| --- | --- | --- |
| Reader | Browser/API requests require valid auth and appropriate scopes | [OAuth policy](../../SECURITY_SCOPES_RBAC.md) |
| Google Identity Provider | Auth federates; Google tokens are **not** downstream API bearer tokens | [Auth card](../services/auth.md) |
| Google Books | Catalog reads external metadata for ISBN import | [Catalog card](../services/catalog.md) |
| Developer/operator | GitHub CI and local/Kind are not production rollout evidence | [Deployment](../../../deploy/kubernetes/README.md) |

Next: [C4 L2 — containers](c4-containers.md) · [Service catalog](../services/README.md) · [Architecture index](../README.md).

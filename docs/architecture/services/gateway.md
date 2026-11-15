---
id: vh-service-gateway
code: VH-GTW
slug: gateway
type: c4-container
c4_level: 2
system: vellumhub
status: implemented-in-repository
source_path: services/gateway-service
data_store: "No domain database; Redis gateway instance for rate limiting"
related:
  - vh-c4-containers
  - vh-service-auth
  - vh-service-user
  - vh-service-catalog
  - vh-service-engagement
  - vh-service-recommendation
---

# VH-GTW — Gateway

**Container boundary:** public API edge, path routing, JWT verification, bearer relay, rate limiting and documentation aggregation.

| Contract | Implemented role |
| --- | --- |
| Owns | public API edge, path routing, JWT verification, bearer relay, rate limiting and documentation aggregation |
| Source of truth | No domain database; Redis gateway instance for rate limiting |
| Inbound | Client bearer API calls under /api/v1/users, /api/v1/catalog, /api/v1/engagement and /api/v1/recommendations |
| Outbound | Forwards calls to VH-USR, VH-CAT, VH-ENG and VH-REC; consults gateway-only Redis limiter. Login and PKCE go directly to Auth. |
| Security invariant | Enforces edge policy but downstream Resource Servers independently validate JWT audience/scopes/roles. Never signs tokens or owns domain state. |
| Failure / consistency boundary | Downstream unavailability affects that route; Gateway has no synchronous federated read orchestration for Recommendation. |

## Source and neighboring documents

- Implementation: [`services/gateway-service/`](../../../services/gateway-service/) and [service README](../../../services/gateway-service/README.md).
- Diagrams and flows: [c4-containers](../diagrams/c4-containers.md) · [recommendation-feedback](../flows/recommendation-feedback.md).
- Registry: [all six stable service codes](README.md); [architecture home](../README.md).
- Relevant runtime truth: [Main README](../../../README.md), [security policies](../../SECURITY_SCOPES_RBAC.md), [distributed test evidence](../../DISTRIBUTED_TESTING.md).

**Scope of claim:** this document describes the repository's container and contract, not proof of a running production deployment. Verify the actual routes and event topic names in source/config when changing this service.

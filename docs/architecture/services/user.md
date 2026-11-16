---
id: vh-service-user
code: VH-USR
slug: user
type: c4-container
c4_level: 2
system: vellumhub
status: implemented-in-repository
source_path: services/user-service
data_store: "User PostgreSQL"
related:
  - vh-service-auth
  - vh-service-recommendation
  - vh-identity-onboarding
---

# VH-USR — User

**Container boundary:** user profile, display details, genre preferences and cold-start preference facts.

| Contract | Implemented role |
| --- | --- |
| Owns | user profile, display details, genre preferences and cold-start preference facts |
| Source of truth | User PostgreSQL |
| Inbound | Protected profile/preferences API via Gateway; private profile provisioning from VH-AUT |
| Outbound | Publishes created-user-preference to Kafka for the VH-REC cold-start profile; stores profile state locally. |
| Security invariant | Not an OAuth token issuer; password/JWT/Google signing details in older service README sections are pre-Auth historical documentation. |
| Failure / consistency boundary | Profile provisioning is idempotent on the Auth-issued canonical UUID; preference propagation is asynchronous and may lag. |

## Source and neighboring documents

- Implementation: [`services/user-service/`](../../../services/user-service/) and [service README](../../../services/user-service/README.md).
- Diagrams and flows: [identity-onboarding](../flows/identity-onboarding.md) · [c4-containers](../diagrams/c4-containers.md).
- Registry: [all six stable service codes](README.md); [architecture home](../README.md).
- Relevant runtime truth: [Main README](../../../README.md), [security policies](../../SECURITY_SCOPES_RBAC.md), [distributed test evidence](../../DISTRIBUTED_TESTING.md).

**Scope of claim:** this document describes the repository's container and contract, not proof of a running production deployment. Verify the actual routes and event topic names in source/config when changing this service.

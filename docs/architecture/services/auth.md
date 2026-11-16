---
id: vh-service-auth
code: VH-AUT
slug: auth
type: c4-container
c4_level: 2
system: vellumhub
status: implemented-in-repository
source_path: services/auth-service
data_store: "Auth PostgreSQL (identity, OAuth grants and JDBC sessions)"
related:
  - vh-service-user
  - vh-c4-context
  - vh-identity-onboarding
---

# VH-AUT — Auth

**Container boundary:** canonical account identities, local Argon2id credentials, Google OIDC bindings, OAuth2/OIDC grants, RS256 signing and public JWKS, browser sessions.

| Contract | Implemented role |
| --- | --- |
| Owns | canonical account identities, local Argon2id credentials, Google OIDC bindings, OAuth2/OIDC grants, RS256 signing and public JWKS, browser sessions |
| Source of truth | Auth PostgreSQL (identity, OAuth grants and JDBC sessions) |
| Inbound | Direct OAuth2/OIDC browser requests, identity registration, token issuance and logout; internal JWKS retrieval by Resource Servers |
| Outbound | Private, idempotent profile provisioning to VH-USR; Google federation when configured; persists state in its own PostgreSQL |
| Security invariant | Sole API access-token issuer. Access tokens carry at+jwt, issuer/audience, canonical UUID subject, roles and scopes. ID tokens or Google tokens are not VellumHub API access tokens. |
| Failure / consistency boundary | Failed User provisioning leaves a durable pending identity; retries reuse the original UUID. Browser-session logout does not retrospectively revoke issued access tokens. |

## Source and neighboring documents

- Implementation: [`services/auth-service/`](../../../services/auth-service/) and [service README](../../../services/auth-service/README.md).
- Diagrams and flows: [identity-onboarding](../flows/identity-onboarding.md) · [c4-context](../diagrams/c4-context.md) · [c4-containers](../diagrams/c4-containers.md).
- Registry: [all six stable service codes](README.md); [architecture home](../README.md).
- Relevant runtime truth: [Main README](../../../README.md), [security policies](../../SECURITY_SCOPES_RBAC.md), [distributed test evidence](../../DISTRIBUTED_TESTING.md).

**Scope of claim:** this document describes the repository's container and contract, not proof of a running production deployment. Verify the actual routes and event topic names in source/config when changing this service.

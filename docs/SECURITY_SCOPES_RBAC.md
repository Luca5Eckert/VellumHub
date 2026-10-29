# SEC-10 — OAuth scopes and local RBAC (#311)

## Execution plan

1. Inventory current HTTP operations and distinguish anonymous, delegated human,
   human ADMIN and private provisioning access.
2. Register explicit client capabilities in Auth; preserve the granted subset in
   the access token. Issue `grant_type=authorization_code` from protocol context.
3. Enforce scope AND human role in each Resource Server before request binding;
   keep existing method/domain checks. Deny unmapped operations by default.
4. Exercise each operation with anonymous, USER, ADMIN, missing/wrong scope and
   machine credentials; verify OpenAPI completeness and signed-token regression.
5. Validate the real reduced grant through Auth -> Gateway -> all four APIs,
   run reactor/distributed CI, review the diff and record evidence in the Vault.

## Client -> audience -> scope -> role

| Client / actor | Audience | Scopes | Additional requirement |
| --- | --- | --- | --- |
| Explicitly enabled public PKCE client | `api://vellumhub` (configured API audience) | Requested subset of configured `VELLUM_AUTH_CLIENT_SCOPES` | Human USER or ADMIN |
| Human administrator using an explicitly configured client | Same audience | Only operator-allowed requested scopes; admin scopes are excluded from default registration | ADMIN for administrative operations |
| Autonomous machine | No human API grant | None of these human capabilities | Always denied by domain API policies, even if it presents human role strings |
| Auth -> User profile provisioner | Private `/internal/identity/profiles` | Not OAuth; existing separate service key | Existing private controller authentication and network boundary |

No client is enabled by default. The default enabled-client capability allowlist is
`user:read,catalog:read,catalog:personal:read,catalog:personal:write,catalog:requests:write,engagement:read,engagement:write,recommendation:read`.
Admin capabilities (`user:write`, `catalog:write`, `catalog:requests:read`) must be
explicitly added by an operator to the relevant client registration. A USER asking
for an admin capability still cannot use it: role and scope are independent.
No Client Credentials or Token Exchange client is introduced (#314/#316 remain deferred).
The common logical audience is retained for verified Token Relay; per-context scopes
prevent a User-only token from accessing Catalog, Engagement or Recommendation.

## Endpoint matrix

Paths below are Resource Server paths after Gateway prefix stripping. `USER/ADMIN`
means either human role, with the exact listed scope. Ownership remains #312/#313.

| Service | Method | Path | Required scope | Human role |
| --- | --- | --- | --- | --- |
| user | GET | `/users` | `user:read` | USER/ADMIN |
| user | GET | `/users/me` | `user:read` | USER/ADMIN |
| user | GET | `/users/{id}` | `user:read` | USER/ADMIN |
| user | PUT | `/users/{id}` | `user:write` | ADMIN |
| user | DELETE | `/users/{id}` | `user:write` | ADMIN |
| catalog | GET | `/books` | `catalog:read` | USER/ADMIN |
| catalog | GET | `/books/{id}` | `catalog:read` | USER/ADMIN |
| catalog | GET | `/books/{id}/cover` | `catalog:read` | USER/ADMIN |
| catalog | POST | `/books/bulk` | `catalog:read` | USER/ADMIN |
| catalog | POST | `/books/covers/bulk` | `catalog:read` | USER/ADMIN |
| catalog | POST | `/books` | `catalog:write` | ADMIN |
| catalog | POST | `/books/isbn` | `catalog:write` | ADMIN |
| catalog | POST | `/books/{id}/cover` | `catalog:write` | ADMIN |
| catalog | PUT | `/books/{id}` | `catalog:write` | ADMIN |
| catalog | DELETE | `/books/{id}` | `catalog:write` | ADMIN |
| catalog | POST | `/book-requests` | `catalog:requests:write` | USER/ADMIN |
| catalog | POST | `/book-requests/{id}/approve` | `catalog:write` | ADMIN |
| catalog | GET | `/book-requests` | `catalog:requests:read` | ADMIN |
| catalog | POST | `/book-progress/{id}/status` | `catalog:personal:write` | USER/ADMIN |
| catalog | PUT | `/book-progress/{id}/progress` | `catalog:personal:write` | USER/ADMIN |
| catalog | DELETE | `/book-progress/{id}` | `catalog:personal:write` | USER/ADMIN |
| catalog | GET | `/book-progress/reading-list` | `catalog:personal:read` | USER/ADMIN |
| catalog | POST | `/book-list` | `catalog:personal:write` | USER/ADMIN |
| catalog | PUT | `/book-list/{id}` | `catalog:personal:write` | USER/ADMIN |
| catalog | DELETE | `/book-list/{id}` | `catalog:personal:write` | USER/ADMIN |
| catalog | GET | `/book-list` | `catalog:personal:read` | USER/ADMIN |
| catalog | GET | `/book-list/{id}` | `catalog:personal:read` | USER/ADMIN |
| catalog | POST | `/book-list/{id}/books/{relatedId}` | `catalog:personal:write` | USER/ADMIN |
| catalog | DELETE | `/book-list/{id}/books/{relatedId}` | `catalog:personal:write` | USER/ADMIN |
| catalog | POST | `/book-list/membership/{id}` | `catalog:personal:write` | USER/ADMIN |
| catalog | PUT | `/book-list/membership/{id}/role` | `catalog:personal:write` | USER/ADMIN |
| catalog | DELETE | `/book-list/membership/{id}/{relatedId}` | `catalog:personal:write` | USER/ADMIN |
| catalog | GET | `/book-list/membership` | `catalog:personal:read` | USER/ADMIN |
| catalog | GET | `/book-list/membership/{id}` | `catalog:personal:read` | USER/ADMIN |
| engagement | POST | `/rating` | `engagement:write` | USER/ADMIN |
| engagement | PUT | `/rating/{id}` | `engagement:write` | USER/ADMIN |
| engagement | DELETE | `/rating/{id}` | `engagement:write` | USER/ADMIN |
| engagement | GET | `/rating/{id}` | `engagement:read` | USER/ADMIN |
| engagement | GET | `/rating/me` | `engagement:read` | USER/ADMIN |
| engagement | POST | `/reactions` | `engagement:write` | USER/ADMIN |
| engagement | PUT | `/reactions/{id}` | `engagement:write` | USER/ADMIN |
| engagement | GET | `/reactions/{id}` | `engagement:read` | USER/ADMIN |
| engagement | GET | `/reactions` | `engagement:read` | USER/ADMIN |
| recommendation | GET | `/recommendations` | `recommendation:read` | USER/ADMIN |

Anonymous exceptions remain GET `/files/books/{filename}` in Catalog and health/
Prometheus operational routes. Swagger/OpenAPI remains controlled by
`VELLUM_DOCS_ENABLED`. These exceptions do not turn domain reads public.
Private provisioning is protected by its higher-priority filter chain and controller.

Bulk book/cover POST operations are reads and require `catalog:read`, not write.
Catalog progress/list/membership capabilities are grouped as personal read/write;
Engagement ratings/reactions are grouped as engagement read/write. This deliberately
uses a small capability catalog instead of the older proposal's scope per entity.
Further separation is possible when clients require different privileges.

## HTTP and token contract

- 401 JSON `UNAUTHORIZED`: missing/invalid bearer, wrong issuer/audience/signature,
  expired token, ID token (`typ` differs from `at+jwt`), untrusted key.
- 403 JSON `FORBIDDEN`: authenticated token without the required scope or role,
  missing provenance claim, machine grant on a human route, or unmapped operation.
- ADMIN does not imply scopes; scope does not imply role. Neither overrides ownership.
- Each API revalidates JWT independently. No per-request central policy/identity lookup.
- The AS rejects non-authorization-code access-token customization. Human token
  provenance is based on the OAuth grant, not the shape of `sub` or a role name.
- Spring Authorization Server retains standard requested/registered scope validation
  and emits only authorized scopes. No scope is derived from USER/ADMIN.
- Existing low-cardinality HTTP status telemetry captures denials; no bearer, subject,
  credential, or per-user metric label is introduced by this change.

OpenAPI defines an OAuth2 Authorization Code scheme using the configured issuer,
with one explicit required scope per domain operation. Postman obtains tokens through
PKCE and must request the scopes listed for its workflow. An `openid profile` token
alone is deliberately insufficient for domain operations.

## Setup / rollback

For a development test client, enable it with existing redirect, post-logout and PKCE
settings. Set `VELLUM_AUTH_CLIENT_SCOPES` to the explicit comma-separated capability
allowlist (do not use `*`). Unknown scopes fail startup. A persisted registration with
different scopes also fails startup, avoiding silent privilege expansion on restart.
Recreate disposable dev/QA OAuth state or explicitly reconcile the registration and
invalidate old authorizations. No production-user migration is needed for this project.
After upgrade, obtain a fresh token with explicit scopes and the provenance claim;
previously issued tokens without that claim receive 403 until replaced/expired.
Rollback code/configuration together and recreate disposable OAuth state as needed;
never disable signature/audience/type verification to make old tokens work.

## Validation and review

`OAuthScopeBoundaryTest` runs the real security chain in each domain with an isolated
204 probe handler, covering every listed operation with anonymous, USER/ADMIN,
wrong/missing scopes, no provenance and client credentials, including machine tokens
that carry forged-looking human role strings. This proves route policy and error
boundaries, not persistence or ownership. Separate tests check real controller
annotations and existing controller/security regression behavior.

`HumanApiAccessTest` exercises the production converter: roles never create scopes,
scopes never create roles, and unrelated resources cannot reuse a grant.
`AccessTokenClaimsTest` checks AS provenance/roles; signed-JWT suites retain ID-token,
issuer/audience/signature/JWKS regression coverage. The distributed OIDC smoke requests
only `openid user:read`, confirms that exact granted subset, succeeds against User,
and receives 403 from Catalog, Engagement and Recommendation with the same token.
The full existing reactor, distributed E2E, Postman, CodeQL and image checks remain gates.

Review scope: least privilege, ordering of bulk-read exceptions before Catalog writes,
private provisioning chain precedence, method RBAC activation in User, JWT provenance,
requested scope preservation, public-cover boundary, safe errors and configuration drift.

Known pre-existing issue found during inventory: RatingController declares both
GET `/{userId}` and GET `/{bookId}`. Those templates collide for UUID requests.
This change does not claim to fix that domain contract; both require `engagement:read`.
The unambiguous `/rating/me` flow is used in distributed security validation.
Ownership/IDOR and granular list membership remain explicitly #312/#313.
This document describes branch behavior until merge and CI evidence confirm delivery.

---
name: backend-security
description: Review Java backend authentication, authorization, token handling and data exposure with negative-path verification.
---

# backend-security

## Trigger
AuthN/AuthZ, OAuth2/OIDC, JWT/JWKS, sessions, credentials, endpoint scope, secrets or sensitive data.

## Procedure
1. Draw the trust boundaries and actor types: anonymous, authenticated, machine account, privileged account, other organization.
2. For tokens verify issuer, audience, algorithm allow-list, signature, expiry, key rotation, intended token type and applicable session/revocation constraints. PKCE protects code exchange; it does not replace server-side authorization.
3. Enforce authorization at use cases and query scope, not only UI, route guards or client-provided identifiers. Default deny; test cross-context access, privilege escalation and revoked sessions where applicable.
4. Audit object-level access, mass assignment, pagination leakage, error responses, rate limiting and consistent status codes.
5. Check CSRF/cookie, CORS and forwarded headers only against actual deployment model; avoid globally permissive settings.
6. Never log credentials, cookies, bearer tokens or user-sensitive objects. Avoid committing secrets or test fixtures copied from production.
7. Review backward compatibility for legacy clients without silently preserving insecure access.
8. Execute negative tests for every distinct actor/scope boundary, and distinguish CI evidence from production enforcement.

## Output
Threat boundary, test matrix, findings with severity and references, residual risks.

## Reference
Adapted from https://github.com/github/awesome-copilot/tree/main/skills/spring-security/SKILL.md and https://github.com/github/awesome-copilot/tree/main/skills/security-review/SKILL.md.

---
name: security-authorization-review
description: Use for OAuth2/OIDC, JWT, gateway token relay, account scoping and authentication or authorization changes.
---
# Security and authorization review

1. Identify actor, resource, tenant/account, trusted issuer, intended audience and authority of each token before changing validation.
2. Check signature/JWKS, allowed algorithms, expiry, issuer/audience, token type, lifecycle, redirect/PKCE and cookie/session boundaries as applicable.
3. Confirm authentication is distinct from authorization; validate ownership/scope at entry and within data access where required.
4. Test cross-user and cross-service access, missing/forged claims, expired/revoked sessions, token replay, failure to resolve JWKS and denial paths.
5. Avoid exposing tokens, credentials, keys, PII or security-sensitive claim values through logs, exceptions, fixtures or PR descriptions.
6. Validate compatibility with existing clients and current security tests; never disable checks to fix an integration failure.
7. Report security assumptions and residual threats separately from passing tests.

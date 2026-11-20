---
name: backend-security
description: Review or change VellumHub authentication and authorization — Auth (Spring Authorization Server, RS256 at+jwt, JWKS, sessions, Google OIDC federation and account linking), Gateway JWT relay and rate limits, OAuth scopes and USER/ADMIN roles, resource ownership (owner/member/PUBLIC, 404 for hidden resources), internal provisioning keys, Kafka service identities and any data exposure in responses or logs. Use it whenever a change touches SecurityConfig, lib/jwt-security, services/auth-service, ownership checks, error bodies or logging of user data, even if the user frames it as a bug fix or refactor.
---

# Backend security (VellumHub)

Security regressions here are usually silent: a request that should fail starts succeeding, or a value that should stay server-side shows up in a body or a log. So every change needs a negative test, not only a happy path.

## Trust model to keep in mind
- **Auth is the only issuer.** It signs RS256 `typ=at+jwt` access tokens with one configured key (`kid`), publishes JWKS and owns identities, Argon2id credentials, sessions and Google bindings. Google ID tokens are never API bearers. Diagrams: `docs/architecture/flows/api-request-authorization.md`, `flows/google-federation.md`, `states/auth-identity-lifecycle.md`.
- **Resource Servers verify locally.** Gateway and each domain service check signature, `kid`, issuer, audience, `typ`, expiry and scope through `lib/jwt-security`; no per-request call to Auth or User. Logout does not revoke issued tokens, which stay valid until `exp`.
- **Actors:** anonymous, USER, ADMIN (ADMIN never implies scopes), other users (ownership), Auth calling User's internal provisioning endpoint with `VELLUM_INTERNAL_PROVISION_KEY`, and Kafka service principals (`docs/KAFKA_SERVICE_IDENTITIES.md`).

## Review procedure
1. Identify actor, resource, owning service, token issuer and audience for each changed path. Authentication and authorization are separate questions; answer both.
2. Check ownership inside the use case or query, from the verified `sub`, never from a client-supplied ID or header. The Gateway strips `X-User-*` identity headers; downstream code must not rely on them anyway.
3. Keep error semantics consistent: 401 invalid/missing token, 403 missing scope/role or forbidden mutation, 404 for hidden or foreign private resources (identical body), 429 for throttling. Error bodies carry no SQL, constraint, parser or class names (`GlobalExceptionHandlerLeakTest`).
4. Never log tokens, cookies, passwords, request bodies with proofs, e-mails or other personal values; database messages count, because PostgreSQL includes conflicting values. Denial logs stay low-cardinality (`resource`, `decision`).
5. For token or session changes, test expired, tampered, wrong-key, wrong-issuer/audience, wrong `typ`, replayed and revoked cases, plus JWKS fetch failure. PKCE protects the code exchange; it does not replace server-side authorization.
6. For Google federation, linking happens only through the session-bound nonce flow; never link accounts by matching e-mail.
7. CORS, CSRF, cookies and forwarded headers: evaluate against the real deployment model (`VELLUM_TRUSTED_PROXY_CIDRS` empty means TCP peer). Do not make them globally permissive to fix an integration.
8. Never disable a check to make a test pass, and keep secrets and production data out of fixtures, commits and PR descriptions.

## Output
Threat boundary, actor × operation test matrix with results, findings by severity with file/line, and residual risks that tests do not prove (for example live Google or HTTPS behaviour).

Adapted from github/awesome-copilot `spring-security` and `security-review`; merges the former `security-authorization-review` supplement.

---
id: vh-api-request-authorization
slug: api-request-authorization
type: sequence-diagram
system: vellumhub
status: implemented-in-repository
related: [vh-service-gateway, vh-service-auth, vh-identity-onboarding, vh-c4-containers]
---

# Sequence — authenticated API request (Gateway → Resource Server)

Auth is the only issuer of RS256 `at+jwt` access tokens. The Gateway and every domain service verify the token **independently** against the Auth JWKS; no request path calls Auth or User to validate a bearer. Scopes and roles allow an operation; **ownership** is decided inside the use case from the verified `sub`.

```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant G as VH-GTW Gateway
    participant R as Gateway Redis
    participant A as VH-AUT Auth JWKS
    participant S as Domain service (USR/CAT/ENG/REC)
    participant D as Service PostgreSQL
    Client->>G: /api/v1/** + Authorization: Bearer at+jwt
    G->>G: CORS allowlist, strip X-User-* identity headers
    G->>G: Deny /api/v1/internal/** and /users/internal/**
    G->>A: Fetch JWKS (cached, only on unknown kid)
    G->>G: Verify RS256, kid, iss, aud, typ, exp, scope
    alt Invalid or missing token
        G-->>Client: 401
    else Valid token
        G->>R: Rate limit (users: IP 5/10, catalog, engagement: user 30/60, recommendations: user 20/40)
        alt Bucket empty
            G-->>Client: 429
        else Allowed
            G->>S: Route with the same bearer
            S->>S: Verify JWT again (RS256 only, issuer, audience, scope)
            alt Scope or role missing
                S-->>Client: 403
            else Authorized operation
                S->>D: Use case scoped by verified sub (owner, member, PUBLIC)
                alt Resource missing or not visible to sub
                    S-->>Client: 404 with generic body
                else Visible
                    D-->>S: Rows
                    S-->>Client: 2xx
                end
            end
        end
    end
```

| Rule | Where it is enforced |
| --- | --- |
| Token validation (RS256, `kid`, issuer, audience, `typ=at+jwt`) | [`lib/jwt-security`](../../../lib/jwt-security/src/main/java/com/vellumhub/security/jwt/JwtAccessToken.java) and each service `SecurityConfig` |
| Scopes per route (`catalog:read`, `catalog:write`, `engagement:*`, `user:*`) | [OAuthScopes](../../../lib/jwt-security/src/main/java/com/vellumhub/security/jwt/OAuthScopes.java) · [scope/RBAC matrix](../../SECURITY_SCOPES_RBAC.md) |
| Header stripping and internal-route denial | [UntrustedIdentityHeadersFilter](../../../services/gateway-service/src/main/java/com/vellumhub/gateway_service/config/security/UntrustedIdentityHeadersFilter.java) · [GatewaySecurityConfig](../../../services/gateway-service/src/main/java/com/vellumhub/gateway_service/config/security/GatewaySecurityConfig.java) |
| Rate limits and client key | [application.yml](../../../services/gateway-service/src/main/resources/application.yml) · [RateLimiterConfig](../../../services/gateway-service/src/main/java/com/vellumhub/gateway_service/config/rate/RateLimiterConfig.java) (`VELLUM_TRUSTED_PROXY_CIDRS` empty ⇒ TCP peer) |
| Book lists: PUBLIC, owner or member; private lists hidden as 404 | [BookList](../../../services/catalog-service/src/main/java/com/vellumhub/catalog_service/module/book_list/domain/model/BookList.java) · [BookListSpecificationProvider](../../../services/catalog-service/src/main/java/com/vellumhub/catalog_service/module/book_list/infrastructure/persistence/specification/BookListSpecificationProvider.java) |
| Ratings/reactions: owner only, foreign or missing ⇒ identical 404 | Engagement `GlobalExceptionHandler` (`handlePrivateResourceHidden`) |
| Recommendations: no client-chosen user ID | Recommendation controller and read use case use the principal |
| Error bodies never carry SQL, constraint or parser text | `GlobalExceptionHandler` in each service · `GlobalExceptionHandlerLeakTest` |

**Signing key:** Auth loads one RSA key pair (≥ 2048 bits, configured `kid`) and publishes it through JWKS; key rotation is not implemented. **Logout** does not revoke issued API tokens; they stay valid until `exp`. Documentation (`VELLUM_DOCS_ENABLED`) and Actuator endpoints must stay off public ingress.

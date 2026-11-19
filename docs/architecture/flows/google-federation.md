---
id: vh-google-federation
slug: google-federation
type: sequence-diagram
system: vellumhub
status: implemented-in-repository
related: [vh-service-auth, vh-identity-onboarding, vh-auth-identity-lifecycle]
---

# Sequence — Google sign-in and manual account link (Auth)

Google is an upstream OIDC provider only, enabled with `vellum.google.enabled=true`. Auth binds the verified `(issuer, subject)` to one VellumHub UUIDv7 and keeps issuing its own access tokens. **Email is never a link key**, and a Google ID token is never accepted as an API bearer. The Gateway does not route these Auth endpoints.

```mermaid
sequenceDiagram
    autonumber
    actor Reader
    participant A as VH-AUT Auth
    participant GG as Google OIDC
    participant AD as Auth PostgreSQL
    participant U as VH-USR User (private)
    rect rgba(127,127,127,0.08)
    Note over Reader,U: Sign-in with Google
    Reader->>A: GET /oauth2/authorization/google
    A->>GG: Authorization request (state, nonce)
    GG-->>A: Callback /login/oauth2/code/google
    A->>A: Validate ID token: signature, iss, aud, exp, nonce
    A->>AD: Resolve binding by (issuer, subject)
    alt Subject already bound
        AD-->>A: Existing UUID
    else New subject and email not used locally
        A->>AD: Insert identity PENDING_PROFILE + binding
        A->>U: Provision profile (same UUID, idempotent)
        A->>AD: Mark ACTIVE
    else Email belongs to an unlinked local account
        A-->>Reader: Rejected (no automatic link)
    end
    A-->>Reader: Auth session, then OAuth2 code + PKCE ⇒ RS256 at+jwt
    end
    rect rgba(127,127,127,0.08)
    Note over Reader,AD: Link Google to an existing local account
    Reader->>A: POST /v1/identity/google/link/challenge {currentPassword} + CSRF
    A->>AD: Store nonce for hashed session ID (5 min, replaces older)
    A-->>Reader: {nonce}
    Reader->>GG: Sign in with nonce, same client ID
    GG-->>Reader: Fresh ID token
    Reader->>A: POST /v1/identity/google/link {idToken} + CSRF
    A->>AD: Consume challenge atomically (DELETE … RETURNING)
    A->>A: Verify aud, nonce, issuer, email_verified, subject
    alt Subject bound to another user, or user already has another Google binding
        A-->>Reader: 409
    else Proof invalid or expired
        A-->>Reader: 403
    else Valid
        A->>AD: Insert unique (issuer, subject) for current UUID
        A-->>Reader: 204
    end
    end
```

| Control | Implementation |
| --- | --- |
| Federated login and canonical principal | [GoogleFederationConfig](../../../services/auth-service/src/main/java/com/vellumhub/auth/config/GoogleFederationConfig.java) · [FederatedIdentityService](../../../services/auth-service/src/main/java/com/vellumhub/auth/identity/FederatedIdentityService.java) |
| Link challenge and proof | [GoogleIdentityLinkController](../../../services/auth-service/src/main/java/com/vellumhub/auth/identity/GoogleIdentityLinkController.java) · [GoogleLinkChallengeRepository](../../../services/auth-service/src/main/java/com/vellumhub/auth/identity/GoogleLinkChallengeRepository.java) |
| Login/link throttle: 20 attempts per minute per remote address, `429` + `Retry-After` | [AuthenticationThrottleFilter](../../../services/auth-service/src/main/java/com/vellumhub/auth/config/AuthenticationThrottleFilter.java) |
| Sessions: JDBC, 30 min idle, 12 h absolute; logout does not revoke issued JWTs | [SessionLifecycleConfig](../../../services/auth-service/src/main/java/com/vellumhub/auth/config/SessionLifecycleConfig.java) · [AbsoluteSessionLifetimeFilter](../../../services/auth-service/src/main/java/com/vellumhub/auth/config/AbsoluteSessionLifetimeFilter.java) |

There is no unlink endpoint. CI proves the protocol with a controlled signed OIDC upstream (`FederatedOidcLifecycleIntegrationTest`); a live Google browser flow behind HTTPS is not evidenced. Local setup and secrets: [Auth service](../../../services/auth-service/README.md).

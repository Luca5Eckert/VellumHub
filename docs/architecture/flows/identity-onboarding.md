---
id: vh-identity-onboarding
slug: identity-onboarding
type: sequence-diagram
system: vellumhub
status: implemented-in-repository
related: [vh-service-auth, vh-service-user, vh-c4-context]
---

# Sequence — Auth registration and User profile provisioning

The current topology has a **separate Auth issuer**. Auth provisions a User profile using the same canonical UUID; User is not a fallback issuer. Google federation is an optional identity provider path, not a replacement for VellumHub API tokens.

```mermaid
sequenceDiagram
    autonumber
    actor Reader
    participant A as VH-AUT Auth
    participant AD as Auth PostgreSQL
    participant U as VH-USR User (private API)
    participant UD as User PostgreSQL
    Reader->>A: POST /v1/identity/register
    A->>A: Validate input, allocate canonical UUIDv7, hash password with Argon2id
    A->>AD: Persist pending identity and provisioning intent
    A->>U: Provision profile (UUID, display name, email, idempotent)
    U->>UD: Upsert / confirm profile by UUID
    U-->>A: Profile confirmed
    A->>AD: Activate identity after profile confirmed
    A-->>Reader: Registration result
    opt Provisioning unavailable
        Note over A,UD: Pending Auth identity is durable, retry keeps the same UUID
    end
    Reader->>A: OAuth2 Authorization Code + PKCE
    A->>AD: Authenticate / persist browser and grant state
    A-->>Reader: RS256 access token, typ=at+jwt
    Note over Reader,A: API JWT is verified locally by Gateway and domain services
```

**Evidence / ownership:** [Auth service](../services/auth.md) · [User service](../services/user.md) · [Auth implementation](../../../services/auth-service/README.md) · [authorization sequence](api-request-authorization.md) · [identity states](../states/auth-identity-lifecycle.md). The sequence abstracts provider/browser redirects; it does **not** claim that a live Google callback, HTTPS reverse proxy or real frontend has been deployed.

# SEC-02 — Google external identity ownership

Issue [#303](https://github.com/Luca5Eckert/VellumHub/issues/303) protects account ownership without introducing a new OAuth2 Authorization Server.

## Why this is needed

Before SEC-02, verified Google ID tokens were resolved through `findByEmail`. Matching email is **not** a proof that the local account belongs to the external subject. Provider email may change. Existing accounts with the same email require explicit account-owner verification and must never be automatically linked.

## Contracts

| Path via Gateway | Method | Auth | Behavior |
| --- | --- | --- | --- |
| `/api/v1/auth/google` | POST | Public | Verify Google ID token and resolve/create by `issuer+subject`, legacy collision → 409 |
| `/api/v1/auth/google/link` | POST | Bearer JWT + current password + verified Google ID token | Link to current local UUID; repeat same link → 204; conflict → 409 |
| `/api/v1/auth/google/link` | DELETE | Bearer JWT + current password | Unlink provider; repeat → 204; passwordless account → 403 |

All other protected APIs continue to validate the legacy HS256 JWT. Google ID tokens are accepted only by the external identity verification adapter; **never** by Resource Servers as API access tokens.

Request bodies:

```json
{"externalToken":"<fresh Google ID token>","currentPassword":"<local account password>"}
```

```json
{"currentPassword":"<local account password>"}
```

Both proof-bearing requests must use HTTPS outside isolated local development. Never log their bodies, tokens or passwords.

## Verified identity invariants

- Verify provider cryptographic signature, Google issuer, allowed OAuth client ID audience, validity time, non-empty subject, email and `email_verified=true`.
- Normalize both Google issuer aliases to `https://accounts.google.com`. Provider `sub` remains stable; email is **not** a key.
- DB unique `(issuer,subject)`, unique `(user_id,issuer)` and FK to `tb_users.id`. An external identity cannot be attached to another account even when emails match.
- Binding and user creation share one PostgreSQL transaction and are serialized by transaction-scoped advisory locks; constraints remain a final line of defense.
- If a Google identity is already linked and its email changes, use the original local user UUID and stored local profile email. No automatic email update.
- For an unbound Google identity with an email already present in User Service, reject the attempt and guide the user to local-password login and explicit linking. No fallback to email ownership.
- New Google-only accounts have `password=NULL`; local password login is unavailable. They cannot remove their only Google login by unlinking.
- Signed JWT remains valid until expiration after unlink; preventing immediate revocation requires separate session/revocation design.

## Legacy migration and recovery

Flyway V2 expands `tb_users.password` nullability and creates binding and audit tables. **No migration inserts bindings for existing users based on email**, because the historical Google subject was not recorded and cannot be derived reliably. Password accounts can log in as before and explicitly link their Google identity after current-password verification. Existing accounts that only used Google and have no usable local password require a manual identity-recovery process with independently verified ownership; do not grant account control on matching email alone.

Persisted audit records contain action, local UUID, issuer, hash of external subject and timestamp. They never contain JWT, raw Google ID tokens or passwords. Operational logs and traces must not contain request bodies.

## Verification and rollout gates

Run a user-service Maven test suite, Flyway/Testcontainers PostgreSQL tests, endpoint security integration checks, and full Maven/CI before any cutover. Test identity under both accepted Google issuer spellings, unverified email, invalid audience/signature/expiry, conflicting email or subject, repeat login, duplicate link, password denial, cross-account association, soft-deleted/disabled users and concurrent logins.

The repository tests prove persistence and transaction behavior with verified identity fixtures. **A real cryptographically signed Google ID token through Gateway → User remains a separate integration evidence requirement** before production use.

No provider keys or JWT algorithm changes are included. On a bad rollout, disable new account-linking traffic at the edge, preserve database contents and restore compatible immutable application images only after accounting for the new schema; **Flyway V2 is an additive forward-only migration**, not a reversible table-drop. Dropping bindings would orphan identities and violate auditability. Use a forward fix or coordinated restore to a consistent backup if rollback of data is indispensable.

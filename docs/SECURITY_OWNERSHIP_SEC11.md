# SEC-11 — User/Catalog resource ownership (#312)

## Security model

Auth is the only issuer. The canonical UUIDv7 `sub` from a verified access token
is used as the calling user ID. Scopes and roles from SEC-10 do not confer access
to an arbitrary resource. Ownership is evaluated in application/use-case logic.
No synchronous lookup from Catalog to Auth/User, no new policy service,
no frontend and no production-user migration are introduced.

| Operation | Policy |
| --- | --- |
| `GET /users/me` | Authenticated subject's own profile |
| `GET /users/{id}` | ID must equal JWT `sub`; unrelated/missing IDs return identical 404 |
| `GET /users` | ADMIN plus `user:read` |
| `PUT/DELETE /users/{id}` | Remain explicitly ADMIN-only |
| `GET /book-list/{id}` | PUBLIC, owner or current member; hidden PRIVATE lists return 404 |
| `GET /book-list` | DB visibility filter PUBLIC OR owner OR member remains mandatory |
| `GET /book-list/membership/{id}` | Member in question, list owner or list ADMIN |
| `GET /book-list/membership?bookListId=...` | List owner or member, not external public-list viewers |
| Membership writes | OWNER role cannot be assigned via add-member or role update |
| Book-progress endpoints | Subject-derived user ID and existing user+book scoped operations retained |

## Error semantics

- 401: missing or invalid token; 403: insufficient scope or role.
- 404: hidden/missing profile, private list or membership with a generic body.
- 403: valid subject cannot modify a list or membership; generic response.
- 400: invalid business command such as adding a second OWNER.

## Verification and release

Run `mvn -B -pl services/user-service,services/catalog-service -am test`
and the full reactor `verify` plus OAuth boundary and distributed workflow checks.
Unit tests cover user-A/user-B membership and public/private list access; existing
book-progress/outbox tests must stay green. A real PostgreSQL/Testcontainers
cross-subject verification remains a merge-readiness check unless CI proves it.
No schema migration is involved. Rollback is a code/config revert.

This does not implement ownership in Engagement/Recommendation (#313), which
remains a separate backlog item; Kafka ACLs (#315) are independent.

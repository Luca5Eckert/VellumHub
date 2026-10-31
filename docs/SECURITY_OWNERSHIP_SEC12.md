# SEC-12 — Ownership de Engagement e Recommendation (#313)

## Scope and source of truth

Authenticated human endpoints enforce SEC-10 scopes and roles at the Resource
Server boundary. Within application use cases the verified RFC 9068 JWT `sub`
(UUID) is the only user identity; clients never choose the owner of mutations.

| Operation | Policy |
| --- | --- |
| POST /rating | Create as authenticated subject, ignoring any supplied owner |
| PUT/DELETE /rating/{ratingId} | Owner only; missing/foreign record returns identical 404 |
| GET /rating/{userId} | Only when userId equals JWT subject; denied before repository access |
| GET /rating/me | Same subject-scoped policy |
| GET /rating/books/{bookId} | Public ratings/reviews of a book (authenticated, engagement:read); explicitly allowed |
| POST /reactions | Create as authenticated subject |
| PUT /reactions/{id} | Owner only; missing/foreign record returns identical 404 |
| GET /reactions/{id} | Owner only, checked in use case; missing/foreign returns 404 |
| GET /reactions | Only authenticated subject's reactions |
| GET /recommendations | No client-selected userId; both controller and read use case require canonical principal |

## Intentional HTTP changes

`GET /rating/{userId}` and `GET /rating/{bookId}` had identical URL shapes
(UUID IDs). The book-centric operation is now
`GET /rating/books/{bookId}`; the user-centric legacy path remains supported
and is private. Postman is regenerated to represent the unambiguous endpoint.
The book-centric read is an explicit visibility exception: reviews in its response
may include displayable rating metadata. Future privacy rules for review bodies
should be separately specified instead of silently changing this public surface.

- 401: missing/invalid bearer; 403: missing scope/role, or authenticated user
  trying to request someone else's recommendation through the use case.
- 404: missing or unauthorized rating/reaction (uniform response body).
- Denial logging is low-cardinality, with `resource` and `decision` only;
  never log token, JWT subject, requested object ID, email, review or recommendation.
- Existing event/outbox and recommendation fallback paths are unchanged.

## Verification and rollback

SEC-12 unit tests exercise A/B access, no persistence query on denied bulk reads,
no save/outbox side effect on denied mutation, legitimate subject and popular
recommendation fallback. OAuth route boundary tests include the new public
book-ratings route. The full Maven reactor, PostgreSQL/Testcontainers regression,
CodeQL, image builds, distributed Kafka and OpenAPI/Postman drift checks must be
green on the final PR SHA before merge.

Rollback: revert this PR as a unit; no schema migration or real-user migration.
There is no additional request-path fan-out, no query to User/Catalog/Engagement
from Recommendation, and no ranking/vector change.

# Improvements & Engineering Notes

## 1. README Improvements

1. **Gateway route prefixes are outdated/inconsistent in docs**
   - **BEFORE:** README routing map says `/api/users/**`, `/api/catalog/**`, etc.
   - **AFTER:** Document actual gateway routes from `gateway-service/src/main/resources/application.yml`: `/api/v1/users/**`, `/api/v1/auth/**`, `/api/v1/catalog/**`, `/api/v1/engagement/**`, `/api/v1/recommendations/**`.
   - **WHY:** Prevents integration confusion and broken client calls.

2. **“Internal services no longer handle auth independently” is inaccurate**
   - **BEFORE:** README claims auth is handled only at gateway.
   - **AFTER:** Clarify that all downstream services still enforce JWT validation in their own `SecurityConfig` classes; gateway is an additional edge layer.
   - **WHY:** Accurately describes defense-in-depth and avoids false assumptions in zero-trust deployments.

3. **ECST claim “no synchronous inter-service coupling” is too broad**
   - **BEFORE:** README implies fully asynchronous inter-service communication.
   - **AFTER:** Clarify scope: recommendation query path is local-read-model based, but recommendation still depends on catalog metadata replication and may use synchronous integration in some flows.
   - **WHY:** Improves credibility and avoids overclaiming architecture purity.

4. **Topic contract table does not match implementation**
   - **BEFORE:** Mentions events like `created-user-profile`, `updated-user-profile`, `created-book-feature`, `updated-book-feature`.
   - **AFTER:** Mark these as “planned/not implemented” unless code actually publishes them.
   - **WHY:** Keeps event contract documentation trustworthy for consumers.

5. **Document known event naming mismatch**
   - **BEFORE:** README and retry config reference `updated-book-progress`.
   - **AFTER:** Add explicit note that catalog currently publishes `updated-progress` in `UpdateBookProgressHandler`/`DefineBookStatusHandler`.
   - **WHY:** This mismatch is currently a production correctness risk and should be visible to operators/developers.

6. **Update Feign/Ribbon references in docs**
   - **BEFORE:** `docs/SERVICE_COMMUNICATION.md` references Ribbon and a Feign adapter path that no longer exists.
   - **AFTER:** Either provide current implementation references or remove obsolete guidance.
   - **WHY:** Reduces onboarding friction and avoids dead-end debugging.

---

## 2. Architecture Improvements

1. **Fix event contract governance**
   - Introduce shared, versioned event contracts (schema registry or explicit JSON schema files with compatibility policy).
   - Trade-off: additional process overhead and migration planning.
   - Do **not** apply full schema-registry complexity if team/project scale remains small and contracts are changing rapidly; in that case start with strict contract tests + schema files in-repo.

2. **Standardize topic and consumer-group naming conventions**
   - Current style mixes kebab-case and snake_case (`created-rating`, `create_user_preference`, `recommendation-service` vs `recommendation_service_group`).
   - Trade-off: migration requires coordinated producer/consumer rollout.
   - Do **not** rename aggressively in production without dual-read/dual-write migration window.

3. **Strengthen write consistency pattern for DB + Kafka**
   - Catalog and engagement publish Kafka events inside/near transactional operations but without explicit outbox pattern.
   - Adopt transactional outbox for critical state-transfer events.
   - Trade-off: more tables/processes and relay component.
   - Do **not** apply for low-criticality events where occasional loss is acceptable and compensations exist.

4. **Separate retry/DLT ownership per service**
   - Engagement DLT consumer uses group id `recommendation-service-dlt-group`; should be service-local.
   - Trade-off: more consumer-group management overhead.
   - Do **not** share DLT group IDs across services unless intentionally centralizing DLT processing.

5. **Preserve ranking order end-to-end**
   - Recommendation IDs are ranked in SQL, then loaded with `findAllById(List<UUID>)`, which does not guarantee order.
   - Add deterministic ordering strategy in persistence layer.
   - Trade-off: custom query/code path complexity.
   - Do **not** skip this if product depends on deterministic recommendation ranking.

---

## 3. Code-Level Improvements

1. **Resolve progress topic mismatch**
   - Producers: catalog sends `updated-progress`.
   - Consumer/retry config: recommendation listens/retries `updated-book-progress`.
   - Impact: progress learning path can silently fail.

2. **Make consumer updates idempotent**
   - Current profile updates apply additive learning on each event; duplicate delivery can bias vectors/engagement scores.
   - Add event IDs + dedup store (or semantic idempotency keys) before applying updates.

3. **Handle missing dependencies in consumers more safely**
   - `UpdateUserProfileWithRatingUseCase`/`UpdateBookProgressUseCase`/`ReactionChangedUseCase` throw when `BookFeature` missing; retries may exhaust into DLT during startup lag.
   - Add explicit transient-vs-permanent failure handling and optional deferred processing.

4. **Avoid insecure fallback JWT secret defaults in gateway**
   - Gateway `application.yml` contains a long default `secret-key` fallback.
   - Remove insecure defaults and fail fast if secret is missing/invalid base64.

5. **Fix ambiguous engagement rating routes**
   - `GET /rating/{userId}` and `GET /rating/{bookId}` overlap in `RatingController`.
   - Make paths explicit (`/rating/user/{userId}`, `/rating/book/{bookId}`).

6. **Align Kafka consumer group configuration**
   - `spring.kafka.consumer.group-id=recommendation-group`, but many listeners hardcode `recommendation-service`/`recommendation_service_group`.
   - Consolidate to config-driven group IDs unless per-listener isolation is intentional and documented.

7. **Reassess logging levels**
   - Gateway logs `org.springframework.cloud.gateway: TRACE` in default config; likely too noisy for production and costly under load.
   - Move to INFO/WARN in production profile.

---

## 4. Missing Production Concerns

1. **Schema evolution policy**
   - No explicit compatibility matrix or contract-version handling strategy for Kafka events.

2. **Backpressure and lag handling**
   - DLT logging exists, but no clear strategy for sustained lag spikes, replay tooling, or automated alerting thresholds tied to business impact.

3. **Replay/reprocessing workflows**
   - DLT is observable, but no implemented operator workflow to reprocess safely after fixes.

4. **Cold-start race handling**
   - First recommendation request may happen before profile/book features are ready; fallback exists, but there is no SLO/metric tracking for this condition.

5. **Ranking quality monitoring**
   - No offline/online quality metrics (CTR, conversion, novelty/diversity, calibration drift) documented for the 70/30 heuristic.

6. **Model drift and embedding lifecycle**
   - No model versioning, re-embedding migration plan, or rollback strategy when embedding model changes.

7. **Security hardening at edge**
   - IP resolution trusts `X-Forwarded-For` directly; in untrusted proxy chains this can be spoofed and affect rate limiting.

8. **Ordering assumptions**
   - No explicit partition key and ordering guarantees documented for cross-event flows affecting same user/book aggregate.

---

## 5. Risk Assessment

| Risk | Severity | Why |
|---|---|---|
| Progress events not consumed due to topic mismatch (`updated-progress` vs `updated-book-progress`) | High | User profile learning misses progress signal; recommendation quality drifts silently |
| Duplicate event deliveries inflate profile updates | High | Non-idempotent additive updates can bias vectors and engagement score over time |
| Ranked recommendation order lost after `findAllById` | Medium | Returned list can diverge from computed rank relevance |
| Misleading README claims about event contract and coupling | Medium | Integration teams may build against non-existent/incorrect assumptions |
| Shared/misnamed DLT consumer group in engagement | Medium | Operational confusion and possible consumer ownership issues |
| Default gateway TRACE logging | Medium | Increased overhead/log volume under load |
| Ambiguous rating endpoint path variables | Medium | Route ambiguity and potential incorrect handler behavior |
| Insecure default JWT secret fallback in gateway config | High | Misconfiguration can degrade authentication security in deployed environments |

---

## 6. Suggested Next Steps

1. **Fix the progress topic contract immediately** (`updated-progress` vs `updated-book-progress`) and add contract test coverage.
2. **Implement idempotency for profile-affecting events** (rating/reaction/progress/preference) with event IDs and dedup persistence.
3. **Correct README/docs to match real implementation** (gateway paths, auth boundaries, event table, Feign/Ribbon guidance).
4. **Guarantee recommendation output ordering** by preserving ranked ID order in repository retrieval.
5. **Remove insecure default JWT secret and enforce startup validation** for required secrets.
6. **Resolve ambiguous rating routes** by explicit URI segmentation.
7. **Standardize topic and consumer-group naming conventions** and document migration.
8. **Fix engagement DLT group ID ownership** and make DLT handling service-specific.
9. **Add integration tests for Kafka flows and failure paths** (duplicate events, missing book features, retry-to-DLT, recovery).
10. **Introduce schema/version governance for Kafka events** (at least in-repo schemas + compatibility tests).


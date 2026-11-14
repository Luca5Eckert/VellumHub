# Schema-04 — acceptance and evidence register (#350)

Status: code in [PR #361](https://github.com/Luca5Eckert/VellumHub/pull/361) / [PR #362](https://github.com/Luca5Eckert/VellumHub/pull/362) merged; extended lab rehearsal tested in [PR #364](https://github.com/Luca5Eckert/VellumHub/pull/364). Verified on 14 November 2026. **CI is not evidence of a production rollout.**

## Reproducible evidence

| Requirement | Mechanism | Observed evidence |
| --- | --- | --- |
| Distinct writer subjects and compatibility across evolution | Registry manifest, schema tests and Kafka Avro encoding with approved IDs; `TopicRecordNameStrategy` | [Registry and contract smoke](https://github.com/Luca5Eckert/VellumHub/actions/runs/43597844952); [canonical real streams](https://github.com/Luca5Eckert/VellumHub/actions/runs/43615689013) |
| v1/v2 read-model semantics | Real Apicurio registration, compatible v2 bytes sent to v1 deployed readers, same SQL checkpoint and UserProfile/ReadingSession projection | [5 distributed publisher/consumer jobs](https://github.com/Luca5Eckert/VellumHub/actions/runs/43615689013) and [previous merged phase gate](https://github.com/Luca5Eckert/VellumHub/actions/runs/43602931693) |
| Duplicate/gap, baseline, delete, DLT | Real distributed consumers verify duplicate no-op, gaps stay at prior revision, raw Avro bytes and source metadata on DLT, baseline-only semantics, RatingDeleted | [Recommendation/Engagement jobs](https://github.com/Luca5Eckert/VellumHub/actions/runs/43615689013); Book/rebalance guardrails already addressed in [AFF-02 #324](https://github.com/Luca5Eckert/VellumHub/issues/324) |
| Reading freeze, drain, JSON→Avro→JSON rollback | Pre-V5 PostgreSQL JSONB; separate Reading writer gate; Outbox published/high-watermark counts, source Kafka JSON/Avro bytes and approved schema ID, immutable ID/wire/topic | `ReadingProgressAvroCutoverRehearsalDistributedTest`, [Catalog distributed job](https://github.com/Luca5Eckert/VellumHub/actions/runs/43615689013) |
| Independent Ratings/Reactions freeze/drain/rollback | Pre-V10 PostgreSQL JSONB; per-type gate; two independent cutovers; Outbox rows + Kafka encoded samples; SQL assertions 3 facts, one Avro and two JSON per type | `EngagementAvroCutoverRehearsalDistributedTest`, [Engagement distributed job](https://github.com/Luca5Eckert/VellumHub/actions/runs/43615689013) |
| Legacy shutdown only after drain | Default `KAFKA_LEGACY_CONSUMERS_ENABLED=true`, explicit checkpoints/drain/runbook with required GO/NO-GO steps | [Reading runbook](READING_PROGRESS_AVRO_CUTOVER_RUNBOOK.md) and [Engagement runbook](ENGAGEMENT_AVRO_CUTOVER_RUNBOOK.md); no shared-environment switch asserted |

The [extended 5-job canonical workflow](../.github/workflows/canonical-streams-avro-acceptance.yml) explicitly runs:
```
mvn -B -ntp -pl services/catalog-service -am -Dgroups=reading-avro-e2e -Dsurefire.failIfNoSpecifiedTests=false test
mvn -B -ntp -pl services/engagement-service -am -Dgroups=reading-avro-e2e -Dsurefire.failIfNoSpecifiedTests=false test
mvn -B -ntp -pl services/recommendation-service -am -Dgroups=reading-avro-e2e -Dsurefire.failIfNoSpecifiedTests=false test
mvn -B -ntp -pl services/engagement-service -am -Dgroups=engagement-avro-e2e -Dsurefire.failIfNoSpecifiedTests=false test
mvn -B -ntp -pl services/recommendation-service -am -Dgroups=engagement-avro-e2e -Dsurefire.failIfNoSpecifiedTests=false test
```

On [run 43615689013](https://github.com/Luca5Eckert/VellumHub/actions/runs/43615689013) (head `281bc752b8ae21735ee836f80cd58add5a6ed17f`) all five jobs completed successfully. The Catalog and Engagement job logs confirm the **new rehearsal tests each ran once** (0 failed, 0 errors, 0 skipped). Runner test artifacts are retained for 21 days.

## Strict limitations and deployment handoff

This audit establishes code behavior and reproducible lab cutover only. It does **not** claim: production Registry registration, live Kafka group rebalance, drain of real legacy offsets, application replica flag cutover, SQL reconciliation of a real populated environment, production TLS/permissions, or operator signoff. Production rollout and irreversible changes require separate approvals and evidence captured in the runbooks. A single global legacy listener flag affects more than one stream; NEVER set it to false solely because a stream's lab test passed.

The acceptance of #350 as a **repository feature** must not be reused as an assertion of an environment rollout. Any external deployment should be tracked independently with its own GO/NO-GO outcome and evidence.

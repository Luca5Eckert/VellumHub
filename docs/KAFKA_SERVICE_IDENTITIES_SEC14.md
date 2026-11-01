# SEC-14: Kafka identities, ACLs and token-free event transport (#315)

SEC-14 is **transport/broker authorization**, independent of HTTP OAuth/OIDC.
HTTP callers authenticate with JWT; Kafka authenticates **service principals**
against the broker. Domain events retain stable `userId`/`actorId` as business
data where already defined, never a bearer/refresh/ID token or caller JAAS secret.
Do not add bearer tokens in headers, outbox JSON, DLT provenance or structured logs.

## Current inventory and least privilege

The exact, machine-readable ACL source of truth is
[`infra/kafka/acl-policy.json`](../infra/kafka/acl-policy.json);
[`provision-acls.py`](../infra/scripts/kafka/provision-acls.py) applies it
idempotently with **broker administrator credentials**, never with a service
principal. No application is a superuser, no cluster-wide topic wildcard grants.
The broker grants `IdempotentWrite` at cluster level only to producer principals;
publishing still requires topic-specific `Write`.

| Broker identity | Publishes | Subscribes | Consumer groups |
| --- | --- | --- | --- |
| `catalog_service` | `book-events`, `reading-progress-events`, Book/Progress legacy topics | none | none |
| `user_service` | `created-user-preference` | none | none |
| `engagement_service` | `engagement.ratings.v1`, `engagement.reactions.v1`, legacy rating/reaction topics; own retry and DLT | `book-events`, `reading-progress-events`, Book/Progress legacy topics; own retry/DLT | `engagement-service`, `engagement-service-dlt-group`, generated retry groups |
| `recommendation_service` | only retry and DLT transport | Book/Progress/Engagement streams and legacy user-preference/ratings/reactions | `recommendation-service`, `recommendation-group`, `recommendation_service_group`, `recommendation-service-dlt-group`, generated retry groups |

Publishers are responsible only for their emitted topic families. Consumers may
`Read`/`Describe` their sources. Spring RetryTopic owns its own
`<source>-retry*` namespace and `<source>-dlt` topic; the ACL script
restricts their `Create`/`Write` permissions to the matching source family.
Ordered DLTs have an exact `<source>.<service>-dlt` grant, preserving the
quarantine/recovery contract of #272.

**Manual review gate:** if Kafka listeners, topic names, consumer groups or
retry topology change, edit the JSON policy in the same PR; do not broaden the
ACL to all topics or all groups as a workaround. Dedicated
`recommendation-group-test` is test-only, not provisioned for production.
The DLT regex listener sees only topics it can describe/read.

## Deployment

### Local protected Compose integration

The standard `docker-compose.yml` broker is deliberately PLAINTEXT for
offline/dev compatibility. The security verification runs against the explicit
[`docker-compose.kafka-secure.yml`](../docker-compose.kafka-secure.yml)
overlay, which enables SASL/PLAIN and denies all non-authorized topic/group
operations. **SASL_PLAINTEXT has no transport confidentiality and is limited to
the isolated local Docker network; never expose the listener publicly.**
The unauthenticated Kafka UI is not started in the secure overlay.

1. Export five **different** URL-safe 24-128 character values in your shell:
   `KAFKA_ADMIN_PASSWORD`, `KAFKA_CATALOG_PASSWORD`,
   `KAFKA_USER_PASSWORD`, `KAFKA_ENGAGEMENT_PASSWORD`,
   `KAFKA_RECOMMENDATION_PASSWORD`. Do not pass them on CLI arguments,
   commit them to Git, paste them into PR comments, or use real credentials in CI.
2. Run `python3 infra/scripts/kafka/generate-local-secrets.py`. This creates
   `.secrets/kafka/` (untracked, mode 0700) containing broker JAAS, client
   property files (0600), and a scoped superuser administration config.
   **UID requirement for Linux bind mounts:** broker/admin/CLI files must be
   readable by the Confluent container UID 1000, while each Java service's
   `<service>_service.properties` must be owned by the Java image UID 10001
   (all stay mode 0600). The SEC-14 CI sets ownership explicitly. For a
   local Docker environment with the same UIDs, apply the matching `chown`
   through a privileged local setup. Never relax secrets to world-readable.
3. Start isolated infrastructure:
   `docker compose -f docker-compose.yml -f docker-compose.kafka-secure.yml up -d --wait zookeeper kafka`.
4. Run ACL provisioning:
   `docker compose -f docker-compose.yml -f docker-compose.kafka-secure.yml run --rm --no-deps kafka-acl-init`.
   Full-stack Compose uses `depends_on: kafka-acl-init`, so services cannot
   start before ACL provisioning succeeds.
5. Broker-backed negative test:
   `docker compose -f docker-compose.yml -f docker-compose.kafka-secure.yml run --rm --no-deps --entrypoint python3 kafka-acl-init /opt/kafka/verify-acls.py`.
   The test verifies allowed Catalog publish / Engagement consume; denies
   User reading Book events and unauthorized cross-service writes; verifies
   that authorized Engagement DLT and retry publishing/consumption succeed.
   Denials must be real authorization errors, not just empty-topic timeouts.
6. Stop isolated broker with `docker compose -f docker-compose.yml -f docker-compose.kafka-secure.yml down -v`
   **only** in a disposable local environment; `-v` destroys persisted data.

The generated property file is loaded through Spring `SPRING_CONFIG_IMPORT`
and inherited by **all** producers: User custom template, ordinary outbox,
legacy retry template and ordered DLT producer. Never use a bare ad-hoc
`DefaultKafkaProducerFactory` with only `bootstrap.servers` in a secure mode.

### Kubernetes / production

The production overlay sets `KAFKA_SECURITY_PROTOCOL=SASL_SSL` with
`KAFKA_SASL_MECHANISM=PLAIN` and uses four unique `Secret` keys
`KAFKA_{USER,CATALOG,ENGAGEMENT,RECOMMENDATION}_JAAS`. Never place JAAS,
passwords or keystore material in ConfigMaps. Supply the JAAS stanza through
your secret manager / External Secrets controller. `secret.example.yaml`
is an illustrative schema only; its placeholders must not be applied.

The production broker is **external/managed**, not the unsecured single-node
Kafka used by the local K8s overlay. Enable broker-side TLS verification (a
publicly trusted CA or configured truststore), ACL enforcement, broker
administrator, audit logs, and allowlisted network access first. If the broker
uses a private CA, mount its CA truststore separately and configure the Kafka
SSL truststore properties through an environment-specific Secret, not in Git.
No cluster-root `allow.everyone.if.no.acl.found` policy is acceptable.

Use a temporary administrator pod or approved management workstation with
Kafka 3.9 compatible CLIs and the secured **administrator** configuration:

```sh
export KAFKA_ADMIN_CONFIG=/run/secrets/kafka/admin.properties
export KAFKA_BOOTSTRAP_SERVERS=kafka.production.internal:9093
python3 infra/scripts/kafka/provision-acls.py
```

The admin client config must set `security.protocol=SASL_SSL`, the correct
SASL mechanism and broker CA trust configuration. Do not deploy that config
into app pods. Production topic partition/replication/retention settings
should be explicitly reviewed before running the example provisioner
(it creates one-partition, replication-1 topics for the **local** broker).
For production, precreate topics using approved broker infrastructure and
apply only the ACLs; never use this development provisioning script to create
single-replica production topics.

## Rotation and rollout

1. Freeze ACL topology/consumer groups. Prepare new service credentials in
   the broker and the external Secret manager without removing old ones;
   broker SASL/PLAIN may require a controlled broker restart to add a new user.
2. Rotate **one service at a time**; restart its deployment to reload JAAS.
   Validate local publish/consume and broker principal audit logs, plus
   outbox delivery and retry/DLT recovery before switching the next service.
3. Revoke only the old broker credential after all old connections drain.
   Verify failed authentication for the old principal. Distinct credentials
   allow revocation without changing unrelated services.
4. Audit old ACL entries, topic retention and consumer groups. Secure backups
   of any retained quarantine or outbox state; do not delete offsets.

Rollback: restore a previously authorized **service credential** and
configuration, never set `allow.everyone.if.no.acl.found=true` or add a topic
wildcard. Preserve outbox rows, ordered checkpoints and blocked/quarantined
offsets; do not reset offsets to work around ACL errors. A rejected DLT write
must keep the original record pending and trigger operational alerting.

### Independent DLT replay: dedicated operator

The optional `scripts/kafka-recovery.py replay-dlt --execute` now
requires explicit `KAFKA_RECOVERY_SECURITY_PROTOCOL=SASL_SSL`,
`KAFKA_RECOVERY_USERNAME`, and `KAFKA_RECOVERY_PASSWORD` provided by a
separate operational secret; configure `KAFKA_RECOVERY_SSL_CA_LOCATION`
for private-CA brokers. A local-only `SASL_PLAINTEXT` replay requires the
additional `KAFKA_RECOVERY_ALLOW_LOCAL_INSECURE=true` opt-in.
The CLI **refuses** to use a domain-service identity, broker admin identity,
or an unauthenticated fallback.

The operator principal must be provisioned independently with read/describe
on specifically approved independent DLT topics, write/describe on the
corresponding allowed source topics, read on group
`vellum-recovery-inspect`, and Kafka `IdempotentWrite` if the broker
requires it. These are **not yet automatically provisioned** by the
four-service policy and require security review before replay is enabled.
Do not grant topic or group wildcards. Ordered sources still use
quarantine-based `resume` and must never be blindly republished.
Broker-backed operator replay is a separate acceptance gate.

## Evidence, limits and non-goals

- Static policy tests: `python3 -m unittest discover -s infra/scripts/kafka -p 'test_*.py' -v`.
- CI workflow: `.github/workflows/kafka-service-acls.yml` runs a **real**
  ephemeral SASL/PLAIN broker and tests allowed/forbidden topic/group ACLs.
- The local Kind workflow creates **empty** Kafka JAAS Secret keys because
  its in-cluster broker uses PLAINTEXT. Production injects nonempty, distinct
  JAAS values through an approved secret manager; empty production values are invalid.
- Existing Maven distributed/outbox/ordering tests remain the regression gates
  for at-least-once delivery, checkpoint semantics and sanitized DLT headers.
- SEC-14 does **not** change event contracts, introduce OAuth token exchange,
  delegate end-user authorization to the broker, or prove a live production
  Kafka cluster has correct external ACLs and TLS without deployment evidence.
- No SQL migration. Applying ACLs is a broker configuration rollout, separate
  from the Auth issuer cutover. Rollback is reversible but must not discard
  durable Kafka/DB recovery state.

See [Kafka recovery](KAFKA_RECOVERY_RUNBOOK.md) and
[Kafka monitoring](KAFKA_MONITORING.md).

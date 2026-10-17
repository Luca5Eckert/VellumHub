#!/usr/bin/env python3
"""Explicit, auditable recovery of VellumHub Kafka consumer quarantines (#272).

Dependencies: pip install -r scripts/kafka-recovery-requirements.txt
Run with --execute only after fixing the underlying consumer failure.
Never prints event payloads or credential-bearing exception headers.
"""
import argparse
import json
import os
import re
import sys
from datetime import datetime, timezone
from uuid import UUID

ORDERED = {"book-events", "reading-progress-events",
           "engagement.ratings.v1", "engagement.reactions.v1"}
IDENTITY = re.compile(r"^[a-zA-Z0-9_.-]{1,160}$")
TYPE_ALIAS = re.compile(r"^[a-z][a-z0-9_]{1,80}$")


def text(value, name):
    if not IDENTITY.fullmatch(value or ""):
        raise ValueError(f"Invalid {name}")
    return value


def connect():
    import psycopg
    dsn = os.environ.get("VELLUM_RECOVERY_DSN")
    if not dsn:
        raise RuntimeError("Set VELLUM_RECOVERY_DSN for the target consumer database")
    return psycopg.connect(dsn)


def origin(args):
    return (text(args.group, "consumer group"), text(args.topic, "source topic"),
            args.partition, args.offset)


def inspect(args):
    with connect() as conn:
        row = conn.execute("""
            SELECT consumer_group,source_topic,source_partition,source_offset,
                   event_id,failure_category,attempts,status,dlt_topic,blocked_at,updated_at
              FROM kafka_consumer_quarantine
             WHERE consumer_group=%s AND source_topic=%s AND source_partition=%s
               AND source_offset=%s
        """, origin(args)).fetchone()
    if not row:
        raise RuntimeError("Quarantine record not found")
    fields = ("group", "topic", "partition", "offset", "event_id", "category",
              "attempts", "status", "dlt_topic", "blocked_at", "updated_at")
    print(json.dumps(dict(zip(fields, (str(v) if v is not None else None for v in row))),
                     ensure_ascii=False))


def resume(args):
    if not args.reason or len(args.reason) > 500:
        raise ValueError("Reason required (maximum 500 characters)")
    operator = text(args.operator, "operator")
    coordinates = origin(args)
    if not args.execute:
        print("DRY RUN: request replay of ORIGINAL source offset", coordinates,
              "for operator", operator)
        return
    with connect() as conn:
        with conn.transaction():
            status = conn.execute("""
                SELECT status FROM kafka_consumer_quarantine
                WHERE consumer_group=%s AND source_topic=%s AND source_partition=%s
                  AND source_offset=%s FOR UPDATE
            """, coordinates).fetchone()
            if status is None or status[0] != "BLOCKED":
                raise RuntimeError("Expected durable BLOCKED quarantine before resume")
            conn.execute("""
                UPDATE kafka_consumer_quarantine
                   SET status='RETRY_REQUESTED',updated_at=NOW()
                 WHERE consumer_group=%s AND source_topic=%s
                   AND source_partition=%s AND source_offset=%s
            """, coordinates)
            conn.execute("""
                INSERT INTO kafka_recovery_audit
                    (operation,consumer_group,source_topic,source_partition,source_offset,
                     operator_id,reason,outcome)
                VALUES('RESUME_REQUESTED',%s,%s,%s,%s,%s,%s,'RECORDED')
            """, (*coordinates, operator, args.reason))
    print("Replay requested. Restart the target consumer service so the assignment")
    print("seeks the ORIGINAL offset. Verify RESOLVED and checkpoint progression.")


def header(headers, name):
    return next((v for k, v in (headers or []) if k == name), None)


def replay_dlt(args):
    """Limited to identity-bearing, non-ordered records. No blind legacy replay."""
    if not args.reason or len(args.reason) > 500:
        raise ValueError("Reason required (maximum 500 characters)")
    operator = text(args.operator, "operator")
    topic = text(args.dlt_topic, "DLT topic")
    if not topic.endswith("-dlt"):
        raise ValueError("Only DLT records can be replayed")
    if not args.execute:
        print("DRY RUN: inspect DLT record", topic, args.partition, args.offset)
        return

    from confluent_kafka import Consumer, Producer, TopicPartition
    bootstrap = os.environ.get("KAFKA_BOOTSTRAP_SERVERS")
    if not bootstrap:
        raise RuntimeError("Set KAFKA_BOOTSTRAP_SERVERS")
    consumer = Consumer({"bootstrap.servers": bootstrap,
                         "group.id": "vellum-recovery-inspect",
                         "enable.auto.commit": False,
                         "auto.offset.reset": "none"})
    try:
        consumer.assign([TopicPartition(topic, args.partition, args.offset)])
        msg = consumer.poll(10)
        if msg is None or msg.error() or msg.offset() != args.offset:
            raise RuntimeError("DLT message not found at exact coordinates")
        headers = msg.headers() or []
        raw_topic = header(headers, "x-vellum-source-topic")
        if raw_topic is None:
            # Legacy DLT lacks a uniform idempotency/identity contract.
            raise RuntimeError("Legacy DLT is not approved for automatic replay")
        source = raw_topic.decode("utf-8")
        if source in ORDERED:
            raise RuntimeError("Ordered events must be replayed by RESUME of original offset")
        text(source, "original topic")
        event_id = json.loads(msg.value() or b"{}").get("eventId")
        if not event_id:
            raise RuntimeError("Replay requires a stable eventId")
        UUID(str(event_id))
        type_id = header(headers, "__TypeId__")
        if type_id is None or not TYPE_ALIAS.fullmatch(type_id.decode("ascii")):
            raise RuntimeError("Replay requires an allowlisted type alias")
        forwarded = [("__TypeId__", type_id)]
        correlation = header(headers, "x-correlation-id")
        if correlation and len(correlation) <= 128:
            forwarded.append(("x-correlation-id", correlation))

        group_header = header(headers, "x-vellum-consumer-group")
        group = text(group_header.decode("utf-8") if group_header else "", "consumer group")
        part = int(header(headers, "x-vellum-source-partition"))
        offset = int(header(headers, "x-vellum-source-offset"))
        with connect() as conn:
            with conn.transaction():
                conn.execute("""
                    INSERT INTO kafka_recovery_audit
                        (operation,consumer_group,source_topic,source_partition,source_offset,
                         operator_id,reason,outcome)
                    VALUES('DLT_REPLAY',%s,%s,%s,%s,%s,%s,'ATTEMPTED')
                """, (group, source, part, offset, operator, args.reason))
        producer = Producer({"bootstrap.servers": bootstrap, "acks": "all",
                             "enable.idempotence": True})
        result = []
        producer.produce(source, key=msg.key(), value=msg.value(), headers=forwarded,
                         on_delivery=lambda error, message: result.append(error))
        producer.flush(15)
        ok = result == [None]
        with connect() as conn:
            conn.execute("""
                INSERT INTO kafka_recovery_audit
                    (operation,consumer_group,source_topic,source_partition,source_offset,
                     operator_id,reason,outcome)
                VALUES('DLT_REPLAY',%s,%s,%s,%s,%s,%s,%s)
            """, (group, source, part, offset, operator, args.reason,
                  "PUBLISHED" if ok else "FAILED"))
        if not ok:
            raise RuntimeError("Replay publication was not acknowledged")
        print("Published immutable eventId", event_id, "to", source,
              "; verify downstream checkpoint before closing the incident.")
    finally:
        consumer.close()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    subs = parser.add_subparsers(dest="action", required=True)
    for name in ("inspect", "resume"):
        sub = subs.add_parser(name)
        sub.add_argument("--group", required=True)
        sub.add_argument("--topic", required=True)
        sub.add_argument("--partition", type=int, required=True)
        sub.add_argument("--offset", type=int, required=True)
        if name == "resume":
            sub.add_argument("--operator", required=True)
            sub.add_argument("--reason", required=True)
            sub.add_argument("--execute", action="store_true")
    dlt = subs.add_parser("replay-dlt")
    dlt.add_argument("--dlt-topic", required=True)
    dlt.add_argument("--partition", type=int, required=True)
    dlt.add_argument("--offset", type=int, required=True)
    dlt.add_argument("--operator", required=True)
    dlt.add_argument("--reason", required=True)
    dlt.add_argument("--execute", action="store_true")
    args = parser.parse_args()
    if args.partition < 0 or args.offset < 0:
        parser.error("partition and offset must be nonnegative")
    try:
        {"inspect": inspect, "resume": resume, "replay-dlt": replay_dlt}[args.action](args)
    except (ValueError, RuntimeError) as error:
        print(f"Recovery refused: {error}", file=sys.stderr)
        sys.exit(2)


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Exercise audited DLT replay against disposable Kafka and PostgreSQL containers."""

from contextlib import ExitStack
import json
import os
from pathlib import Path
import secrets
import socket
import subprocess
import sys
import time
from uuid import uuid4

from confluent_kafka import Consumer, KafkaError, KafkaException, Producer, TopicPartition
from confluent_kafka.admin import (
    AclBinding, AclOperation, AclPermissionType, AdminClient, NewTopic, ResourcePatternType,
    ResourceType,
)
import psycopg

ROOT = Path(__file__).resolve().parents[3]
KAFKA = "confluentinc/cp-kafka:7.5.0"
SOURCE, DLT, UNRELATED = "replay-fixture", "replay-fixture-dlt", "unrelated-fixture"
ADMIN, OPERATOR, SERVICE = "kafka_admin", "recovery_operator", "user_service"
GROUP = "vellum-recovery-inspect"


def docker(*args, environment=None, quiet=False):
    return subprocess.run(["docker", *args], env=environment, check=not quiet,
                          stdout=subprocess.DEVNULL if quiet else subprocess.PIPE,
                          stderr=subprocess.DEVNULL if quiet else None, text=True).stdout


def ready(must_succeed, errors, name):
    deadline = time.monotonic() + 75
    while True:
        try:
            return must_succeed()
        except errors as exc:
            if time.monotonic() >= deadline:
                raise RuntimeError(f"{name} did not become ready") from exc
            time.sleep(1)


def free_port():
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", 0))
        return listener.getsockname()[1]


def client_config(bootstrap, principal, passwords):
    return {"bootstrap.servers": bootstrap, "security.protocol": "SASL_PLAINTEXT",
            "sasl.mechanisms": "PLAIN", "sasl.username": principal,
            "sasl.password": passwords[principal], "log_level": 3}


def produce(config, topic, key, value, headers):
    producer = Producer({**config, "message.timeout.ms": 8000})
    delivered = []
    producer.produce(topic, key=key, value=value, headers=headers,
                     on_delivery=lambda error, message: delivered.append((error, message)))
    assert producer.flush(10) == 0 and len(delivered) == 1, "Kafka delivery timed out"
    return delivered[0]


def read(config, topic, offset, group):
    consumer = Consumer({**config, "group.id": group, "enable.auto.commit": False})
    try:
        consumer.assign([TopicPartition(topic, 0, offset)])
        message = consumer.poll(10)
        assert message is not None, "Kafka read timed out"
        return message
    finally:
        consumer.close()


def exercise(bootstrap, dsn, passwords):
    admin_config = client_config(bootstrap, ADMIN, passwords)
    operator_config = client_config(bootstrap, OPERATOR, passwords)
    service_config = client_config(bootstrap, SERVICE, passwords)
    admin = AdminClient(admin_config)
    ready(lambda: admin.list_topics(timeout=3), (KafkaException,), "SASL broker")
    for result in admin.create_topics([NewTopic(topic, 1, 1) for topic in (SOURCE, DLT, UNRELATED)]).values():
        result.result(timeout=15)

    grants = [
        (DLT, ResourceType.TOPIC, OPERATOR, AclOperation.READ),
        (SOURCE, ResourceType.TOPIC, OPERATOR, AclOperation.WRITE),
        (GROUP, ResourceType.GROUP, OPERATOR, AclOperation.READ),
        # librdkafka names Kafka ACL resource 4 BROKER.
        ("kafka-cluster", ResourceType.BROKER, OPERATOR, AclOperation.IDEMPOTENT_WRITE),
    ]
    bindings = [AclBinding(resource_type, name, ResourcePatternType.LITERAL, f"User:{principal}",
                           "*", operation, AclPermissionType.ALLOW)
                for name, resource_type, principal, operation in grants]
    for result in admin.create_acls(bindings).values():
        result.result(timeout=15)

    with ready(lambda: psycopg.connect(dsn), (psycopg.OperationalError,), "audit database") as conn:
        conn.execute((ROOT / "services/recommendation-service/src/main/resources/db/migration/"
                      "V5__kafka_consumer_quarantine.sql").read_text())

    event_id = str(uuid4())
    key = b"fixture-aggregate"
    value = json.dumps({"eventId": event_id, "fixture": True}).encode()
    headers = [("__TypeId__", b"replay_fixture"),
               ("x-vellum-source-topic", SOURCE.encode()),
               ("x-vellum-consumer-group", b"fixture-consumer"),
               ("x-vellum-source-partition", b"0"), ("x-vellum-source-offset", b"7"),
               ("x-correlation-id", b"fixture-correlation"), ("unrelated-header", b"private")]
    error, message = produce(admin_config, DLT, key, value, headers)
    assert error is None, "Could not seed the replay fixture"
    service_error, _ = produce(service_config, SOURCE, key, value, headers)
    assert service_error is not None and service_error.code() == KafkaError.TOPIC_AUTHORIZATION_FAILED, service_error
    denied, _ = produce(operator_config, UNRELATED, key, value, headers)
    assert denied is not None and denied.code() == KafkaError.TOPIC_AUTHORIZATION_FAILED

    subprocess.run([sys.executable, str(ROOT / "scripts/kafka-recovery.py"), "replay-dlt",
                    "--dlt-topic", DLT, "--partition", "0", "--offset", str(message.offset()),
                    "--operator", OPERATOR, "--reason", "disposable test fixture", "--execute"],
                   check=True, env={**os.environ, "KAFKA_BOOTSTRAP_SERVERS": bootstrap,
                                    "KAFKA_RECOVERY_SECURITY_PROTOCOL": "SASL_PLAINTEXT",
                                    "KAFKA_RECOVERY_ALLOW_LOCAL_INSECURE": "true",
                                    "KAFKA_RECOVERY_USERNAME": OPERATOR,
                                    "KAFKA_RECOVERY_PASSWORD": passwords[OPERATOR],
                                    "VELLUM_RECOVERY_DSN": dsn})
    replayed = read(admin_config, SOURCE, 0, "fixture-check")
    assert replayed.error() is None
    assert replayed.key() == key and replayed.value() == value
    assert replayed.headers() == [("__TypeId__", b"replay_fixture"),
                                  ("x-correlation-id", b"fixture-correlation")]
    with psycopg.connect(dsn) as conn:
        rows = conn.execute("""
            SELECT operation, consumer_group, source_topic, source_partition, source_offset,
                   operator_id, outcome
              FROM kafka_recovery_audit
             ORDER BY id
            """).fetchall()
    assert rows == [("DLT_REPLAY", "fixture-consumer", SOURCE, 0, 7, OPERATOR, outcome)
                    for outcome in ("ATTEMPTED", "PUBLISHED")]
    print("Operator replay, ACL denials, key, payload, headers and PostgreSQL audit verified.")


def main():
    names = "vellum-replay-" + uuid4().hex[:10]
    network, broker, zoo, database = (names + "-" + suffix for suffix in ("net", "broker", "zk", "db"))
    passwords = {name: secrets.token_urlsafe(32) for name in (ADMIN, OPERATOR, SERVICE)}
    db_password = secrets.token_urlsafe(32)
    port = free_port()
    credentials = " ".join(f'user_{principal}="{password}"' for principal, password in passwords.items())
    jaas = ('org.apache.kafka.common.security.plain.PlainLoginModule required '
            f'username="{ADMIN}" password="{passwords[ADMIN]}" {credentials};')
    with ExitStack() as cleanup:
        docker("network", "create", network)
        cleanup.callback(docker, "network", "rm", network, quiet=True)
        for container in (zoo, broker, database):
            cleanup.callback(docker, "rm", "-fv", container, quiet=True)
        docker("run", "-d", "--name", zoo, "--network", network,
               "-e", "ZOOKEEPER_CLIENT_PORT=2181", "confluentinc/cp-zookeeper:7.5.0")
        docker("run", "-d", "--name", database, "--network", network, "-p", "127.0.0.1::5432",
               "-e", "POSTGRES_PASSWORD", "postgres:16-alpine",
               environment={**os.environ, "POSTGRES_PASSWORD": db_password})
        db_address = docker("port", database, "5432/tcp").strip()
        settings = {
            "KAFKA_BROKER_ID": "1",
            "KAFKA_ZOOKEEPER_CONNECT": f"{zoo}:2181",
            "KAFKA_LISTENERS": "INTERNAL://0.0.0.0:29092,EXTERNAL://0.0.0.0:39092",
            "KAFKA_ADVERTISED_LISTENERS": f"INTERNAL://{broker}:29092,EXTERNAL://127.0.0.1:{port}",
            "KAFKA_LISTENER_SECURITY_PROTOCOL_MAP": "INTERNAL:SASL_PLAINTEXT,EXTERNAL:SASL_PLAINTEXT",
            "KAFKA_INTER_BROKER_LISTENER_NAME": "INTERNAL",
            "KAFKA_SASL_ENABLED_MECHANISMS": "PLAIN",
            "KAFKA_SASL_MECHANISM_INTER_BROKER_PROTOCOL": "PLAIN",
            "KAFKA_LISTENER_NAME_INTERNAL_PLAIN_SASL_JAAS_CONFIG": jaas,
            "KAFKA_LISTENER_NAME_EXTERNAL_PLAIN_SASL_JAAS_CONFIG": jaas,
            "KAFKA_AUTHORIZER_CLASS_NAME": "kafka.security.authorizer.AclAuthorizer",
            "KAFKA_ALLOW_EVERYONE_IF_NO_ACL_FOUND": "false",
            "KAFKA_SUPER_USERS": f"User:{ADMIN}",
            "KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR": "1",
            "KAFKA_HEAP_OPTS": "-Xms256M -Xmx512M",
        }
        variables = [argument for name in settings for argument in ("-e", name)]
        docker("run", "-d", "--name", broker, "--network", network, "-p", f"127.0.0.1:{port}:39092",
               *variables, KAFKA, environment={**os.environ, **settings})
        exercise(f"127.0.0.1:{port}", f"postgresql://postgres:{db_password}@{db_address}/postgres", passwords)


if __name__ == "__main__":
    main()

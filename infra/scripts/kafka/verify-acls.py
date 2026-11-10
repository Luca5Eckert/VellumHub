#!/usr/bin/env python3
"""Broker-backed SEC-14 smoke: allowed principal succeeds; cross-service access fails.

Uses a disposable local broker, never a real-user token or business payload.
Run after provision-acls.py with docker-compose.kafka-secure.yml.
"""
import os
from pathlib import Path
import subprocess
import sys

BOOTSTRAP = os.environ.get("KAFKA_BOOTSTRAP_SERVERS", "kafka:29092")
SECRETS = Path("/run/secrets/kafka")
MARKER = "sec14-acl-smoke-no-jwt"


def run(command, payload=None):
    try:
        return subprocess.run(command, input=payload, text=True,
                              stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                              timeout=40, check=False)
    except subprocess.TimeoutExpired:
        raise AssertionError("Kafka ACL verification command exceeded its timeout")


def client(name):
    path = SECRETS / (name + ".client.properties")
    if not path.is_file():
        raise AssertionError("Service principal config not provisioned")
    return str(path)


def publish(name, topic, permitted):
    cmd = ["kafka-console-producer", "--bootstrap-server", BOOTSTRAP,
           "--producer.config", client(name), "--topic", topic, "--sync",
           "--producer-property", "max.block.ms=7000",
           "--producer-property", "request.timeout.ms=4000",
           "--producer-property", "delivery.timeout.ms=9000"]
    result = run(cmd, MARKER + "\n")
    if permitted:
        if result.returncode != 0:
            raise AssertionError("Authorized publish failed: principal=" + name +
                                 " topic=" + topic)
    else:
        # A nonzero exit can also mean DNS/timeout/broker downtime. Require
        # explicit authorization denial; infrastructure failures are NOT proof.
        failure = (result.stderr + result.stdout).lower()
        if result.returncode == 0 or not any(term in failure for term in
                ("topicauthorizationexception", "clusterauthorizationexception",
                 "authorizationexception", "authorization failed",
                 "not authorized", "topic authorization")):
            raise AssertionError("Negative publish test did not prove ACL denial: principal="
                                 + name + " topic=" + topic)


def consume(name, topic, group, permitted):
    cmd = ["kafka-console-consumer", "--bootstrap-server", BOOTSTRAP,
           "--consumer.config", client(name), "--topic", topic,
           "--group", group, "--from-beginning", "--max-messages", "1",
           "--timeout-ms", "12000"]
    result = run(cmd)
    if permitted:
        if result.returncode != 0 or MARKER not in result.stdout:
            raise AssertionError("Authorized consumer failed: principal=" + name +
                                 " topic=" + topic)
    else:
        # A timeout alone is NOT evidence of denial. Require broker authorization
        # rejection, never consider an empty topic a negative ACL proof.
        error = (result.stderr + result.stdout).lower()
        if not any(term in error for term in
                   ("authorizationexception", "authorization failed",
                    "not authorized", "topic authorization", "group authorization")):
            raise AssertionError("Negative consumer test did not prove denial")


def main():
    publish("catalog_service", "book-events", True)
    publish("user_service", "created-user-preference", True)
    publish("catalog_service", "book-events.avro", True)
    consume("recommendation_service", "book-events.avro", "recommendation-service-book-avro", True)
    consume("engagement_service", "book-events.avro", "engagement-service-book-avro", True)
    publish("user_service", "book-events.avro", False)
    publish("recommendation_service", "book-events.avro", False)
    consume("recommendation_service", "book-events.avro", "engagement-service-book-avro", False)
    consume("recommendation_service", "created-user-preference", "recommendation-service", True)
    publish("engagement_service", "engagement.ratings.v1", True)
    consume("recommendation_service", "engagement.ratings.v1", "recommendation-service", True)
    consume("engagement_service", "book-events", "engagement-service", True)
    publish("user_service", "book-events", False)
    publish("catalog_service", "engagement.ratings.v1", False)
    consume("user_service", "book-events", "engagement-service", False)
    consume("recommendation_service", "book-events", "engagement-service", False)
    publish("recommendation_service", "created-book", False)

    # DLT publication and reading must remain permitted to the correct service.
    dlt = "book-events.engagement-service-dlt"
    publish("engagement_service", dlt, True)
    consume("engagement_service", dlt, "engagement-service-dlt-group", True)
    publish("catalog_service", dlt, False)

    # Retry namespaces are private to the specific consumer service.
    retry = "created-book-retry-3000"
    publish("engagement_service", retry, True)
    consume("engagement_service", retry, "engagement-service-retry-0", True)
    publish("user_service", retry, False)
    print("SEC-14: authorized publish/subscribe, denied cross-service access, "
          "and DLT/retry ACLs verified without JWTs")


if __name__ == "__main__":
    try:
        main()
    except AssertionError as exc:
        print(str(exc), file=sys.stderr)
        sys.exit(1)

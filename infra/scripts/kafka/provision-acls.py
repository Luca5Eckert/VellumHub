#!/usr/bin/env python3
"""SEC-14: provision exact Kafka topics and service-scoped ACLs.

Run only with broker administrator credentials. No application principal gets
Cluster ALTER, wildcard topic grants, or a broker super-user identity.
"""
import json
import os
from pathlib import Path
import subprocess
import sys

POLICY = Path(os.environ.get("KAFKA_ACL_POLICY", "infra/kafka/acl-policy.json"))
BOOTSTRAP = os.environ.get("KAFKA_BOOTSTRAP_SERVERS", "kafka:29092")
CONFIG = os.environ.get("KAFKA_ADMIN_CONFIG", "/run/secrets/kafka/admin.properties")


def execute(binary, *args):
    subprocess.run([binary, "--bootstrap-server", BOOTSTRAP,
                    "--command-config", CONFIG, *args], check=True)


def grant(user, resource, name, actions, pattern="literal"):
    if name == "*" or not name or "*" in name:
        raise ValueError("Wildcard resources are prohibited")
    args = ["--add", "--allow-principal", "User:" + user]
    for action in actions:
        args.extend(["--operation", action])
    args.extend(["--resource-pattern-type", pattern, "--" + resource, name])
    execute("kafka-acls", *args)


def main():
    if not Path(CONFIG).is_file():
        raise RuntimeError("Kafka administrator credentials file is required")
    policy = json.loads(POLICY.read_text(encoding="utf-8"))
    if set(policy) != {"catalog_service", "user_service",
                       "engagement_service", "recommendation_service"}:
        raise ValueError("SEC-14 principal inventory changed; review ACLs first")

    # Precreate known domain streams and service DLTs before startup.
    topics = set()
    for user, rights in policy.items():
        topics.update(rights["publish"])
        topics.update(rights["subscribe"])
        topics.update(t + "-dlt" for t in rights["legacy_retry"])
        topics.update(t + "." + user.replace("_", "-") + "-dlt"
                      for t in rights["ordered_dlt"])
    if os.environ.get("KAFKA_PROVISION_TOPICS", "false").lower() == "true":
        if os.environ.get("KAFKA_DEPLOYMENT_TIER", "local") != "local":
            raise RuntimeError("Single-replica topic creation is only valid locally")
        for topic in sorted(topics):
            execute("kafka-topics", "--create", "--if-not-exists",
                    "--topic", topic, "--partitions", "1", "--replication-factor", "1")

    for user, rights in policy.items():
        for topic in rights["publish"]:
            grant(user, "topic", topic, ["Write", "Describe"])
        for topic in rights["subscribe"]:
            grant(user, "topic", topic, ["Read", "Describe"])
        for topic in rights["legacy_retry"]:
            # Spring's generated -retry-N topics, including retry DLT, stay in
            # the family namespace. Only that specific family can be created.
            grant(user, "topic", topic + "-retry", ["Read", "Write", "Describe", "Create"], "prefixed")
            grant(user, "topic", topic + "-dlt", ["Read", "Write", "Describe", "Create"])
        for topic in rights["ordered_dlt"]:
            grant(user, "topic", topic + "." + user.replace("_", "-") + "-dlt",
                  ["Read", "Write", "Describe", "Create"])
        for group in rights["groups"]:
            grant(user, "group", group, ["Read"])
            # Spring RetryTopic generates groups based on the listener group.
            grant(user, "group", group + "-retry", ["Read"], "prefixed")
        if rights["publish"] or rights["legacy_retry"] or rights["ordered_dlt"]:
            # Kafka idempotent producers require a cluster capability, but
            # this does NOT grant permission to write to other topics.
            execute("kafka-acls", "--add", "--allow-principal", "User:" + user,
                    "--operation", "IdempotentWrite", "--cluster")
    print("SEC-14 topic and ACL provisioning completed; no credentials logged")


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, subprocess.CalledProcessError) as error:
        # Avoid printing Kafka's command line or JAAS content in exceptions.
        print("Kafka ACL provisioning failed; inspect broker administration logs", file=sys.stderr)
        sys.exit(1)

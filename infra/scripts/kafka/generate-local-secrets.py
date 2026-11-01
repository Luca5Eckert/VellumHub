#!/usr/bin/env python3
"""Generate untracked, mode-0600 SASL/PLAIN credentials for local Kafka only.

Required environment: KAFKA_ADMIN_PASSWORD, KAFKA_CATALOG_PASSWORD,
KAFKA_USER_PASSWORD, KAFKA_ENGAGEMENT_PASSWORD, KAFKA_RECOMMENDATION_PASSWORD.
Never pass passwords as CLI arguments (shell history/process listing).
"""
import os
from pathlib import Path
import re
import sys

NAMES = {
    "kafka_admin": "KAFKA_ADMIN_PASSWORD",
    "catalog_service": "KAFKA_CATALOG_PASSWORD",
    "user_service": "KAFKA_USER_PASSWORD",
    "engagement_service": "KAFKA_ENGAGEMENT_PASSWORD",
    "recommendation_service": "KAFKA_RECOMMENDATION_PASSWORD",
}
PASSWORD = re.compile(r"[A-Za-z0-9_-]{24,128}\Z")


def write_private(directory, name, content):
    file = directory / name
    if file.is_symlink():
        raise RuntimeError("Refusing symbolic link for secret file")
    fd = os.open(file, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    try:
        os.fchmod(fd, 0o600)
        with os.fdopen(fd, "w", encoding="utf-8") as out:
            out.write(content)
    except BaseException:
        try:
            os.close(fd)
        except OSError:
            pass
        raise


def jaas(user, password):
    return ('org.apache.kafka.common.security.plain.PlainLoginModule required '
            + 'username="' + user + '" password="' + password + '";')


def main():
    output = Path(".secrets/kafka")
    if output.is_symlink():
        raise RuntimeError("Refusing symbolic-link secret directory")
    values = {}
    for identity, variable in NAMES.items():
        value = os.environ.get(variable, "")
        if not PASSWORD.fullmatch(value):
            raise ValueError(variable + " must be 24-128 URL-safe characters")
        values[identity] = value
    if len(set(values.values())) != len(values):
        raise ValueError("Service/broker passwords must be unique")
    output.mkdir(mode=0o700, parents=True, exist_ok=True)
    os.chmod(output, 0o700)
    # KafkaServer requires ONE JAAS stanza; all options precede its semicolon.
    broker = "KafkaServer {\n  org.apache.kafka.common.security.plain.PlainLoginModule required\n"
    broker += '  username="kafka_admin"\n  password="' + values["kafka_admin"] + '"\n'
    for name, password in values.items():
        broker += '  user_' + name + '="' + password + '"\n'
    broker += ";\n};\n"
    write_private(output, "broker.jaas.conf", broker)
    for name, password in values.items():
        if name == "kafka_admin":
            target = "admin.properties"
            config = ("security.protocol=SASL_PLAINTEXT\nsasl.mechanism=PLAIN\n"
                      "sasl.jaas.config=" + jaas(name, password) + "\n")
        else:
            target = name + ".properties"
            config = ("KAFKA_SECURITY_PROTOCOL=SASL_PLAINTEXT\n"
                      "KAFKA_SASL_MECHANISM=PLAIN\n"
                      "KAFKA_SASL_JAAS_CONFIG=" + jaas(name, password) + "\n")
        write_private(output, target, config)
        if name != "kafka_admin":
            kafka_client = ("security.protocol=SASL_PLAINTEXT\n"
                            "sasl.mechanism=PLAIN\n"
                            "sasl.jaas.config=" + jaas(name, password) + "\n")
            write_private(output, name + ".client.properties", kafka_client)
    print("Untracked SEC-14 secrets written with mode 0600; credentials not displayed")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, RuntimeError) as exc:
        print(str(exc), file=sys.stderr)
        sys.exit(1)

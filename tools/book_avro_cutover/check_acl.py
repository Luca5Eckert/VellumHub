#!/usr/bin/env python3
"""Reject missing or wildcard privileges for new ordered Book Avro stream."""
import json
from pathlib import Path
policy = json.loads(Path("infra/kafka/acl-policy.json").read_text())
topic="book-events.avro"
assert topic in policy["catalog_service"]["publish"]
for service, group in (("engagement_service","engagement-service-book-avro"),
                       ("recommendation_service","recommendation-service-book-avro")):
    assert topic in policy[service]["subscribe"]
    assert topic in policy[service]["ordered_dlt"]
    assert group in policy[service]["groups"]
    assert topic not in policy[service]["publish"]
for identity, permissions in policy.items():
    for key, values in permissions.items():
        assert all("*" not in value for value in values), (identity, key)
print("Book Avro ACL allowlist: PASS")

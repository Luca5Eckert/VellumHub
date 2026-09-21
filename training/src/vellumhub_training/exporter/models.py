"""Version-one facts, with no training weights or inferred current state."""
from datetime import datetime, timezone
from uuid import UUID


def json_value(value):
    if isinstance(value, UUID):
        return str(value)
    if isinstance(value, datetime):
        if value.tzinfo is None:
            return value.isoformat()
        return value.astimezone(timezone.utc).isoformat(timespec="microseconds").replace("+00:00", "Z")
    if isinstance(value, dict):
        return {key: json_value(item) for key, item in value.items()}
    if isinstance(value, (list, tuple)):
        return [json_value(item) for item in value]
    return value


def signal(row, kind, service, role, payload, occurred_at=None):
    return json_value(dict(schemaVersion=1, userId=row["user_id"],
                           bookId=row["book_id"], signalType=kind,
                           sourceService=service, sourceRole=role,
                           sourceRecordId=str(row["id"]),
                           occurredAt=occurred_at, payload=payload))


def signal_key(row):
    return (row["userId"], row["bookId"], row["signalType"],
            row["occurredAt"] is None, row["occurredAt"] or "", row["sourceRecordId"])

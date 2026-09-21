from .models import json_value


def manifest(export_id, exported_at, catalog_at, engagement_at, artifacts):
    return json_value(dict(
        schemaVersion=1, exportId=str(export_id), exportedAt=exported_at,
        consistency="PER_SOURCE_REPEATABLE_READ_NOT_GLOBAL_ATOMIC",
        sources={
            "catalog": {"role": "CANONICAL_CURRENT", "snapshotAt": catalog_at},
            "engagement": {"roles": ["CANONICAL_INTERACTION", "REPLICATED_HISTORY"],
                           "snapshotAt": engagement_at}},
        artifacts=artifacts))

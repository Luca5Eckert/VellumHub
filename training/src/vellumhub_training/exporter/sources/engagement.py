from ..models import signal

RATINGS_SQL = """
SELECT id, user_id, book_id, stars, (review IS NOT NULL AND btrim(review) <> '') AS has_review,
       timestamp FROM rating ORDER BY user_id, book_id, id
"""
REACTIONS_SQL = """
SELECT id, user_id, book_snapshot_id AS book_id, type_reaction, created_at, updated_at
FROM reactions ORDER BY user_id, book_snapshot_id, id
"""
HISTORY_SQL = """
SELECT id, user_id, book_snapshot_book_id AS book_id, reading_session_id,
       type, page_read, event_id, timestamp FROM reading_session_entries
ORDER BY user_id, book_snapshot_book_id, timestamp NULLS LAST, id
"""


def rating(row):
    # The database stores a local timestamp without a timezone. Do not invent UTC.
    return signal(row, "RATING", "engagement-service", "CANONICAL_INTERACTION",
                  dict(stars=row["stars"], hasReview=row["has_review"],
                       recordedLocalAt=row["timestamp"], timeQuality="TIMEZONE_UNKNOWN"))


def reaction(row):
    # Legacy reaction dates were backfilled by V3; these are audit, not event timestamps.
    return signal(row, "REACTION", "engagement-service", "CANONICAL_INTERACTION",
                  dict(type=row["type_reaction"], createdAt=row["created_at"],
                       updatedAt=row["updated_at"], timeQuality="AUDIT_MAY_BE_BACKFILLED"))


def history(row):
    complete = row["event_id"] is not None and row["timestamp"] is not None
    return signal(row, "READING_PROGRESS_HISTORY", "engagement-service", "REPLICATED_HISTORY",
                  dict(status=row["type"], page=row["page_read"], eventId=row["event_id"],
                       bookProgressId=row["reading_session_id"],
                       historyQuality="EVENT_METADATA" if complete else "LEGACY_UNKNOWN"),
                  row["timestamp"])


def read(connection):
    return [mapper(row) for query, mapper in ((RATINGS_SQL, rating), (REACTIONS_SQL, reaction),
                                              (HISTORY_SQL, history))
            for row in connection.execute(query)]

# Offline training data

This job exports versioned facts for #193. It does not train a model, assign weights,
reconstruct current state from Kafka, or change the online recommendation path.
The synthetic benchmark under `services/recommendation-service/evaluation/` remains separate.

| Source | Data | Role |
|---|---|---|
| Catalog `books`, `book_genre_id`, `genres` | Non-deleted corpus | Canonical corpus |
| Catalog `book_progress` | Persisted reading records, including completed/inactive | `CANONICAL_CURRENT` |
| Engagement `rating`, `reactions` | Current feedback | `CANONICAL_INTERACTION` |
| Engagement `reading_session_entries` | Replicated reading history | `REPLICATED_HISTORY` |

`CANONICAL_CURRENT` identifies the authoritative source, not a guarantee of one row
per user/book. All Catalog records are preserved with their source IDs and `isActive`.
The exporter never replaces them with the most recent replicated entry.

## Run

Use Python 3.11+ and migrate both databases first (Engagement requires V5).

```sh
pip install -e 'training[test]'
# Configure CATALOG_DB_URL and ENGAGEMENT_DB_URL as PostgreSQL URIs.
# Optional separate CATALOG_DB_USER / CATALOG_DB_PASSWORD and
# ENGAGEMENT_DB_USER / ENGAGEMENT_DB_PASSWORD override URI credentials.
python -m vellumhub_training.exporter --output target/training-export/reference
```

Use read-only database accounts with SELECT access to the listed tables. URLs use
`postgresql://host:5432/database`, not JDBC syntax. Never commit credentials or datasets.
Connection strings and errors from database drivers are not printed by the CLI.
Use a new output directory for every export; existing outputs are never overwritten.

## Dataset contract (schemaVersion 1)

- `books.jsonl`: `bookId`, `title`, `description`, `author`, sorted `genres`,
  `releaseYear`, `pageCount`, `publisher`, `updatedAt`. No cover URLs.
- `user_book_signals.jsonl`: `schemaVersion`, `userId`, `bookId`, `signalType`,
  `sourceService`, `sourceRole`, `sourceRecordId`, `occurredAt`, `payload`.
- `manifest.json`: schema version, export ID/time, snapshot time for each database,
  source roles, artifact paths, counts and SHA-256 hashes.

Signal payloads:

| signalType | payload |
|---|---|
| `READING_STATE` | status, currentPage, startedAt, endedAt, isActive |
| `READING_PROGRESS_HISTORY` | status, page, eventId, bookProgressId, historyQuality |
| `RATING` | stars, hasReview, recordedLocalAt, timeQuality |
| `REACTION` | type, createdAt, updatedAt, timeQuality |

Reading history preserves Catalog `eventId` and `occurredAt`. Missing either field
sets `historyQuality=LEGACY_UNKNOWN`; no consumer time is substituted. Training must
exclude these rows from time-dependent features unless it explicitly handles unknowns.
Duplicate events are preserved for audit; #200 owns online idempotency. Training must
make an explicit duplicate policy before summing signals.

Current reading records have no occurrence timestamp. Ratings store a timestamp
without timezone: `occurredAt=null`, `recordedLocalAt` retains the local value and
`timeQuality=TIMEZONE_UNKNOWN`. Reaction audit timestamps were backfilled by V3 for
legacy rows, so `occurredAt=null` and `timeQuality=AUDIT_MAY_BE_BACKFILLED`; the original
audit values are retained. None of these dates should silently become temporal labels.
Free-text reviews are never selected or exported; only `hasReview` is selected.

## Consistency, integrity and limits

Each source is read in its own read-only REPEATABLE READ transaction. The first SELECT
establishes its MVCC snapshot and records a descriptive capture time. Two databases
are **not globally atomic**; replication lag is expected and listed in the manifest.
Capture timestamps do not let you recreate a historical database snapshot.

Books sort by ID; signals sort by user ID, book ID, signal type, occurrence time
(nulls last), then string source record ID. JSON keys and UTC timestamps are normalized.
Repeated exports of unchanged source facts produce identical JSONL hashes; manifest IDs
and capture times change. Source IDs are strings, including numeric Engagement IDs.

An orphan signal (including null book IDs or references to soft-deleted corpus books)
fails the export. There is no implicit tombstone/filter policy in v1. Investigate the
source inconsistency or replication lag before retrying; no state is repaired by this job.
The Python API reports the offending source/type/record ID, while the CLI uses a sanitized
error message to prevent driver credentials from leaking.

Files are staged in a sibling temporary directory and renamed into place only after
validation and hashing. A sibling exclusive lock prevents simultaneous cooperating exports
to the same destination. Failed runs clean temporary files and their lock; a process kill
can leave a stale lock/temp directory, which an operator may remove after verifying no job
is running. Publication assumes a local filesystem with atomic directory rename; power-loss
durability and non-cooperating writers are outside this contract.

Version 1 materializes rows in memory for deterministic sorting. Size the offline worker
for the dataset; very large exports will need external sorting/streaming. Keep generated
datasets access-controlled: user UUIDs are pseudonymous identifiers, not anonymized data.

## Verify

```sh
python -m pytest training/tests -q
# Against a disposable PostgreSQL database (creates and drops isolated schemas):
TEST_POSTGRES_URL=postgresql://test:test@localhost:5432/training_test python -m pytest training/tests -q
```

The integration test applies the actual Catalog and Engagement SQL migrations and proves
corpus, feedback, legacy history, independent current state, stable hashes and orphan rejection.
CI supplies PostgreSQL and runs it on every PR. Without the test URL it is explicitly skipped.
#193 owns sampling, temporal splits, labels, weights and model evaluation; #194 owns import.

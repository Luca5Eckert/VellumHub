"""Read each source consistently; publish only a complete, validated dataset."""
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import shutil
import tempfile
from uuid import uuid4

from .manifest import manifest
from .models import signal_key
from .sources import catalog, engagement


def snapshot(config, reader):
    with config.connect() as connection:
        connection.execute("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY")
        # This first SELECT establishes the MVCC snapshot; time is descriptive, not a replay token.
        at = connection.execute("SELECT clock_timestamp() AS captured_at, pg_current_snapshot() AS snapshot").fetchone()["captured_at"]
        return at, reader(connection)


def write_jsonl(path, rows):
    digest = hashlib.sha256()
    count = 0
    with path.open("xb") as stream:
        for row in rows:
            data = (json.dumps(row, ensure_ascii=False, sort_keys=True,
                               separators=(",", ":"), allow_nan=False) + "\n").encode("utf-8")
            stream.write(data)
            digest.update(data)
            count += 1
    return dict(path=path.name, rows=count, sha256=digest.hexdigest())


def export_dataset(catalog_config, engagement_config, output):
    output = Path(output).absolute()
    output.parent.mkdir(parents=True, exist_ok=True)
    # A sibling lock prevents two cooperating exporters publishing to the same target.
    lock = output.with_name(f".{output.name}.export.lock")
    with lock.open("x"):
        try:
            if output.exists() or output.is_symlink():
                raise FileExistsError("Output already exists; choose a new dataset directory")
            temporary = Path(tempfile.mkdtemp(prefix=f".{output.name}.tmp-", dir=output.parent))
            try:
                catalog_at, (books, signals) = snapshot(catalog_config, catalog.read)
                engagement_at, feedback = snapshot(engagement_config, engagement.read)
                signals.extend(feedback)
                book_ids = {book["bookId"] for book in books}
                for row in signals:
                    if row["bookId"] not in book_ids:
                        raise ValueError(f"Signal references a missing or deleted book: {row['sourceService']} "
                                         f"{row['signalType']} record {row['sourceRecordId']}")
                export_id = uuid4()
                artifacts = {
                    "books": write_jsonl(temporary / "books.jsonl", sorted(books, key=lambda b: b["bookId"])),
                    "userBookSignals": write_jsonl(temporary / "user_book_signals.jsonl", sorted(signals, key=signal_key))}
                result = manifest(export_id, datetime.now(timezone.utc), catalog_at, engagement_at, artifacts)
                (temporary / "manifest.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
                if output.exists() or output.is_symlink():
                    raise FileExistsError("Output appeared during export")
                temporary.rename(output)
                return result
            finally:
                if temporary.exists():
                    shutil.rmtree(temporary)
        finally:
            lock.unlink()

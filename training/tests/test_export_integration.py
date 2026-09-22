"""Real PostgreSQL test against the actual service migrations, isolated by schema."""
from pathlib import Path
import json
import os
from uuid import uuid4

import pytest

from vellumhub_training.exporter.config import DatabaseConfig
from vellumhub_training.exporter.export import export_dataset


@pytest.mark.integration
def test_actual_schemas_export_facts_without_promoting_history(tmp_path):
    dsn = os.environ.get("TEST_POSTGRES_URL")
    if not dsn:
        pytest.skip("TEST_POSTGRES_URL is required for the real PostgreSQL integration test")
    import psycopg
    from psycopg import sql
    root = Path(__file__).resolve().parents[2]
    suffix = uuid4().hex
    schemas = {name: f"export_{name}_{suffix}" for name in ("catalog", "engagement")}
    configs = {}
    with psycopg.connect(dsn, autocommit=True) as admin:
        try:
            for name, schema in schemas.items():
                admin.execute(sql.SQL("CREATE SCHEMA {}").format(sql.Identifier(schema)))
                admin.execute(sql.SQL("SET search_path TO {}").format(sql.Identifier(schema)))
                migrations = root / "services" / f"{name}-service/src/main/resources/db/migration"
                for path in sorted(migrations.glob("V*.sql")):
                    admin.execute(path.read_text())
                configs[name] = DatabaseConfig(psycopg.conninfo.make_conninfo(dsn, options=f"-csearch_path={schema}"))
            book, other, user, progress, event = (uuid4() for _ in range(5))
            with configs["catalog"].connect() as connection:
                for key in (book, other):
                    connection.execute("""INSERT INTO books
                        (id,title,release_year,author,isbn,page_count,publisher,version,created_at)
                        VALUES (%s,'Book',2026,'Author','isbn',300,'Publisher',1,now())""", (key,))
                connection.execute("INSERT INTO book_genre_id SELECT %s,id FROM genres", (book,))
                connection.execute("""INSERT INTO book_progress
                    (id,book_id,user_id,reading_status,current_page,is_active)
                    VALUES (%s,%s,%s,'COMPLETED',300,false)""", (progress, book, user))
            with configs["engagement"].connect() as connection:
                connection.execute("INSERT INTO book_snapshot VALUES (%s)", (book,))
                connection.execute("INSERT INTO rating (user_id,book_id,stars,review) VALUES (%s,%s,5,'private')", (user,book))
                connection.execute("INSERT INTO reactions (id,user_id,book_snapshot_id,type_reaction) VALUES (1,%s,%s,'POSITIVE')", (user,book))
                for page, event_id, at in ((10, None, None),(20,event,"2026-09-21T12:00:00Z")):
                    connection.execute("""INSERT INTO reading_session_entries
                        (reading_session_id,user_id,book_snapshot_book_id,type,page_read,event_id,timestamp)
                        VALUES (%s,%s,%s,'READING',%s,%s,%s)""", (progress,user,book,page,event_id,at))
            manifest = export_dataset(configs["catalog"], configs["engagement"], tmp_path / "one")
            second = export_dataset(configs["catalog"], configs["engagement"], tmp_path / "two")
            assert manifest["artifacts"] == second["artifacts"]
            assert manifest["artifacts"]["books"]["rows"] == 2
            assert manifest["artifacts"]["userBookSignals"]["rows"] == 5
            signals = [json.loads(line) for line in (tmp_path / "one/user_book_signals.jsonl").read_text().splitlines()]
            current = next(s for s in signals if s["sourceRole"] == "CANONICAL_CURRENT")
            assert current["payload"]["status"] == "COMPLETED"
            history = [s for s in signals if s["sourceRole"] == "REPLICATED_HISTORY"]
            assert len(history) == 2
            assert {s["payload"]["historyQuality"] for s in history} == {"EVENT_METADATA", "LEGACY_UNKNOWN"}
            assert "private" not in (tmp_path / "one/user_book_signals.jsonl").read_text()
            # Orphans can occur across independent source snapshots and must fail closed.
            with configs["engagement"].connect() as connection:
                connection.execute("INSERT INTO rating (user_id,book_id,stars) VALUES (%s,%s,1)", (user,uuid4()))
            with pytest.raises(ValueError, match="missing or deleted book"):
                export_dataset(configs["catalog"], configs["engagement"], tmp_path / "orphan")
            assert not (tmp_path / "orphan").exists()
        finally:
            for schema in schemas.values():
                admin.execute(sql.SQL("DROP SCHEMA IF EXISTS {} CASCADE").format(sql.Identifier(schema)))

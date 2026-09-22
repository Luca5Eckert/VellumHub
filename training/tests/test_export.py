from datetime import datetime, timezone
import hashlib
import json
from uuid import UUID

import pytest
from vellumhub_training.exporter import export as module
from vellumhub_training.exporter.config import DatabaseConfig
from vellumhub_training.exporter.sources import catalog, engagement

BOOK = "00000000-0000-0000-0000-000000000001"
USER = "00000000-0000-0000-0000-000000000002"
AT = datetime(2026, 9, 21, tzinfo=timezone.utc)


def history(**changes):
    row = dict(id=2, user_id=USER, book_id=BOOK, reading_session_id=BOOK,
               type="READING", page_read=10, event_id=UUID(BOOK), timestamp=AT)
    return engagement.history(row | changes)


def state():
    return catalog.reading_state(dict(id=BOOK, user_id=USER, book_id=BOOK,
        reading_status="COMPLETED", current_page=300, started_at=None, end_at=None, is_active=False))


def snapshots(monkeypatch, feedback=None):
    def snapshot(config, reader):
        if reader == catalog.read:
            return AT, ([dict(bookId=BOOK)], [state()])
        return AT, feedback if feedback is not None else [history()]
    monkeypatch.setattr(module, "snapshot", snapshot)


def test_legacy_and_complete_history_have_explicit_provenance():
    assert history()["payload"]["historyQuality"] == "EVENT_METADATA"
    for fields in ({"event_id": None}, {"timestamp": None}, {"event_id": None, "timestamp": None}):
        item = history(**fields)
        assert item["payload"]["historyQuality"] == "LEGACY_UNKNOWN"
        assert item["sourceRole"] == "REPLICATED_HISTORY"
    assert history(timestamp=None)["occurredAt"] is None
    assert state()["sourceRole"] == "CANONICAL_CURRENT"
    assert state()["payload"]["isActive"] is False
    assert state()["payload"]["startedAt"] is None


def test_interactions_do_not_invent_occurrence_or_export_review_text():
    row = dict(id=1, user_id=USER, book_id=BOOK, stars=5, has_review=True,
               timestamp=datetime(2026, 9, 21), review="must not export")
    rating = engagement.rating(row)
    assert rating["occurredAt"] is None
    assert rating["payload"]["hasReview"] is True
    assert "must not export" not in json.dumps(rating)
    reaction = engagement.reaction(row | dict(type_reaction="VERY_POSITIVE", created_at=AT, updated_at=AT))
    assert reaction["sourceRole"] == "CANONICAL_INTERACTION"
    assert reaction["occurredAt"] is None
    assert reaction["payload"]["type"] == "VERY_POSITIVE"


def test_publish_hashes_order_and_repeatability(monkeypatch, tmp_path):
    snapshots(monkeypatch, [history(timestamp=None, id=3), history()])
    manifests = [module.export_dataset(None, None, tmp_path / name) for name in ("one", "two")]
    for artifact in ("books", "userBookSignals"):
        meta = manifests[0]["artifacts"][artifact]
        data = (tmp_path / "one" / meta["path"]).read_bytes()
        assert meta["sha256"] == hashlib.sha256(data).hexdigest()
        assert meta == manifests[1]["artifacts"][artifact]
        assert len(data.splitlines()) == meta["rows"]
    rows = [json.loads(line) for line in (tmp_path / "one/user_book_signals.jsonl").read_text().splitlines()]
    assert [r["signalType"] for r in rows] == ["READING_PROGRESS_HISTORY", "READING_PROGRESS_HISTORY", "READING_STATE"]
    assert rows[0]["occurredAt"] is not None and rows[1]["occurredAt"] is None
    assert rows[2]["payload"]["currentPage"] == 300
    assert manifests[0]["sources"]["catalog"]["snapshotAt"]
    assert list(tmp_path.glob(".*")) == []


def test_orphan_fails_without_publishing(monkeypatch, tmp_path):
    snapshots(monkeypatch, [history(book_id="missing")])
    with pytest.raises(ValueError, match="missing or deleted book"):
        module.export_dataset(None, None, tmp_path / "dataset")
    assert list(tmp_path.iterdir()) == []


def test_failed_write_cleans_partial_dataset(monkeypatch, tmp_path):
    snapshots(monkeypatch)
    original = module.write_jsonl
    def fail(path, rows):
        if path.name == "user_book_signals.jsonl":
            raise OSError("disk full")
        return original(path, rows)
    monkeypatch.setattr(module, "write_jsonl", fail)
    with pytest.raises(OSError):
        module.export_dataset(None, None, tmp_path / "dataset")
    assert list(tmp_path.iterdir()) == []


def test_existing_output_and_concurrent_export_are_preserved(monkeypatch, tmp_path):
    snapshots(monkeypatch)
    output = tmp_path / "dataset"
    output.mkdir()
    (output / "precious").write_text("existing")
    with pytest.raises(FileExistsError):
        module.export_dataset(None, None, output)
    assert (output / "precious").read_text() == "existing"
    lock = tmp_path / ".other.export.lock"
    lock.touch()
    with pytest.raises(FileExistsError):
        module.export_dataset(None, None, tmp_path / "other")
    assert lock.exists()


def test_config_and_cli_do_not_leak_credentials(monkeypatch, capsys):
    from vellumhub_training.exporter import __main__ as cli
    monkeypatch.setenv("CATALOG_DB_URL", "postgresql://user:SECRET@localhost/db")
    monkeypatch.setenv("ENGAGEMENT_DB_URL", "postgresql://user:SECRET@localhost/db")
    assert "SECRET" not in repr(DatabaseConfig.from_env("CATALOG"))
    def fail(*args):
        raise RuntimeError("postgresql://user:SECRET@localhost/db")
    monkeypatch.setattr(cli, "export_dataset", fail)
    monkeypatch.setattr("sys.argv", ["exporter", "--output", "unused"])
    assert cli.main() == 1
    assert "SECRET" not in capsys.readouterr().err

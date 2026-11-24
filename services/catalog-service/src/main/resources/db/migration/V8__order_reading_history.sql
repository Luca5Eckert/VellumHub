ALTER TABLE book_progress ADD COLUMN created_at TIMESTAMP(6) WITH TIME ZONE;
UPDATE book_progress SET created_at = COALESCE(started_at, end_at, '-infinity'::timestamptz);
ALTER TABLE book_progress ALTER COLUMN created_at SET DEFAULT clock_timestamp();
ALTER TABLE book_progress ALTER COLUMN created_at SET NOT NULL;

CREATE INDEX idx_book_progress_reading_history
    ON book_progress (user_id, book_id, created_at DESC, id DESC);

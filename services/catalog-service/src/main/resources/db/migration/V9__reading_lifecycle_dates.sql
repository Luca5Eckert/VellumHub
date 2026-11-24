ALTER TABLE book_progress
    ADD COLUMN target_end_date DATE,
    ADD COLUMN last_read_at TIMESTAMP(6) WITH TIME ZONE;

-- The ranked recommendation page is already limited by pgvector.
-- Avoid scanning the complete genres collection when fetching genres for those IDs.
-- Postgres does not automatically index the referencing side of a foreign key.
CREATE INDEX IF NOT EXISTS idx_recommendation_genres_book_id
    ON recommendation_genres (recommendation_book_id);

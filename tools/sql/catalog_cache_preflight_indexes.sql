-- Run manually with psql (autocommit ON) BEFORE the Flyway V6 deployment
-- on a populated Catalog database, to avoid an online deployment blocking writes.
-- Do not execute inside BEGIN/COMMIT or a migration tool's implicit transaction.
-- Requires an operator account with rights to create indexes in the Catalog schema.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_books_visible_created_id
    ON books (created_at, id)
    WHERE deleted_at IS NULL;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_book_genre_by_book
    ON book_genre_id (book_id);
-- Flyway V6 still runs and sees these exact index names (IF NOT EXISTS).
-- Check pg_indexes.indexdef and pg_index.indisvalid before allowing rollout.

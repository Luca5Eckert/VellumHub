-- Deterministic Catalog pagination: ORDER BY created_at ASC, id ASC,
-- with the same soft-delete restriction as the Book entity.
CREATE INDEX IF NOT EXISTS idx_books_visible_created_id
    ON books (created_at, id)
    WHERE deleted_at IS NULL;

-- PostgreSQL does not automatically index FK columns. Supports Hibernate
-- batch loading a page of Book.genres without repeated association scans.
CREATE INDEX IF NOT EXISTS idx_book_genre_by_book
    ON book_genre_id (book_id);

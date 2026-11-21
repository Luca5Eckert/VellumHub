-- Match the same document in the search predicate and the index.
CREATE FUNCTION catalog_book_search_document(title text, author text, description text, publisher text)
RETURNS tsvector
LANGUAGE sql IMMUTABLE PARALLEL SAFE
AS $$
    SELECT setweight(to_tsvector('pg_catalog.simple', coalesce(title, '')), 'A')
        || setweight(to_tsvector('pg_catalog.simple', coalesce(author, '')), 'B')
        || setweight(to_tsvector('pg_catalog.simple', coalesce(description, '')), 'C')
        || setweight(to_tsvector('pg_catalog.simple', coalesce(publisher, '')), 'D')
$$;

CREATE INDEX idx_books_visible_text_search
    ON books USING gin (catalog_book_search_document(title, author, description, publisher))
    WHERE deleted_at IS NULL;

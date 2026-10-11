-- A book's current public state is a reconstruction checkpoint, not a historical creation.
-- Deploy during a controlled writer cutover and after consumers understand BASELINE.
-- Versions are already managed by Book.@Version; no new version column is needed.
WITH active_books AS (
    SELECT b.id, b.version, b.title, b.description, b.release_year, b.cover_url,
           b.author, b.isbn, b.page_count, b.publisher,
           gen_random_uuid() AS event_id,
           now() AS observed_at,
           (SELECT coalesce(jsonb_agg(g.name ORDER BY g.name), '[]'::jsonb)
              FROM book_genre_id bg JOIN genres g ON g.id = bg.genre_id
             WHERE bg.book_id = b.id) AS genres
    FROM books b WHERE b.deleted_at IS NULL
)
INSERT INTO outbox_events(event_id, aggregate_type, aggregate_id, aggregate_version,
                          event_type, topic, message_key, payload)
SELECT a.event_id, 'BOOK', a.id, a.version, 'BookChangedEvent', 'book-events', a.id::text,
       jsonb_build_object(
           'eventId', a.event_id, 'occurredAt', a.observed_at, 'bookId', a.id,
           'revision', a.version, 'kind', 'BASELINE',
           'state', jsonb_build_object(
               'title', a.title, 'description', a.description, 'releaseYear', a.release_year,
               'coverUrl', a.cover_url, 'author', a.author, 'isbn', a.isbn,
               'pageCount', a.page_count, 'publisher', a.publisher, 'genres', a.genres))
FROM active_books a;


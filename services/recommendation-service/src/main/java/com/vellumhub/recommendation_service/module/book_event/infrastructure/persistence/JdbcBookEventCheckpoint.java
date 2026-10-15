package com.vellumhub.recommendation_service.module.book_event.infrastructure.persistence;

import com.vellumhub.recommendation_service.module.book_event.application.port.BookEventCheckpoint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.UUID;

/** Must be invoked inside the SAME @Transactional boundary as the projection mutation. */
@Repository
public class JdbcBookEventCheckpoint implements BookEventCheckpoint {
    private final JdbcTemplate jdbc;
    public JdbcBookEventCheckpoint(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public long lockAndRead(UUID bookId) {
        jdbc.update("INSERT INTO book_event_offsets(book_id, last_revision) VALUES (?, -1) ON CONFLICT DO NOTHING",
                bookId);
        Long offset = jdbc.queryForObject(
                "SELECT last_revision FROM book_event_offsets WHERE book_id=? FOR UPDATE", Long.class, bookId);
        if (offset == null) throw new IllegalStateException("Missing book checkpoint for " + bookId);
        return offset;
    }

    @Override public void applied(UUID bookId, long before, long next, UUID eventId) {
        // A shared unique event identity prevents reuse across Book and Reading Progress.
        // The insert and the per-aggregate revision fence belong to the caller's transaction.
        jdbc.update("INSERT INTO processed_events(event_id) VALUES (?)", eventId);
        int changed = jdbc.update("""
                UPDATE book_event_offsets SET last_revision=?
                WHERE book_id=? AND last_revision=?
                """, next, bookId, before);
        if (changed != 1) throw new IllegalStateException("Stale book checkpoint for " + bookId);
    }
}


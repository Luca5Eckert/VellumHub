package com.vellumhub.recommendation_service.module.reading_progress.infrastructure.persistence;
import com.vellumhub.recommendation_service.module.reading_progress.application.port.ReadingProgressCheckpoint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.UUID;

/** Caller owns the transaction. Row locking also serializes concurrent deliveries for an aggregate. */
@Repository
public class JdbcReadingProgressCheckpoint implements ReadingProgressCheckpoint {
    private final JdbcTemplate jdbc;
    public JdbcReadingProgressCheckpoint(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public long lockAndRead(UUID aggregateId) {
        jdbc.update("INSERT INTO reading_progress_offsets(aggregate_id,last_revision) VALUES (?,-1) ON CONFLICT DO NOTHING",
                    aggregateId);
        Long last = jdbc.queryForObject(
                "SELECT last_revision FROM reading_progress_offsets WHERE aggregate_id=? FOR UPDATE",
                Long.class, aggregateId);
        if (last == null) throw new IllegalStateException("Missing reading progress offset");
        return last;
    }
    @Override public void markApplied(UUID aggregateId, long revision, UUID eventId) {
        jdbc.update("INSERT INTO processed_events(event_id) VALUES (?)", eventId);
        int changed = jdbc.update(
                "UPDATE reading_progress_offsets SET last_revision=? WHERE aggregate_id=? AND last_revision=?",
                revision, aggregateId, revision-1);
        if (changed != 1) throw new IllegalStateException("Concurrent reading progress checkpoint update");
    }
}


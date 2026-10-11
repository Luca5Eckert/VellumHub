package com.vellumhub.catalog_service.module.outbox.infrastructure.persistence;
import com.vellumhub.catalog_service.module.outbox.application.model.OutboxRecord;
import com.vellumhub.catalog_service.module.outbox.application.port.OutboxStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
@Repository
public class JdbcOutboxStore implements OutboxStore {
    private final JdbcTemplate jdbc;
    public JdbcOutboxStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    // The earlier-revision fence ensures each progress stream is dispatched in order.
    // Leases and fencing tokens allow recovery after crash without long-running DB transactions.
    @Override @Transactional
    public Optional<OutboxRecord> claimNext(Duration lease) {
        UUID token = UUID.randomUUID();
        return jdbc.query("""
            WITH candidate AS (
              SELECT e.event_id FROM outbox_events e
              WHERE ((e.status='PENDING' AND e.next_attempt_at <= now())
                OR (e.status='IN_FLIGHT' AND e.lease_until <= now()))
                AND NOT EXISTS (
                    SELECT 1 FROM outbox_events earlier
                    WHERE earlier.aggregate_type=e.aggregate_type
                      AND earlier.aggregate_id=e.aggregate_id
                      AND earlier.aggregate_version < e.aggregate_version
                      AND earlier.status <> 'PUBLISHED'
                )
              ORDER BY e.created_at, e.aggregate_version, e.event_id
              LIMIT 1 FOR UPDATE OF e SKIP LOCKED
            )
            UPDATE outbox_events e SET status='IN_FLIGHT', claim_token=?,
               attempts=e.attempts+1, lease_until=now() + (? * interval '1 second')
            FROM candidate c WHERE e.event_id=c.event_id
            RETURNING e.event_id, e.claim_token, e.event_type, e.topic, e.message_key,
                      e.payload::text AS payload_text, e.attempts
            """, rs -> rs.next() ? Optional.of(new OutboxRecord(
                rs.getObject("event_id", UUID.class), rs.getObject("claim_token", UUID.class),
                rs.getString("event_type"), rs.getString("topic"), rs.getString("message_key"), rs.getString("payload_text"),
                rs.getInt("attempts"))) : Optional.empty(),
            token, Math.max(1L, lease.toSeconds()));
    }
    @Override @Transactional
    public void published(UUID eventId, UUID token) {
        jdbc.update("""
            UPDATE outbox_events SET status='PUBLISHED', published_at=now(),
                claim_token=NULL, lease_until=NULL, last_error=NULL
            WHERE event_id=? AND claim_token=? AND status='IN_FLIGHT'
            """, eventId, token);
    }
    @Override @Transactional
    public void failed(UUID eventId, UUID token, String error, Duration delay) {
        jdbc.update("""
            UPDATE outbox_events SET status='PENDING', claim_token=NULL, lease_until=NULL,
                last_error=?, next_attempt_at=now() + (? * interval '1 second')
            WHERE event_id=? AND claim_token=? AND status='IN_FLIGHT'
            """, error.substring(0, Math.min(1000, error.length())),
                Math.max(1L, delay.toSeconds()), eventId, token);
    }
    @Override public long pendingCount() {
        Long value = jdbc.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE status <> 'PUBLISHED'", Long.class);
        return value == null ? 0 : value;
    }
}



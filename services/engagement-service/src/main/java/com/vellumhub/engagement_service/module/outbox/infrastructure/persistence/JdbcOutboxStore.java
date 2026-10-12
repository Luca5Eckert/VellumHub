package com.vellumhub.engagement_service.module.outbox.infrastructure.persistence;

import com.vellumhub.engagement_service.module.outbox.application.model.OutboxRecord;
import com.vellumhub.engagement_service.module.outbox.application.port.OutboxStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/** SKIP LOCKED work claims, per-aggregate revision fence, and lease-token fencing. */
@Repository
public class JdbcOutboxStore implements OutboxStore {
    private final JdbcTemplate jdbc;
    public JdbcOutboxStore(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    @Override @Transactional
    public Optional<OutboxRecord> claimNext(Duration lease) {
        UUID token=UUID.randomUUID();
        return jdbc.query("""
          WITH candidate AS (
            SELECT e.event_id FROM outbox_events e
            WHERE ((e.status='PENDING' AND e.next_attempt_at<=now())
                    OR (e.status='IN_FLIGHT' AND e.lease_until<=now()))
              AND NOT EXISTS (
                SELECT 1 FROM outbox_events predecessor
                WHERE predecessor.aggregate_type=e.aggregate_type
                  AND predecessor.aggregate_id=e.aggregate_id
                  AND predecessor.aggregate_version<e.aggregate_version
                  AND predecessor.status<>'PUBLISHED'
              )
            ORDER BY e.created_at, e.aggregate_version, e.event_id
            LIMIT 1 FOR UPDATE OF e SKIP LOCKED
          )
          UPDATE outbox_events e
          SET status='IN_FLIGHT', claim_token=?, attempts=e.attempts+1,
              lease_until=now() + (? * interval '1 second')
          FROM candidate c WHERE e.event_id=c.event_id
          RETURNING e.event_id,e.claim_token,e.aggregate_type,e.event_type,
                    e.topic,e.message_key,e.payload::text AS payload_text,e.attempts
          """,rs->rs.next()?Optional.of(new OutboxRecord(
                    rs.getObject("event_id",UUID.class),rs.getObject("claim_token",UUID.class),
                    rs.getString("aggregate_type"),rs.getString("event_type"),
                    rs.getString("topic"),rs.getString("message_key"),
                    rs.getString("payload_text"),rs.getInt("attempts"))):Optional.empty(),
                token,Math.max(1L,lease.toSeconds()));
    }
    @Override @Transactional
    public void published(UUID id, UUID token) {
        jdbc.update("""
            UPDATE outbox_events SET status='PUBLISHED',published_at=now(),
                claim_token=NULL,lease_until=NULL,last_error=NULL
            WHERE event_id=? AND claim_token=? AND status='IN_FLIGHT'
            """,id,token);
    }
    @Override @Transactional
    public void failed(UUID id,UUID token,String error,Duration delay) {
        String safe=error==null?"unknown":error.substring(0,Math.min(1000,error.length()));
        jdbc.update("""
            UPDATE outbox_events SET status='PENDING',claim_token=NULL,lease_until=NULL,
                last_error=?,next_attempt_at=now() + (? * interval '1 second')
            WHERE event_id=? AND claim_token=? AND status='IN_FLIGHT'
            """,safe,Math.max(1L,delay.toSeconds()),id,token);
    }
    @Override public long pendingCount() {
        Long count=jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE status <> 'PUBLISHED'",Long.class);
        return count==null?0:count;
    }
}


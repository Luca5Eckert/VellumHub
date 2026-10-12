package com.vellumhub.engagement_service.module.outbox.application.port;
import com.vellumhub.engagement_service.module.outbox.application.model.OutboxRecord;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/** Persistence lifecycle of broker deliveries; no transactions remain open during network I/O. */
public interface OutboxStore {
    Optional<OutboxRecord> claimNext(Duration lease);
    /** @return true only when this claim still owns the event and its ACK was persisted. */
    boolean published(UUID eventId, UUID claimToken);
    void failed(UUID eventId, UUID claimToken, String error, Duration backoff);
    long pendingCount();
}


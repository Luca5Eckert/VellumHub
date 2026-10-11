package com.vellumhub.catalog_service.module.outbox.application.port;
import com.vellumhub.catalog_service.module.outbox.application.model.OutboxRecord;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
public interface OutboxStore {
    Optional<OutboxRecord> claimNext(Duration lease);
    void published(UUID eventId, UUID claimToken);
    void failed(UUID eventId, UUID claimToken, String error, Duration delay);
    long pendingCount();
}


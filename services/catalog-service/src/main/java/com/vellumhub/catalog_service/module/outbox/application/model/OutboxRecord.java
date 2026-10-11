package com.vellumhub.catalog_service.module.outbox.application.model;
import java.util.UUID;

/** Claimed, immutable message and its lease fencing token. */
public record OutboxRecord(UUID eventId, UUID claimToken, String eventType,
                           String topic, String messageKey, String payload, int attempts) {
    /** Compatibility constructor for existing reading-progress dispatcher tests. */
    public OutboxRecord(UUID eventId, UUID claimToken, String topic, String messageKey,
                        String payload, int attempts) {
        this(eventId, claimToken, "ReadingProgressChangedEvent", topic, messageKey, payload, attempts);
    }
}


package com.vellumhub.catalog_service.module.outbox.application.model;
import java.util.UUID;

/** Claimed immutable event, fenced by its leased claim token. */
public record OutboxRecord(UUID eventId, UUID claimToken, String eventType,
                           String topic, String messageKey, String payload, int attempts,
                           String wireFormat, int payloadSchemaVersion) {
    public OutboxRecord(UUID eventId, UUID claimToken, String eventType,
                        String topic, String messageKey, String payload, int attempts) {
        this(eventId, claimToken, eventType, topic, messageKey, payload, attempts, "JSON", 1);
    }
    public OutboxRecord(UUID eventId, UUID claimToken, String topic, String messageKey,
                        String payload, int attempts) {
        this(eventId, claimToken, "ReadingProgressChangedEvent", topic, messageKey, payload, attempts);
    }
}

package com.vellumhub.engagement_service.module.outbox.application.model;
import java.util.UUID;

/** Durable relay envelope; format and JSONB schema version are immutable per row. */
public record OutboxRecord(UUID eventId, UUID claimToken, String aggregateType,
        String eventType, String topic, String key, String json, int attempts,
        String wireFormat, int payloadSchemaVersion) {
    public OutboxRecord(UUID eventId, UUID claimToken, String aggregateType,
            String eventType, String topic, String key, String json, int attempts) {
        this(eventId,claimToken,aggregateType,eventType,topic,key,json,attempts,"JSON",1);
    }
}

package com.vellumhub.engagement_service.module.outbox.application.model;

import java.util.Objects;
import java.util.UUID;

/** Immutable integration event captured after a domain transition, before transaction commit. */
public record OutboxMessage(UUID eventId, String aggregateType, String aggregateId, long revision,
                            String eventType, String topic, String key, Object payload,
                            String wireFormat, int payloadSchemaVersion) {
    public OutboxMessage {
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(aggregateType);
        Objects.requireNonNull(aggregateId);
        Objects.requireNonNull(eventType);
        Objects.requireNonNull(topic);
        Objects.requireNonNull(key);
        Objects.requireNonNull(payload);
        if(revision < 0) throw new IllegalArgumentException("Aggregate revision must be nonnegative");
        if (!"JSON".equals(wireFormat) && !"AVRO".equals(wireFormat))
            throw new IllegalArgumentException("Invalid Engagement wire format");
        if (payloadSchemaVersion < 1) throw new IllegalArgumentException("Invalid JSONB payload version");
    }

    /** Source compatibility: existing callers and tests continue writing JSON. */
    public OutboxMessage(UUID eventId, String aggregateType, String aggregateId, long revision,
            String eventType, String topic, String key, Object payload) {
        this(eventId, aggregateType, aggregateId, revision, eventType, topic, key, payload, "JSON", 1);
    }
}


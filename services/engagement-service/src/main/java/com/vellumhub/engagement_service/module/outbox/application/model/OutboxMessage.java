package com.vellumhub.engagement_service.module.outbox.application.model;

import java.util.Objects;
import java.util.UUID;

/** Immutable integration event captured after a domain transition, before transaction commit. */
public record OutboxMessage(UUID eventId, String aggregateType, String aggregateId, long revision,
                            String eventType, String topic, String key, Object payload) {
    public OutboxMessage {
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(aggregateType);
        Objects.requireNonNull(aggregateId);
        Objects.requireNonNull(eventType);
        Objects.requireNonNull(topic);
        Objects.requireNonNull(key);
        Objects.requireNonNull(payload);
        if(revision < 0) throw new IllegalArgumentException("Aggregate revision must be nonnegative");
    }
}


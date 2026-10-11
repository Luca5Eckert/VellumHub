package com.vellumhub.catalog_service.module.outbox.application.model;
import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.readingprogress.ReadingProgressChangedEvent;
import java.util.Objects;
import java.util.UUID;
public record OutboxMessage(UUID eventId, UUID aggregateId, long revision, String eventType,
                            String topic, String messageKey, Object payload) {
    public OutboxMessage {
        Objects.requireNonNull(eventId); Objects.requireNonNull(aggregateId);
        Objects.requireNonNull(eventType); Objects.requireNonNull(topic);
        Objects.requireNonNull(messageKey); Objects.requireNonNull(payload);
        if (revision < 0) throw new IllegalArgumentException("Invalid aggregate revision");
    }
    public static OutboxMessage readingProgress(ReadingProgressChangedEvent event) {
        return new OutboxMessage(event.eventId(), event.bookProgressId(), event.revision(),
                "ReadingProgressChangedEvent", KafkaTopics.READING_PROGRESS,
                event.bookProgressId().toString(), event);
    }
}


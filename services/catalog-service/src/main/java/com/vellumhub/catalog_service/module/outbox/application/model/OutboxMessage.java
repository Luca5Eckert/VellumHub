package com.vellumhub.catalog_service.module.outbox.application.model;

import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.book.BookChangedEvent;
import com.vellumhub.kafka.contracts.readingprogress.ReadingProgressChangedEvent;
import java.util.Objects;
import java.util.UUID;

/** Immutable publication route bound to the same transaction as the aggregate revision. */
public record OutboxMessage(UUID eventId, String aggregateType, UUID aggregateId, long revision,
                            String eventType, String topic, String messageKey, Object payload,
                            String wireFormat, int payloadSchemaVersion) {
    public OutboxMessage {
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(aggregateType);
        Objects.requireNonNull(aggregateId);
        Objects.requireNonNull(eventType);
        Objects.requireNonNull(topic);
        Objects.requireNonNull(messageKey);
        Objects.requireNonNull(payload);
        if (revision < 0 || payloadSchemaVersion != 1)
            throw new IllegalArgumentException("Unsupported aggregate/payload revision");
        if (!"JSON".equals(wireFormat) && !"AVRO".equals(wireFormat))
            throw new IllegalArgumentException("Unsupported Outbox format");
        if ("AVRO".equals(wireFormat) && (!"BOOK".equals(aggregateType)
                || !"BookChangedEvent".equals(eventType) || !KafkaTopics.BOOK_EVENTS_AVRO.equals(topic)))
            throw new IllegalArgumentException("Avro Outbox route must be Book on book-events.avro");
    }
    /** Binary compatibility for existing legacy fixtures and clients. */
    public OutboxMessage(UUID eventId, String aggregateType, UUID aggregateId, long revision,
                         String eventType, String topic, String messageKey, Object payload) {
        this(eventId, aggregateType, aggregateId, revision, eventType, topic, messageKey, payload,
                "JSON", 1);
    }
    public static OutboxMessage readingProgress(ReadingProgressChangedEvent event) {
        return new OutboxMessage(event.eventId(), "BOOK_PROGRESS", event.bookProgressId(),
                event.revision(), "ReadingProgressChangedEvent", KafkaTopics.READING_PROGRESS,
                event.bookProgressId().toString(), event);
    }
    public static OutboxMessage book(BookChangedEvent event) {
        return new OutboxMessage(event.eventId(), "BOOK", event.bookId(), event.revision(),
                "BookChangedEvent", KafkaTopics.BOOK_EVENTS, event.bookId().toString(), event);
    }
    public static OutboxMessage bookAvro(BookChangedEvent event) {
        return new OutboxMessage(event.eventId(), "BOOK", event.bookId(), event.revision(),
                "BookChangedEvent", KafkaTopics.BOOK_EVENTS_AVRO, event.bookId().toString(),
                event, "AVRO", 1);
    }
}

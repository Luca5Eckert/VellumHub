package com.vellumhub.catalog_service.module.outbox.application.model;

import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.book.BookChangedEvent;
import com.vellumhub.kafka.contracts.readingprogress.ReadingProgressChangedEvent;
import java.util.Objects;
import java.util.UUID;

/** Immutable instruction to durably enqueue a domain change within the writer transaction. */
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
        if (!"JSON".equals(wireFormat) && !"AVRO".equals(wireFormat))
            throw new IllegalArgumentException("Unknown outbox wire format");
        if (payloadSchemaVersion < 1) throw new IllegalArgumentException("Invalid JSONB payload schema version");
        if (revision < 0) throw new IllegalArgumentException("Invalid aggregate revision");
    }

    /** Legacy source compatibility for tests and other callers. */
    public OutboxMessage(UUID eventId, String aggregateType, UUID aggregateId, long revision,
                         String eventType, String topic, String messageKey, Object payload) {
        this(eventId, aggregateType, aggregateId, revision, eventType, topic,
                messageKey, payload, "JSON", 1);
    }

    public static OutboxMessage readingProgress(ReadingProgressChangedEvent event) {
        return readingProgress(event, false);
    }

    /** Kafka wire routing is stored at enqueue, never recomputed by a new relay version. */
    public static OutboxMessage readingProgress(ReadingProgressChangedEvent event, boolean avro) {
        return new OutboxMessage(event.eventId(), "BOOK_PROGRESS", event.bookProgressId(),
                event.revision(), "ReadingProgressChangedEvent",
                avro ? KafkaTopics.READING_PROGRESS_AVRO : KafkaTopics.READING_PROGRESS,
                event.bookProgressId().toString(), event, avro ? "AVRO" : "JSON", 1);
    }

    public static OutboxMessage book(BookChangedEvent event) {
        return book(event, false);
    }

    /** Routing is a durable property of each row, never re-derived by the relay. */
    public static OutboxMessage book(BookChangedEvent event, boolean avro) {
        return new OutboxMessage(event.eventId(), "BOOK", event.bookId(), event.revision(),
                "BookChangedEvent", avro ? KafkaTopics.BOOK_EVENTS_AVRO : KafkaTopics.BOOK_EVENTS,
                event.bookId().toString(), event, avro ? "AVRO" : "JSON", 1);
    }
}


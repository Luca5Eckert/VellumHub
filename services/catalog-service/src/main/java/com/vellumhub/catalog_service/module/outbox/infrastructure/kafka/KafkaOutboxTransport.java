package com.vellumhub.catalog_service.module.outbox.infrastructure.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.vellumhub.catalog_service.module.outbox.application.model.OutboxRecord;
import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.book.BookChangedEvent;
import com.vellumhub.kafka.contracts.readingprogress.ReadingProgressChangedEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Single transport shared by Book and Reading Progress.
 * The allowlist prevents persisting arbitrary class names as deserialization instructions.
 */
@Component
@ConditionalOnProperty(name="app.outbox.enabled", havingValue="true")
public class KafkaOutboxTransport {
    private static final Map<String, Class<?>> TYPES = Map.of(
        "BookChangedEvent", BookChangedEvent.class,
        "ReadingProgressChangedEvent", ReadingProgressChangedEvent.class
    );
    private final KafkaTemplate<String, Object> kafka;
    private final BookAvroPublisher avro;
    private final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();

    @Autowired
    public KafkaOutboxTransport(KafkaTemplate<String, Object> kafka, BookAvroPublisher avro) {
        this.kafka = kafka;
        this.avro = avro;
    }
    /** Kept for existing JSON-only tests. */
    public KafkaOutboxTransport(KafkaTemplate<String, Object> kafka) {
        this.kafka = kafka;
        this.avro = null;
    }

    public CompletableFuture<SendResult<String, Object>> send(OutboxRecord record) {
        if ("AVRO".equals(record.wireFormat())) {
            if (!"BookChangedEvent".equals(record.eventType()) ||
                    !KafkaTopics.BOOK_EVENTS_AVRO.equals(record.topic()) ||
                    record.payloadSchemaVersion() != 1)
                throw new IllegalArgumentException("Unsupported Book Avro outbox routing");
            if (avro == null) throw new IllegalStateException("Avro publisher unavailable");
            try {
                BookChangedEvent event = mapper.readValue(record.payload(), BookChangedEvent.class);
                if (!record.eventId().equals(event.eventId()) ||
                        !record.messageKey().equals(event.bookId().toString()))
                    throw new IllegalArgumentException("Outbox Book identity differs from JSON payload");
                return avro.send(record.topic(), record.messageKey(), event);
            } catch (JsonProcessingException ex) {
                throw new IllegalArgumentException("Invalid Book JSONB, eventId=" + record.eventId(), ex);
            }
        }
        if (!"JSON".equals(record.wireFormat()) || record.payloadSchemaVersion() != 1)
            throw new IllegalArgumentException("Unsupported legacy Outbox payload");
        Class<?> targetType = TYPES.get(record.eventType());
        if (targetType == null) throw new IllegalArgumentException("Unsupported outbox type: " + record.eventType());
        if (!((targetType == BookChangedEvent.class && KafkaTopics.BOOK_EVENTS.equals(record.topic())) ||
              (targetType == ReadingProgressChangedEvent.class && KafkaTopics.READING_PROGRESS.equals(record.topic()))))
            throw new IllegalArgumentException("Mismatched outbox type and topic, eventId=" + record.eventId());
        try {
            return kafka.send(record.topic(), record.messageKey(), mapper.readValue(record.payload(), targetType));
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Invalid outbox JSON, eventId=" + record.eventId(), ex);
        }
    }
}


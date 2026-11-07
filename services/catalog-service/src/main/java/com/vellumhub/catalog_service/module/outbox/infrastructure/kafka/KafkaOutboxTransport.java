package com.vellumhub.catalog_service.module.outbox.infrastructure.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.vellumhub.catalog_service.module.outbox.application.model.OutboxRecord;
import com.vellumhub.kafka.avro.BookAvroMapper;
import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.book.BookChangedEvent;
import com.vellumhub.kafka.contracts.readingprogress.ReadingProgressChangedEvent;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import java.util.concurrent.CompletableFuture;

/** Sends by the immutable persisted route, not by current writer feature flags. */
@Component
@ConditionalOnProperty(name="app.outbox.enabled", havingValue="true")
public class KafkaOutboxTransport {
    private static final Map<String, Class<?>> TYPES = Map.of(
            "BookChangedEvent", BookChangedEvent.class,
            "ReadingProgressChangedEvent", ReadingProgressChangedEvent.class);
    private final KafkaTemplate<String, Object> json;
    private final KafkaTemplate<String, Object> avro;
    private final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();

    @Autowired
    public KafkaOutboxTransport(KafkaTemplate<String, Object> json,
            @Qualifier("bookAvroKafkaTemplate") ObjectProvider<KafkaTemplate<String, Object>> avro) {
        this(json, avro.getIfAvailable());
    }

    /** Preserves old non-Avro integration tests. */
    public KafkaOutboxTransport(KafkaTemplate<String, Object> json) {
        this(json, (KafkaTemplate<String, Object>) null);
    }

    public KafkaOutboxTransport(KafkaTemplate<String, Object> json, KafkaTemplate<String, Object> avro) {
        this.json = json;
        this.avro = avro;
    }

    public CompletableFuture<SendResult<String, Object>> send(OutboxRecord record) {
        Class<?> target = TYPES.get(record.eventType());
        if (target == null || record.payloadSchemaVersion() != 1)
            throw new IllegalArgumentException("Unsupported persisted event contract");
        boolean bookJson = target == BookChangedEvent.class
                && KafkaTopics.BOOK_EVENTS.equals(record.topic()) && "JSON".equals(record.wireFormat());
        boolean bookAvro = target == BookChangedEvent.class
                && KafkaTopics.BOOK_EVENTS_AVRO.equals(record.topic()) && "AVRO".equals(record.wireFormat());
        boolean progressJson = target == ReadingProgressChangedEvent.class
                && KafkaTopics.READING_PROGRESS.equals(record.topic()) && "JSON".equals(record.wireFormat());
        if (!bookJson && !bookAvro && !progressJson)
            throw new IllegalArgumentException("Mismatched Outbox route eventId=" + record.eventId());
        try {
            Object decoded = mapper.readValue(record.payload(), target);
            if (bookAvro) {
                if (avro == null) throw new IllegalStateException("Avro Registry producer not configured");
                var event = (BookChangedEvent) decoded;
                if (!event.bookId().toString().equals(record.messageKey())
                        || !event.eventId().equals(record.eventId()))
                    throw new IllegalArgumentException("Outbox payload identity/key mismatch");
                var headers = new RecordHeaders();
                headers.add("x-vellum-event-id", event.eventId().toString().getBytes(StandardCharsets.UTF_8));
                return avro.send(new ProducerRecord<String, Object>(record.topic(), null,
                        record.messageKey(), BookAvroMapper.encode(event), headers));
            }
            return json.send(record.topic(), record.messageKey(), decoded);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Invalid persisted Outbox JSON eventId=" + record.eventId(), ex);
        }
    }
}

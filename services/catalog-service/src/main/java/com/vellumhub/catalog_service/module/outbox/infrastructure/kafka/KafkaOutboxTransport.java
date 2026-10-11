package com.vellumhub.catalog_service.module.outbox.infrastructure.kafka;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.vellumhub.catalog_service.module.outbox.application.model.OutboxRecord;
import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.readingprogress.ReadingProgressChangedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import java.util.concurrent.CompletableFuture;
@Component
@ConditionalOnProperty(name="app.outbox.enabled", havingValue="true")
public class KafkaOutboxTransport {
    private final KafkaTemplate<String, Object> kafka;
    private final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
    public KafkaOutboxTransport(KafkaTemplate<String, Object> kafka) {
        this.kafka = kafka;
    }
    public CompletableFuture<SendResult<String,Object>> send(OutboxRecord record) {
        if (!KafkaTopics.READING_PROGRESS.equals(record.topic()))
            throw new IllegalArgumentException("Unexpected topic " + record.topic());
        try {
            ReadingProgressChangedEvent event = mapper.readValue(record.payload(), ReadingProgressChangedEvent.class);
            return kafka.send(record.topic(), record.messageKey(), event);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Invalid outbox payload for " + record.eventId(), ex);
        }
    }
}


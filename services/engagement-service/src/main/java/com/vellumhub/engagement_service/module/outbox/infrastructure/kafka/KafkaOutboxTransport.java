package com.vellumhub.engagement_service.module.outbox.infrastructure.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.vellumhub.engagement_service.module.outbox.application.model.OutboxRecord;
import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.engagement.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Explicit allowlist: source JSONB identity and Kafka wire route never change on retry. */
@Component
public class KafkaOutboxTransport {
    private static final Map<String,Class<?>> TYPES=Map.of(
            "RatingCreatedEvent",RatingCreatedEvent.class,
            "RatingUpdatedEvent",RatingUpdatedEvent.class,
            "RatingDeletedEvent",RatingDeletedEvent.class,
            "RatingBaselineEvent",RatingBaselineEvent.class,
            "ReactionChangedEvent",ReactionChangedEvent.class,
            "ReactionBaselineEvent",ReactionBaselineEvent.class);
    private final KafkaTemplate<String,Object> kafka;
    private final EngagementAvroPublisher avro;
    private final JsonMapper mapper=JsonMapper.builder().findAndAddModules().build();

    @Autowired
    public KafkaOutboxTransport(KafkaTemplate<String,Object> kafka,EngagementAvroPublisher avro) {
        this.kafka=kafka; this.avro=avro;
    }
    /** Existing JSON-only distributed tests use this constructor. */
    public KafkaOutboxTransport(KafkaTemplate<String,Object> kafka) {
        this.kafka=kafka; this.avro=null;
    }

    public CompletableFuture<SendResult<String,Object>> send(OutboxRecord record) {
        Class<?> type=TYPES.get(record.eventType());
        if(type==null) throw new IllegalArgumentException("Unregistered event type="+record.eventType());
        boolean reaction=type==ReactionChangedEvent.class || type==ReactionBaselineEvent.class;
        String expectedJson=reaction?KafkaTopics.ENGAGEMENT_REACTIONS:KafkaTopics.ENGAGEMENT_RATINGS;
        String expectedAvro=reaction?KafkaTopics.ENGAGEMENT_REACTIONS_AVRO:KafkaTopics.ENGAGEMENT_RATINGS_AVRO;
        if (record.payloadSchemaVersion()!=1)
            throw new IllegalArgumentException("Unsupported Engagement JSONB payload schema version");
        String expected="AVRO".equals(record.wireFormat())?expectedAvro:expectedJson;
        if (!expected.equals(record.topic()))
            throw new IllegalArgumentException("Event/topic/format mismatch, eventId="+record.eventId());
        try {
            Object fact=mapper.readValue(record.json(),type);
            var document=mapper.readTree(record.json());
            if (!record.eventId().toString().equals(document.path("eventId").asText()) ||
                    !record.key().equals(document.path("userId").asText()))
                throw new IllegalArgumentException("Engagement Outbox identity differs from JSONB");
            if ("AVRO".equals(record.wireFormat())) {
                if (avro==null) throw new IllegalStateException("Engagement Avro publisher unavailable");
                return avro.send(record.topic(),record.key(),fact);
            }
            if (!"JSON".equals(record.wireFormat()))
                throw new IllegalArgumentException("Unsupported wire format");
            return kafka.send(record.topic(),record.key(),fact);
        } catch(JsonProcessingException error) {
            throw new IllegalArgumentException("Invalid Engagement JSONB Outbox, eventId="+record.eventId(),error);
        }
    }
}

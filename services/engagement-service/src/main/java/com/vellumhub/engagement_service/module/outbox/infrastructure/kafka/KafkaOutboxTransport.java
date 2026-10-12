package com.vellumhub.engagement_service.module.outbox.infrastructure.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.vellumhub.engagement_service.module.outbox.application.model.OutboxRecord;
import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.engagement.RatingCreatedEvent;
import com.vellumhub.kafka.contracts.engagement.RatingUpdatedEvent;
import com.vellumhub.kafka.contracts.engagement.RatingDeletedEvent;
import com.vellumhub.kafka.contracts.engagement.ReactionChangedEvent;
import com.vellumhub.kafka.contracts.engagement.RatingBaselineEvent;
import com.vellumhub.kafka.contracts.engagement.ReactionBaselineEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Whitelisted event deserialization, preserving immutable eventId across publish attempts. */
@Component
public class KafkaOutboxTransport {
    private static final Map<String,Class<?>> TYPES=Map.of(
        "RatingCreatedEvent",RatingCreatedEvent.class,
        "RatingUpdatedEvent",RatingUpdatedEvent.class,
        "RatingDeletedEvent",RatingDeletedEvent.class,
        "ReactionChangedEvent",ReactionChangedEvent.class,
        "RatingBaselineEvent",RatingBaselineEvent.class,
        "ReactionBaselineEvent",ReactionBaselineEvent.class);
    private final KafkaTemplate<String,Object> kafka;
    private final JsonMapper mapper=JsonMapper.builder().findAndAddModules().build();
    public KafkaOutboxTransport(KafkaTemplate<String,Object> kafka) {this.kafka=kafka;}

    public CompletableFuture<SendResult<String,Object>> send(OutboxRecord record) {
        Class<?> type=TYPES.get(record.eventType());
        if(type==null) throw new IllegalArgumentException("Unregistered event type="+record.eventType());
        String requiredTopic=(type==ReactionChangedEvent.class || type==ReactionBaselineEvent.class) ?
                KafkaTopics.ENGAGEMENT_REACTIONS : KafkaTopics.ENGAGEMENT_RATINGS;
        if(!requiredTopic.equals(record.topic()))
            throw new IllegalArgumentException("Event/topic mismatch eventId="+record.eventId());
        try {return kafka.send(record.topic(),record.key(),mapper.readValue(record.json(),type));}
        catch(JsonProcessingException error) {
            throw new IllegalArgumentException("Invalid outbox payload eventId="+record.eventId(),error);
        }
    }
}



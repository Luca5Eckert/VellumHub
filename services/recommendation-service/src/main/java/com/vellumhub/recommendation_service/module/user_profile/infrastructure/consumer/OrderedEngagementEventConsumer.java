package com.vellumhub.recommendation_service.module.user_profile.infrastructure.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import com.vellumhub.kafka.contracts.KafkaConsumerGroups;
import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.engagement.*;
import java.util.UUID;
import com.vellumhub.recommendation_service.share.metrics.VellumHubMetrics;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import com.vellumhub.recommendation_service.share.application.EventProcessingOutcome;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Message type is selected by the producer's explicit, allowlisted JSON type alias. */
@Slf4j
@Component
public class OrderedEngagementEventConsumer {
    private final EngagementEventProcessor processor;
    private final VellumHubMetrics metrics;
    public OrderedEngagementEventConsumer(EngagementEventProcessor processor,VellumHubMetrics metrics) {
        this.processor=processor;this.metrics=metrics;
    }
    @KafkaListener(topics={KafkaTopics.ENGAGEMENT_RATINGS,KafkaTopics.ENGAGEMENT_REACTIONS},
            groupId=KafkaConsumerGroups.RECOMMENDATION_SERVICE,
            containerFactory="orderedEngagementContainerFactory")
    public void consume(ConsumerRecord<String, Object> record) {
        // Spring Kafka 4 supplies ConsumerRecord for an untyped multi-contract listener.
        // Explicitly unwrap the deserialized payload instead of treating the record as a fact.
        Object fact = record.value();
        String name=fact.getClass().getSimpleName();
        Timer.Sample sample=metrics.startKafkaProcessing();
        try {
            UUID id=eventId(fact);
            EventProcessingOutcome outcome = processor.apply(fact);
            if (outcome == EventProcessingOutcome.DUPLICATE) {
                metrics.recordKafkaDuplicate("engagement-events",name,KafkaConsumerGroups.RECOMMENDATION_SERVICE);
                log.debug("operation=engagement_event_duplicate eventId={} type={}",id,name);
            }
            metrics.recordKafkaConsumed("engagement-events",name,KafkaConsumerGroups.RECOMMENDATION_SERVICE);
            metrics.recordKafkaProcessingDuration(sample,"engagement-events",name,
                    KafkaConsumerGroups.RECOMMENDATION_SERVICE,
                    outcome == EventProcessingOutcome.DUPLICATE ? "duplicate" : "success");
            if (outcome == EventProcessingOutcome.APPLIED)
                log.debug("operation=engagement_fact_processed eventId={} type={}",id,name);
        } catch(RuntimeException error) {
            metrics.recordKafkaConsumeFailed("engagement-events",name,KafkaConsumerGroups.RECOMMENDATION_SERVICE);
            metrics.recordKafkaProcessingDuration(sample,"engagement-events",name,
                    KafkaConsumerGroups.RECOMMENDATION_SERVICE,"failure");
            log.error("operation=engagement_fact_failed type={} exception={}",name,
                    error.getClass().getSimpleName(),error);
            throw error;
        }
    }
    private UUID eventId(Object fact) {
        if(fact instanceof RatingCreatedEvent e) return e.eventId();
        if(fact instanceof RatingUpdatedEvent e) return e.eventId();
        if(fact instanceof RatingDeletedEvent e) return e.eventId();
        if(fact instanceof RatingBaselineEvent e) return e.eventId();
        if(fact instanceof ReactionBaselineEvent e) return e.eventId();
        if(fact instanceof ReactionChangedEvent e) return e.eventId();
        throw new IllegalArgumentException("Unexpected engagement fact type="+fact.getClass());
    }

}

package com.vellumhub.recommendation_service.module.user_profile.infrastructure.consumer;

import com.vellumhub.kafka.contracts.KafkaConsumerGroups;
import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.engagement.*;
import java.util.UUID;
import com.vellumhub.recommendation_service.share.metrics.VellumHubMetrics;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
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
    public void consume(Object fact) {
        String name=fact.getClass().getSimpleName();
        UUID id=eventId(fact);
        Timer.Sample sample=metrics.startKafkaProcessing();
        try {
            processor.apply(fact);
            metrics.recordKafkaConsumed("engagement-events",name,KafkaConsumerGroups.RECOMMENDATION_SERVICE);
            metrics.recordKafkaProcessingDuration(sample,"engagement-events",name,
                    KafkaConsumerGroups.RECOMMENDATION_SERVICE,"success");
            log.debug("operation=engagement_fact_processed eventId={} type={}",id,name);
        } catch(RuntimeException error) {
            metrics.recordKafkaConsumeFailed("engagement-events",name,KafkaConsumerGroups.RECOMMENDATION_SERVICE);
            metrics.recordKafkaProcessingDuration(sample,"engagement-events",name,
                    KafkaConsumerGroups.RECOMMENDATION_SERVICE,"failure");
            log.error("operation=engagement_fact_failed eventId={} type={}",id,name,error);
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

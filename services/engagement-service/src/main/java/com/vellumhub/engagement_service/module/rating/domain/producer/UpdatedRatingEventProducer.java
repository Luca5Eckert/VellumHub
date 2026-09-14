package com.vellumhub.engagement_service.module.rating.domain.producer;

import com.vellumhub.engagement_service.module.rating.domain.model.Rating;
import com.vellumhub.engagement_service.module.rating.domain.model.RatingUpdateResult;
import com.vellumhub.engagement_service.module.rating.domain.port.EventProducer;
import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.engagement.UpdatedRatingEvent;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class UpdatedRatingEventProducer {

    private final EventProducer<String, UpdatedRatingEvent> eventProducer;

    public UpdatedRatingEventProducer(EventProducer<String, UpdatedRatingEvent> eventProducer) {
        this.eventProducer = eventProducer;
    }

    public void produce(RatingUpdateResult updateResult) {
        Rating rating = updateResult.rating();
        UpdatedRatingEvent event = new UpdatedRatingEvent(
                UUID.randomUUID(),
                Instant.now(),
                rating.getId(),
                rating.getUserId(),
                rating.getBookId(),
                updateResult.oldStars(),
                rating.getStars(),
                updateResult.reviewChanged()
        );

        eventProducer.send(
                KafkaTopics.UPDATED_RATING,
                event.userId().toString(),
                event
        );
    }
}

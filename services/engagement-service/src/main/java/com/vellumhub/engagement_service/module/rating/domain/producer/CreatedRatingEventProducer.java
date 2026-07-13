package com.vellumhub.engagement_service.module.rating.domain.producer;

import com.vellumhub.engagement_service.module.rating.domain.model.Rating;
import com.vellumhub.engagement_service.module.rating.domain.port.EventProducer;
import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.engagement.CreatedRatingEvent;
import org.springframework.stereotype.Component;

@Component
public class CreatedRatingEventProducer {

    private final EventProducer<String, CreatedRatingEvent> eventProducer;

    public CreatedRatingEventProducer(EventProducer<String, CreatedRatingEvent> eventProducer) {
        this.eventProducer = eventProducer;
    }

    public void produce(Rating rating) {
        CreatedRatingEvent event = new CreatedRatingEvent(
                rating.getUserId(),
                rating.getBookId(),
                rating.getStars()
        );

        eventProducer.send(
                KafkaTopics.CREATED_RATING,
                event.userId().toString(),
                event
        );
    }


}

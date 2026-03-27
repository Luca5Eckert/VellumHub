package com.mrs.engagement_service.module.rating.domain.producer;

import com.mrs.engagement_service.module.rating.domain.event.CreatedRatingEvent;
import com.mrs.engagement_service.module.rating.domain.model.Rating;
import com.mrs.engagement_service.module.rating.domain.port.EventProducer;
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
                "created-rating",
                event.userId().toString(),
                event
        );
    }


}

package com.mrs.engagement_service.module.rating.domain.producer;

import com.mrs.engagement_service.module.rating.domain.event.UpdatedRatingEvent;
import com.mrs.engagement_service.module.rating.domain.model.Rating;
import com.mrs.engagement_service.module.rating.domain.port.EventProducer;

public class CreatedRatingEventProducer {

    private static final int SCORE_POSITIVE = 15;
    private static final int SCORE_NEUTRAL = 5;
    private static final int SCORE_NEGATIVE = -15;

    private final EventProducer<String, UpdatedRatingEvent> eventProducer;

    public CreatedRatingEventProducer(EventProducer<String, UpdatedRatingEvent> eventProducer) {
        this.eventProducer = eventProducer;
    }

    public void produce(Rating rating) {
        int score = calculateRecommendationScore(rating.getStars());

        UpdatedRatingEvent event = new UpdatedRatingEvent(
                rating.getUserId(),
                rating.getBookId(),
                score
        );

        eventProducer.send("updated-rating-event", event.userId().toString(), event);
    }

    private int calculateRecommendationScore(int stars) {
        return switch (stars) {
            case 5, 4 -> SCORE_POSITIVE;
            case 3    -> SCORE_NEUTRAL;
            case 2, 1 -> SCORE_NEGATIVE;
            default   -> 0;
        };
    }

}

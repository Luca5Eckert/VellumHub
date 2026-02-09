package com.mrs.engagement_service.module.rating.domain.handler;

import com.mrs.engagement_service.module.rating.domain.event.RatingEvent;
import com.mrs.engagement_service.module.rating.domain.exception.InvalidRatingException;
import com.mrs.engagement_service.module.book_progress.domain.model.Rating;
import com.mrs.engagement_service.module.rating.domain.port.EngagementRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class CreateRatingHandler {

    private final EngagementRepository engagementRepository;

    private final KafkaTemplate<String, RatingEvent> kafka;

    public CreateRatingHandler(EngagementRepository engagementRepository, KafkaTemplate<String, RatingEvent> kafka) {
        this.engagementRepository = engagementRepository;
        this.kafka = kafka;
    }

    public void handler(Rating rating){
        if(rating == null) throw new InvalidRatingException();

        engagementRepository.save(rating);

        RatingEvent ratingEvent = new RatingEvent(
                rating.getId(),
                rating.getUserId(),
                rating.getMediaId(),
                rating.getStars(),
                rating.getReview(),
                rating.getTimestamp()
        );

        kafka.send("engagement-created", rating.getUserId().toString(), ratingEvent);
    }

}

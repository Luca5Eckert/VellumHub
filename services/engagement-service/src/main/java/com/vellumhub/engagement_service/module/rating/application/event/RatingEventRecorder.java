package com.vellumhub.engagement_service.module.rating.application.event;

import com.vellumhub.engagement_service.module.outbox.application.model.OutboxMessage;
import com.vellumhub.engagement_service.module.outbox.application.port.OutboxWriter;
import com.vellumhub.engagement_service.module.rating.domain.model.Rating;
import com.vellumhub.engagement_service.module.rating.domain.model.RatingUpdateResult;
import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.engagement.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.UUID;

/** Writes committed facts in the same transaction as their Rating aggregate. */
@Slf4j
@Component
public class RatingEventRecorder {
    private final OutboxWriter outbox;
    public RatingEventRecorder(OutboxWriter outbox){this.outbox=outbox;}

    public void created(Rating rating) {
        var event=new RatingCreatedEvent(UUID.randomUUID(),Instant.now(),
                rating.getId(),rating.getUserId(),rating.getBookId(),rating.getStars(),rating.getVersion());
        append(rating,event.eventId(),event.revision(),"RatingCreatedEvent",event);
    }
    public void updated(RatingUpdateResult result) {
        Rating rating=result.rating();
        if(result.oldStars()==rating.getStars() && !result.reviewChanged()) return;
        var event=new RatingUpdatedEvent(UUID.randomUUID(),Instant.now(),
                rating.getId(),rating.getUserId(),rating.getBookId(),result.oldStars(),
                rating.getStars(),result.reviewChanged(),rating.getVersion());
        append(rating,event.eventId(),event.revision(),"RatingUpdatedEvent",event);
    }
    public void deleted(Rating rating) {
        var event=new RatingDeletedEvent(UUID.randomUUID(),Instant.now(),
                rating.getId(),rating.getUserId(),rating.getBookId(),rating.getStars(),rating.getVersion()+1);
        append(rating,event.eventId(),event.revision(),"RatingDeletedEvent",event);
    }
    private void append(Rating rating,UUID id,long revision,String kind,Object event) {
        outbox.append(new OutboxMessage(id,"RATING",rating.getId().toString(),revision,kind,
                KafkaTopics.ENGAGEMENT_RATINGS,rating.getUserId().toString(),event));
        log.debug("operation=engagement_event_enqueued eventId={} kind={} ratingId={} revision={}",
                id,kind,rating.getId(),revision);
    }
}


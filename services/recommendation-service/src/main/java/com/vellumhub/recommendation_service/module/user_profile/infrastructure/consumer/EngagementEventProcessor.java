package com.vellumhub.recommendation_service.module.user_profile.infrastructure.consumer;

import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.engagement.*;
import com.vellumhub.recommendation_service.module.user_profile.application.command.ReactionChangedCommand;
import com.vellumhub.recommendation_service.module.user_profile.application.command.UpdateUserProfileWithRatingCommand;
import com.vellumhub.recommendation_service.module.user_profile.application.use_case.ReactionChangedUseCase;
import com.vellumhub.recommendation_service.module.user_profile.application.use_case.UpdateUserProfileWithRatingUseCase;
import com.vellumhub.recommendation_service.module.user_profile.application.use_case.RemoveRatingFromProfileUseCase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/**
 * Acknowledges an event only after its profile delta and durable idempotency checkpoint
 * commit together. New topics are keyed by userId, while offsets protect individual aggregates.
 */
@Service
public class EngagementEventProcessor {
    private final JdbcTemplate jdbc;
    private final UpdateUserProfileWithRatingUseCase rating;
    private final RemoveRatingFromProfileUseCase deletion;
    private final ReactionChangedUseCase reaction;
    public EngagementEventProcessor(JdbcTemplate jdbc,UpdateUserProfileWithRatingUseCase rating,
             RemoveRatingFromProfileUseCase deletion,ReactionChangedUseCase reaction) {
        this.jdbc=jdbc;this.rating=rating;this.deletion=deletion;this.reaction=reaction;
    }

    @Transactional
    public void apply(Object fact) {
        final UUID id;
        final String aggregateType,aggregateId,topic;
        final long revision;
        if(fact instanceof RatingCreatedEvent e) {
            id=e.eventId();aggregateType="RATING";aggregateId=e.ratingId().toString();
            revision=e.revision();topic=KafkaTopics.ENGAGEMENT_RATINGS;
        } else if(fact instanceof RatingUpdatedEvent e) {
            id=e.eventId();aggregateType="RATING";aggregateId=e.ratingId().toString();
            revision=e.revision();topic=KafkaTopics.ENGAGEMENT_RATINGS;
        } else if(fact instanceof RatingDeletedEvent e) {
            id=e.eventId();aggregateType="RATING";aggregateId=e.ratingId().toString();
            revision=e.revision();topic=KafkaTopics.ENGAGEMENT_RATINGS;
        } else if(fact instanceof ReactionChangedEvent e) {
            id=e.eventId();aggregateType="REACTION";aggregateId=e.reactionId().toString();
            if(e.revision()==null) throw new IllegalArgumentException("Reaction revision required");
            revision=e.revision();topic=KafkaTopics.ENGAGEMENT_REACTIONS;
        } else throw new IllegalArgumentException("Unsupported engagement fact: "+fact.getClass());

        jdbc.update("""
            INSERT INTO engagement_event_offsets(aggregate_type,aggregate_id,last_revision)
            VALUES(?,?,-1) ON CONFLICT DO NOTHING
            """,aggregateType,aggregateId);
        long last=jdbc.queryForObject("""
            SELECT last_revision FROM engagement_event_offsets
            WHERE aggregate_type=? AND aggregate_id=? FOR UPDATE
            """,Long.class,aggregateType,aggregateId);
        if(revision<=last) return;
        // The initial revision can be >0 for aggregates created before the cutover.
        if(last>=0 && revision!=last+1)
            throw new IllegalStateException("Missing engagement revision aggregate="+aggregateId+
                    " expected="+(last+1)+" received="+revision);

        int accepted=jdbc.update("""
            INSERT INTO processed_engagement_events(event_id,topic,aggregate_type,aggregate_id)
            VALUES(?,?,?,?) ON CONFLICT DO NOTHING
            """,id,topic,aggregateType,aggregateId);
        if(accepted==0) return;
        if(fact instanceof RatingCreatedEvent e) {
            rating.executeStrict(new UpdateUserProfileWithRatingCommand(
                    e.userId(),e.bookId(),0,e.stars(),true));
        } else if(fact instanceof RatingUpdatedEvent e) {
            rating.executeStrict(new UpdateUserProfileWithRatingCommand(
                    e.userId(),e.bookId(),e.oldStars(),e.newStars(),false));
        } else if(fact instanceof RatingDeletedEvent e) {
            deletion.execute(e.userId(),e.bookId(),e.oldStars());
        } else if(fact instanceof ReactionChangedEvent e) {
            reaction.execute(ReactionChangedCommand.of(
                    e.userId(),e.bookId(),e.oldTypeReaction(),e.resultingTypeReaction()));
        }
        int changed=jdbc.update("""
            UPDATE engagement_event_offsets SET last_revision=?
            WHERE aggregate_type=? AND aggregate_id=? AND last_revision=?
            """,revision,aggregateType,aggregateId,last);
        if(changed!=1) throw new IllegalStateException("Stale engagement checkpoint "+aggregateId);
    }
}


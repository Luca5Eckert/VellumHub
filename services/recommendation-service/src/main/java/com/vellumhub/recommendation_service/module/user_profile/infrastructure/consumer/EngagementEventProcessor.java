package com.vellumhub.recommendation_service.module.user_profile.infrastructure.consumer;

import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.engagement.RatingBaselineEvent;
import com.vellumhub.kafka.contracts.engagement.RatingCreatedEvent;
import com.vellumhub.kafka.contracts.engagement.RatingDeletedEvent;
import com.vellumhub.kafka.contracts.engagement.RatingUpdatedEvent;
import com.vellumhub.kafka.contracts.engagement.ReactionBaselineEvent;
import com.vellumhub.kafka.contracts.engagement.ReactionChangedEvent;
import com.vellumhub.recommendation_service.module.user_profile.application.command.ReactionChangedCommand;
import com.vellumhub.recommendation_service.module.user_profile.application.command.UpdateUserProfileWithRatingCommand;
import com.vellumhub.recommendation_service.module.user_profile.application.use_case.ReactionChangedUseCase;
import com.vellumhub.recommendation_service.module.user_profile.application.use_case.RemoveRatingFromProfileUseCase;
import com.vellumhub.recommendation_service.module.user_profile.application.use_case.UpdateUserProfileWithRatingUseCase;
import com.vellumhub.recommendation_service.share.application.EventProcessingOutcome;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Acknowledges an event only after its profile delta and durable idempotency checkpoint
 * commit together. New topics are keyed by userId, while offsets protect individual aggregates.
 *
 * <p>Processing order inside one transaction: lock the aggregate checkpoint, classify
 * duplicates, verify revision continuity, record the event ID, apply the profile delta and
 * finally advance the checkpoint with a compare-and-set.</p>
 */
@Service
public class EngagementEventProcessor {

    private static final long NO_REVISION = -1;

    private final JdbcTemplate jdbc;
    private final UpdateUserProfileWithRatingUseCase rating;
    private final RemoveRatingFromProfileUseCase deletion;
    private final ReactionChangedUseCase reaction;

    public EngagementEventProcessor(JdbcTemplate jdbc,
                                    UpdateUserProfileWithRatingUseCase rating,
                                    RemoveRatingFromProfileUseCase deletion,
                                    ReactionChangedUseCase reaction) {
        this.jdbc = jdbc;
        this.rating = rating;
        this.deletion = deletion;
        this.reaction = reaction;
    }

    @Transactional
    public EventProcessingOutcome apply(Object fact) {
        EventIdentity event = identify(fact);
        long last = lockCheckpoint(event);

        if (event.revision() <= last) {
            return classifyAlreadyApplied(event);
        }
        verifyRevisionContinuity(fact, event, last);
        recordProcessedEvent(event);
        applyProfileDelta(fact);
        advanceCheckpoint(event, last);
        return EventProcessingOutcome.APPLIED;
    }

    /** Coordinates that identify one engagement fact and its ordered aggregate. */
    private record EventIdentity(UUID id, String aggregateType, String aggregateId, long revision, String topic) {

        static EventIdentity rating(UUID id, Long ratingId, long revision) {
            return new EventIdentity(id, "RATING", ratingId.toString(), revision, KafkaTopics.ENGAGEMENT_RATINGS);
        }

        static EventIdentity reaction(UUID id, Long reactionId, long revision) {
            return new EventIdentity(id, "REACTION", reactionId.toString(), revision, KafkaTopics.ENGAGEMENT_REACTIONS);
        }
    }

    private static EventIdentity identify(Object fact) {
        return switch (fact) {
            case RatingCreatedEvent e -> EventIdentity.rating(e.eventId(), e.ratingId(), e.revision());
            case RatingUpdatedEvent e -> EventIdentity.rating(e.eventId(), e.ratingId(), e.revision());
            case RatingDeletedEvent e -> EventIdentity.rating(e.eventId(), e.ratingId(), e.revision());
            case RatingBaselineEvent e -> EventIdentity.rating(e.eventId(), e.ratingId(), e.revision());
            case ReactionBaselineEvent e -> EventIdentity.reaction(e.eventId(), e.reactionId(), e.revision());
            case ReactionChangedEvent e -> {
                if (e.revision() == null) throw new IllegalArgumentException("Reaction revision required");
                yield EventIdentity.reaction(e.eventId(), e.reactionId(), e.revision());
            }
            default -> throw new IllegalArgumentException("Unsupported engagement fact: " + fact.getClass());
        };
    }

    private long lockCheckpoint(EventIdentity event) {
        jdbc.update("""
                INSERT INTO engagement_event_offsets(aggregate_type,aggregate_id,last_revision)
                VALUES(?,?,-1) ON CONFLICT DO NOTHING
                """, event.aggregateType(), event.aggregateId());
        return jdbc.queryForObject("""
                SELECT last_revision FROM engagement_event_offsets
                WHERE aggregate_type=? AND aggregate_id=? FOR UPDATE
                """, Long.class, event.aggregateType(), event.aggregateId());
    }

    /** Same event ID at an applied revision is a redelivery; any other identity is a collision. */
    private EventProcessingOutcome classifyAlreadyApplied(EventIdentity event) {
        Integer known = jdbc.queryForObject("""
                SELECT count(*) FROM processed_engagement_events
                WHERE event_id=? AND aggregate_type=? AND aggregate_id=?
                """, Integer.class, event.id(), event.aggregateType(), event.aggregateId());
        if (known != null && known == 1) {
            return EventProcessingOutcome.DUPLICATE;
        }
        throw new IllegalStateException("Engagement revision collision aggregate=" + event.aggregateId()
                + " revision=" + event.revision());
    }

    /**
     * Existing aggregates begin with a baseline at their persisted revision.
     * New aggregates must begin with a creation fact at revision zero.
     */
    private static void verifyRevisionContinuity(Object fact, EventIdentity event, long last) {
        boolean baseline = fact instanceof RatingBaselineEvent || fact instanceof ReactionBaselineEvent;
        boolean creation = fact instanceof RatingCreatedEvent
                || (fact instanceof ReactionChangedEvent change && change.oldTypeReaction() == null);

        if (last == NO_REVISION && !baseline && (event.revision() != 0 || !creation)) {
            throw new IllegalStateException("Missing engagement cutover baseline: " + event.aggregateId());
        }
        if (last >= 0 && event.revision() != last + 1) {
            throw new IllegalStateException("Missing engagement revision aggregate=" + event.aggregateId()
                    + " expected=" + (last + 1) + " received=" + event.revision());
        }
    }

    private void recordProcessedEvent(EventIdentity event) {
        int accepted = jdbc.update("""
                INSERT INTO processed_engagement_events(event_id,topic,aggregate_type,aggregate_id)
                VALUES(?,?,?,?) ON CONFLICT DO NOTHING
                """, event.id(), event.topic(), event.aggregateType(), event.aggregateId());
        if (accepted == 0) {
            throw new IllegalStateException("Event ID collision: " + event.id());
        }
    }

    /** Baselines only move the checkpoint; historical weight was already reflected in the profile. */
    private void applyProfileDelta(Object fact) {
        switch (fact) {
            case RatingCreatedEvent e -> rating.executeStrict(new UpdateUserProfileWithRatingCommand(
                    e.userId(), e.bookId(), 0, e.stars(), true));
            // Review-only updates are business facts but have no recommendation-weight delta.
            case RatingUpdatedEvent e when e.oldStars() != e.newStars() ->
                    rating.executeStrict(new UpdateUserProfileWithRatingCommand(
                            e.userId(), e.bookId(), e.oldStars(), e.newStars(), false));
            case RatingDeletedEvent e -> deletion.execute(e.userId(), e.bookId(), e.oldStars());
            case ReactionChangedEvent e -> reaction.execute(ReactionChangedCommand.of(
                    e.userId(), e.bookId(), e.oldTypeReaction(), e.resultingTypeReaction()));
            default -> { }
        }
    }

    private void advanceCheckpoint(EventIdentity event, long last) {
        int changed = jdbc.update("""
                UPDATE engagement_event_offsets SET last_revision=?
                WHERE aggregate_type=? AND aggregate_id=? AND last_revision=?
                """, event.revision(), event.aggregateType(), event.aggregateId(), last);
        if (changed != 1) {
            throw new IllegalStateException("Stale engagement checkpoint " + event.aggregateId());
        }
    }
}

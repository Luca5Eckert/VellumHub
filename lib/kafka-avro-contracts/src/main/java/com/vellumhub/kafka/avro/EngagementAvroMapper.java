package com.vellumhub.kafka.avro;

import com.vellumhub.kafka.contracts.engagement.*;
import org.apache.avro.specific.SpecificRecord;
import java.time.Instant;
import java.util.UUID;

/** Shared transport-only conversion; every fact retains eventId, revision and Kafka userId key. */
public final class EngagementAvroMapper {
    private EngagementAvroMapper() {}

    public static SpecificRecord toAvro(Object fact) {
        if (fact instanceof RatingCreatedEvent e) {
            return com.vellumhub.kafka.avro.engagement.RatingCreatedEvent.newBuilder()
                    .setEventId(e.eventId().toString())
                    .setOccurredAt(e.occurredAt().toString())
                    .setRatingId(e.ratingId())
                    .setUserId(e.userId().toString())
                    .setBookId(e.bookId().toString())
                    .setStars(e.stars())
                    .setRevision(e.revision())
                    .build();
        }
        if (fact instanceof RatingUpdatedEvent e) {
            return com.vellumhub.kafka.avro.engagement.RatingUpdatedEvent.newBuilder()
                    .setEventId(e.eventId().toString())
                    .setOccurredAt(e.occurredAt().toString())
                    .setRatingId(e.ratingId())
                    .setUserId(e.userId().toString())
                    .setBookId(e.bookId().toString())
                    .setOldStars(e.oldStars())
                    .setNewStars(e.newStars())
                    .setReviewChanged(e.reviewChanged())
                    .setRevision(e.revision())
                    .build();
        }
        if (fact instanceof RatingDeletedEvent e) {
            return com.vellumhub.kafka.avro.engagement.RatingDeletedEvent.newBuilder()
                    .setEventId(e.eventId().toString())
                    .setOccurredAt(e.occurredAt().toString())
                    .setRatingId(e.ratingId())
                    .setUserId(e.userId().toString())
                    .setBookId(e.bookId().toString())
                    .setOldStars(e.oldStars())
                    .setRevision(e.revision())
                    .build();
        }
        if (fact instanceof RatingBaselineEvent e) {
            return com.vellumhub.kafka.avro.engagement.RatingBaselineEvent.newBuilder()
                    .setEventId(e.eventId().toString())
                    .setOccurredAt(e.occurredAt().toString())
                    .setRatingId(e.ratingId())
                    .setUserId(e.userId().toString())
                    .setBookId(e.bookId().toString())
                    .setRevision(e.revision())
                    .build();
        }
        if (fact instanceof ReactionChangedEvent e) {
            if (e.revision() == null || e.newTypeReaction() == null ||
                    !e.newTypeReaction().equals(e.typeReaction()))
                throw new IllegalArgumentException("Invalid canonical ReactionChangedEvent");
            return com.vellumhub.kafka.avro.engagement.ReactionChangedEvent.newBuilder()
                    .setEventId(e.eventId().toString())
                    .setOccurredAt(e.occurredAt().toString())
                    .setReactionId(e.reactionId())
                    .setUserId(e.userId().toString())
                    .setBookId(e.bookId().toString())
                    .setOldTypeReaction(e.oldTypeReaction())
                    .setNewTypeReaction(e.newTypeReaction())
                    .setTypeReaction(e.typeReaction())
                    .setRevision(e.revision())
                    .build();
        }
        if (fact instanceof ReactionBaselineEvent e) {
            return com.vellumhub.kafka.avro.engagement.ReactionBaselineEvent.newBuilder()
                    .setEventId(e.eventId().toString())
                    .setOccurredAt(e.occurredAt().toString())
                    .setReactionId(e.reactionId())
                    .setUserId(e.userId().toString())
                    .setBookId(e.bookId().toString())
                    .setRevision(e.revision())
                    .build();
        }
        throw new IllegalArgumentException("Unsupported Engagement contract: " + (fact == null ? "null" : fact.getClass()));
    }

    public static Object fromAvro(String key, Object wire) {
        if (wire instanceof com.vellumhub.kafka.avro.engagement.RatingCreatedEvent e) {
            requireKey(key, e.getUserId());
            return new RatingCreatedEvent(UUID.fromString(e.getEventId()), Instant.parse(e.getOccurredAt()), e.getRatingId(), UUID.fromString(e.getUserId()), UUID.fromString(e.getBookId()), e.getStars(), e.getRevision());
        }
        if (wire instanceof com.vellumhub.kafka.avro.engagement.RatingUpdatedEvent e) {
            requireKey(key, e.getUserId());
            return new RatingUpdatedEvent(UUID.fromString(e.getEventId()), Instant.parse(e.getOccurredAt()), e.getRatingId(), UUID.fromString(e.getUserId()), UUID.fromString(e.getBookId()), e.getOldStars(), e.getNewStars(), e.getReviewChanged(), e.getRevision());
        }
        if (wire instanceof com.vellumhub.kafka.avro.engagement.RatingDeletedEvent e) {
            requireKey(key, e.getUserId());
            return new RatingDeletedEvent(UUID.fromString(e.getEventId()), Instant.parse(e.getOccurredAt()), e.getRatingId(), UUID.fromString(e.getUserId()), UUID.fromString(e.getBookId()), e.getOldStars(), e.getRevision());
        }
        if (wire instanceof com.vellumhub.kafka.avro.engagement.RatingBaselineEvent e) {
            requireKey(key, e.getUserId());
            return new RatingBaselineEvent(UUID.fromString(e.getEventId()), Instant.parse(e.getOccurredAt()), e.getRatingId(), UUID.fromString(e.getUserId()), UUID.fromString(e.getBookId()), e.getRevision());
        }
        if (wire instanceof com.vellumhub.kafka.avro.engagement.ReactionChangedEvent e) {
            requireKey(key, e.getUserId());
            if (e.getNewTypeReaction() == null ||
                    !e.getNewTypeReaction().equals(e.getTypeReaction()))
                throw new IllegalArgumentException("Reaction alias mismatch");
            return new ReactionChangedEvent(UUID.fromString(e.getEventId()), Instant.parse(e.getOccurredAt()), e.getReactionId(), UUID.fromString(e.getUserId()), UUID.fromString(e.getBookId()), e.getOldTypeReaction(), e.getNewTypeReaction(), e.getTypeReaction(), e.getRevision());
        }
        if (wire instanceof com.vellumhub.kafka.avro.engagement.ReactionBaselineEvent e) {
            requireKey(key, e.getUserId());
            return new ReactionBaselineEvent(UUID.fromString(e.getEventId()), Instant.parse(e.getOccurredAt()), e.getReactionId(), UUID.fromString(e.getUserId()), UUID.fromString(e.getBookId()), e.getRevision());
        }
        throw new IllegalArgumentException("Unexpected Engagement Avro record");
    }

    private static void requireKey(String key, String userId) {
        UUID id = UUID.fromString(userId);
        if (!id.toString().equals(key))
            throw new IllegalArgumentException("Engagement key does not match userId");
    }
}

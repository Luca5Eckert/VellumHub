package com.vellumhub.kafka.contracts.engagement;
import java.time.Instant;
import java.util.UUID;
/**
 * Technical cutover checkpoint for an existing Rating.
 * This is not a new rating action and must not adjust UserProfile weights.
 */
public record RatingBaselineEvent(UUID eventId, Instant occurredAt, Long ratingId,
                                  UUID userId, UUID bookId, long revision) {}


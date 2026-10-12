package com.vellumhub.kafka.contracts.engagement;
import java.time.Instant;
import java.util.UUID;

/** Immutable persisted rating transition, including the previous score needed for consumers. */
public record RatingUpdatedEvent(UUID eventId,Instant occurredAt,Long ratingId,UUID userId,
                                 UUID bookId,int oldStars,int newStars,boolean reviewChanged,
                                 long revision) {}


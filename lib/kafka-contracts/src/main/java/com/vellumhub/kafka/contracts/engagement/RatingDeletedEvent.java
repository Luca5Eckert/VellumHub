package com.vellumhub.kafka.contracts.engagement;
import java.time.Instant;
import java.util.UUID;

/** A deleted rating's last score permits reversing its prior contribution. */
public record RatingDeletedEvent(UUID eventId,Instant occurredAt,Long ratingId,UUID userId,
                                 UUID bookId,int oldStars,long revision) {}


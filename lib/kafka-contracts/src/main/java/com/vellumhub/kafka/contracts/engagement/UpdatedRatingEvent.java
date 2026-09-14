package com.vellumhub.kafka.contracts.engagement;

import java.time.Instant;
import java.util.UUID;

public record UpdatedRatingEvent(
        UUID eventId,
        Instant occurredAt,
        Long ratingId,
        UUID userId,
        UUID bookId,
        Integer oldStars,
        int newStars,
        boolean reviewChanged
) {
}

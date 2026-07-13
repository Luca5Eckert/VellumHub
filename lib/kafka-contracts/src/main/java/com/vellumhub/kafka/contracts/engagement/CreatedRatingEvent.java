package com.vellumhub.kafka.contracts.engagement;

import java.util.UUID;

public record CreatedRatingEvent(
        UUID userId,
        UUID bookId,
        int stars
) {
}

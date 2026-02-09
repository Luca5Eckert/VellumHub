package com.mrs.engagement_service.domain.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record RatingEvent(
        long id,
        UUID userId,
        UUID mediaId,
        int stars,
        String review,
        LocalDateTime timestamp
) {
}

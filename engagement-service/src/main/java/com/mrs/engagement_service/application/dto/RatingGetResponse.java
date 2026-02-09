package com.mrs.engagement_service.application.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record RatingGetResponse(
        Long id,
        UUID userId,
        UUID mediaId,
        int stars,
        String review,
        LocalDateTime timestamp
) {
}

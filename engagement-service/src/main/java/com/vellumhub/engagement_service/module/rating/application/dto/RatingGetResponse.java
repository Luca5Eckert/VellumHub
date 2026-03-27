package com.vellumhub.engagement_service.module.rating.application.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record RatingGetResponse(
        Long id,
        UUID userId,
        UUID bookId,
        int stars,
        String review,
        LocalDateTime timestamp
) {
}

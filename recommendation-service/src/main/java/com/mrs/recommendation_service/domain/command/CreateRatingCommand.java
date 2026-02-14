package com.mrs.recommendation_service.domain.command;

import java.util.UUID;

public record CreateRatingCommand(
        UUID userId,
        UUID bookId,
        int stars
) {
}

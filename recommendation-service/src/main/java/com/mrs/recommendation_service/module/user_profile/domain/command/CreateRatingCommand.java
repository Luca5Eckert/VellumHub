package com.mrs.recommendation_service.module.user_profile.domain.command;

import java.util.UUID;

public record CreateRatingCommand(
        UUID userId,
        UUID bookId,
        int stars
) {
}

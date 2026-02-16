package com.mrs.recommendation_service.module.user_profile.application.event;

import java.util.UUID;

public record CreatedRatingEvent(
        UUID userId,
        UUID bookId,
        int stars
) {
}

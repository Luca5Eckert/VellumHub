package com.vellumhub.recommendation_service.module.user_profile.presentation.event;

import java.util.UUID;

public record CreatedRatingEvent(
        UUID userId,
        UUID bookId,
        int stars
) {
}

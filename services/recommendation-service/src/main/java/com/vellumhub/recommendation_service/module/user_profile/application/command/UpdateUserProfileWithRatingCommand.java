package com.vellumhub.recommendation_service.module.user_profile.application.command;

import java.util.UUID;

public record UpdateUserProfileWithRatingCommand(
        UUID userId,
        UUID bookId,
        int oldStars,
        int newStars,
        boolean isNewRating
) {

}
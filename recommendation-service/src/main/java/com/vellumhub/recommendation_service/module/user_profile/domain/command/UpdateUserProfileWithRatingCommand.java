package com.vellumhub.recommendation_service.module.user_profile.domain.command;

import com.vellumhub.recommendation_service.module.user_profile.domain.model.RatingCategory;

import java.util.UUID;

public record UpdateUserProfileWithRatingCommand(
        UUID userId,
        UUID bookId,
        int oldStars,
        int newStars,
        boolean isNewRating
) {

}
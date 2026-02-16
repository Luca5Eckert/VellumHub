package com.mrs.recommendation_service.module.user_profile.domain.command;

import com.mrs.recommendation_service.module.user_profile.domain.model.RatingCategory;

import java.util.UUID;

public record UpdateUserProfileWithRatingCommand(
        UUID userId,
        UUID mediaId,
        int oldStars,
        int newStars,
        boolean isNewRating
) {
    public int getWeightAdjustment() {
        int newWeight = RatingCategory.fromStars(newStars).getWeight();
        int oldWeight = isNewRating ? 0 : RatingCategory.fromStars(oldStars).getWeight();

        return newWeight - oldWeight;
    }

    public boolean hasCategoryChanged() {
        if (isNewRating) return true;
        return RatingCategory.fromStars(oldStars) != RatingCategory.fromStars(newStars);
    }

}
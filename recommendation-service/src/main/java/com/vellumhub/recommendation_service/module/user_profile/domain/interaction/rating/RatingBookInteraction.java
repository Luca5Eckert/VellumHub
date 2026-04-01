package com.vellumhub.recommendation_service.module.user_profile.domain.interaction.rating;

import com.vellumhub.recommendation_service.module.book_feature.domain.model.BookFeature;
import com.vellumhub.recommendation_service.module.user_profile.domain.model.ProfileAdjustment;
import org.springframework.stereotype.Service;

@Service
public class RatingBookInteraction {

    public ProfileAdjustment toAdjustment(
            BookFeature bookFeature,
            int oldStars,
            int newStars,
            boolean isNewRating
    ) {
        if (!hasCategoryChanged(newStars, oldStars, isNewRating)) {
            return ProfileAdjustment.of(bookFeature.getBookId(), 0, bookFeature.getEmbedding());
        }

        int adjustmentWeight = getWeightAdjustment(newStars, oldStars, isNewRating);

        return ProfileAdjustment.of(bookFeature.getBookId(), adjustmentWeight, bookFeature.getEmbedding());
    }

    private int getWeightAdjustment(int newStars, int oldStars, boolean isNewRating) {
        int newWeight = RatingCategory.fromStars(newStars).getWeight();
        int oldWeight = isNewRating ? 0 : RatingCategory.fromStars(oldStars).getWeight();

        return newWeight - oldWeight;
    }

    private boolean hasCategoryChanged(int newStars, int oldStars, boolean isNewRating) {
        if (isNewRating) return true;
        return RatingCategory.fromStars(oldStars) != RatingCategory.fromStars(newStars);
    }

}

package com.vellumhub.recommendation_service.module.user_profile.domain.interaction.rating;

import com.vellumhub.recommendation_service.module.book_feature.domain.model.BookFeature;
import com.vellumhub.recommendation_service.module.user_profile.domain.model.ProfileAdjustment;
import org.springframework.stereotype.Service;

@Service
public class RatingBookInteraction {

    /**
     * Calculates the profile adjustment based on the user's rating in book.
     * @param bookFeature the features of the book being rated
     * @param oldStars the previous star rating given by the user (0 if it's a new rating)
     * @param newStars the new star rating given by the user
     * @param isNewRating indicates whether this is a new rating or an update to an existing rating
     * @return a ProfileAdjustment object representing the change to be applied to the user's profile
     */
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

    /**
     * Calculates the weight adjustment based on the change in star ratings.
     * @param newStars the new star rating given by the user
     * @param oldStars the previous star rating given by the user (0 if it's a new rating)
     * @param isNewRating indicates whether this is a new rating or an update to an existing rating
     * @return the weight adjustment to be applied to the user's profile, which is the difference between the new rating's weight and the old rating's weight
     */
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

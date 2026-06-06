package com.vellumhub.recommendation_service.module.user_profile.domain.interaction.progress;

import com.vellumhub.recommendation_service.module.book_feature.domain.model.BookFeature;
import com.vellumhub.recommendation_service.module.user_profile.domain.model.ProfileAdjustment;
import org.springframework.stereotype.Service;

@Service
public class BookProgressInteraction  {

    private final static float PAGE_PROGRESS_WEIGHT = 0.0125f;

    /**
     * Calculates the profile adjustment based on the user's progress in reading a book.
     * @param bookFeature The features of the book being read.
     * @param progress The progress type.
     * @param oldPage The page number before the progress update.
     * @param newPage The page number after the progress update.
     * @return  A ProfileAdjustment object containing the calculated adjustment for the user's profile based on the progress and the book features.
     */
    public ProfileAdjustment toAdjustment(BookFeature bookFeature, String progress, int oldPage, int newPage) {
        float adjustment = perTypeProgress(progress);

        adjustment += calculateScorePerProgress(oldPage, newPage);

        return new ProfileAdjustment(
                bookFeature.getBookId(),
                adjustment,
                bookFeature.getEmbedding()
        );
    }

    /**
     * Calculates the adjustment value based on the type of progress.
     * @param progress The type of progress.
     * @return A float value representing the adjustment.
     */
    private float perTypeProgress(String progress) {
        return Progress.of(progress).adjusment;
    }

    /**
     * Calculates the score adjustment based on the number of pages read.
     * @param oldPage The page number before the progress update.
     * @param newPage The page number after the progress update.
     * @return A float value representing the score adjustment based on the pages read and the defined weight for page progress.
     */
    private float calculateScorePerProgress(int oldPage, int newPage) {
        int readPages = newPage - oldPage;

        return (float) readPages * PAGE_PROGRESS_WEIGHT;
    }
}

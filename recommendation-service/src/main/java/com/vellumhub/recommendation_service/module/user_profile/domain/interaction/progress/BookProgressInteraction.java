package com.vellumhub.recommendation_service.module.user_profile.domain.interaction.progress;

import com.vellumhub.recommendation_service.module.book_feature.domain.model.BookFeature;
import com.vellumhub.recommendation_service.module.user_profile.domain.interaction.BookInteraction;
import com.vellumhub.recommendation_service.module.user_profile.domain.model.ProfileAdjustment;

public class BookProgressInteraction implements BookInteraction {

    private final String progress;
    private final int oldPage;
    private final int newPage;

    public BookProgressInteraction(String progress, int oldPage, int newPage) {
        this.progress = progress;
        this.oldPage = oldPage;
        this.newPage = newPage;
    }

    @Override
    public ProfileAdjustment toAdjustment(BookFeature bookFeature) {
        float adjustment = perTypeProgress();

        adjustment += calculateScorePerProgress();

        return new ProfileAdjustment(
                bookFeature.getBookId(),
                adjustment,
                bookFeature.getEmbedding()
        );
    }

    private float perTypeProgress() {
        return switch(progress) {
            case "WANT_TO_READ" -> 0.5f;
            case "READING" -> 1.0f;
            case "COMPLETED" -> 2.0f;
            default -> 0.0f;
        };
    }

    private float calculateScorePerProgress() {
        int readPages = newPage - oldPage;

        return (float) readPages / 0.1f;
    }
}

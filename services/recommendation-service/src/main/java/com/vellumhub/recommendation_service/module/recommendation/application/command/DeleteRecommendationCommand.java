package com.vellumhub.recommendation_service.module.recommendation.application.command;

import java.util.UUID;

public record DeleteRecommendationCommand(
        UUID bookId
) {
    public static DeleteRecommendationCommand of(UUID bookId) {
        return new DeleteRecommendationCommand(bookId);
    }
}

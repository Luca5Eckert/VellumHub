package com.vellumhub.recommendation_service.module.recommendation.application.command;

import java.util.List;
import java.util.UUID;

public record UpdateRecommendationCommand(
        UUID bookId,
        String title,
        String description,
        int releaseYear,
        String coverUrl,
        String author,
        List<String> genres
) {
    public static UpdateRecommendationCommand of(
            UUID bookId,
            String title,
            String description,
            int releaseYear,
            String coverUrl,
            String author,
            List<String> genres
    ) {
        return new UpdateRecommendationCommand(
                bookId,
                title,
                description,
                releaseYear,
                coverUrl,
                author,
                genres
        );
    }
}

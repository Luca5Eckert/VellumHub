package com.mrs.recommendation_service.module.recommendation.application.command;

import java.util.List;
import java.util.UUID;

public record CreateRecommendationCommand(
        UUID bookId,
        String title,
        String description,
        int releaseYear,
        String coverUrl,
        String author,
        List<String> genres
) {
    public static CreateRecommendationCommand of(
            UUID bookId,
            String title,
            String description,
            int releaseYear,
            String coverUrl,
            String author,
            List<Genre> genres
    ) {
        return new CreateRecommendationCommand(
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

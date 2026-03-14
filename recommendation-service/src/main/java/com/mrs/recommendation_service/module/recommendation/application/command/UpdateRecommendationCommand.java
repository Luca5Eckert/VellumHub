package com.mrs.recommendation_service.module.recommendation.application.command;

import com.mrs.recommendation_service.module.book_feature.domain.model.Genre;

import java.util.List;
import java.util.UUID;

public record UpdateRecommendationCommand(
        UUID bookId,
        String title,
        String description,
        int releaseYear,
        String coverUrl,
        String author,
        List<Genre> genres
) {
    public static UpdateRecommendationCommand of(
            UUID bookId,
            String title,
            String description,
            int releaseYear,
            String coverUrl,
            String author,
            List<Genre> genres
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

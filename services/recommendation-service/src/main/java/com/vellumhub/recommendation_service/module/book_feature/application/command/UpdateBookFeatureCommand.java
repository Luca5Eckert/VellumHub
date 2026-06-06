package com.vellumhub.recommendation_service.module.book_feature.application.command;

import java.util.List;
import java.util.UUID;

public record UpdateBookFeatureCommand(
        UUID bookId,
        String title,
        String author,
        String description,
        List<String> genres
) {
    public static UpdateBookFeatureCommand of(
            UUID bookId,
            String title,
            String author,
            String description,
            List<String> genres
    ) {
        return new UpdateBookFeatureCommand(
                bookId,
                title,
                author,
                description,
                genres
        );
    }
}

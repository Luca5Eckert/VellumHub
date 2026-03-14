package com.mrs.recommendation_service.module.book_feature.application.command;

import com.mrs.recommendation_service.module.book_feature.domain.model.Genre;

import java.util.List;
import java.util.UUID;

public record UpdateBookFeatureCommand(
        UUID bookId,
        List<Genre> genres
) {
    public static UpdateBookFeatureCommand of(UUID bookId, List<Genre> genres) {
        return new UpdateBookFeatureCommand(bookId, genres);
    }
}

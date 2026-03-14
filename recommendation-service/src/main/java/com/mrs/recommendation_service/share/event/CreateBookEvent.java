package com.mrs.recommendation_service.share.event;

import com.mrs.recommendation_service.module.book_feature.domain.model.Genre;

import java.util.List;
import java.util.UUID;

public record CreateBookEvent(
        UUID bookId,
        String title,
        String description,
        int releaseYear,
        String coverUrl,
        String author,
        List<Genre> genres
) {
}

package com.mrs.recommendation_service.module.recommendation.domain.model;

import com.mrs.recommendation_service.module.book_feature.domain.model.Genre;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Recommendation(
        UUID id,
        String title,
        String description,
        int releaseYear,
        String coverUrl,
        List<Genre> genres,
        Instant createAt,
        Instant updateAt
) {
}

package com.mrs.recommendation_service.module.recommendation.application.dto;

import com.mrs.recommendation_service.module.book_feature.domain.model.Genre;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RecommendationResponse (
        UUID id,
        String title,
        String description,
        int releaseYear,
        String coverUrl,
        List<Genre> genres
){}

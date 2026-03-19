package com.mrs.recommendation_service.module.recommendation.presentation.dto;

import java.util.List;
import java.util.UUID;

public record RecommendationResponse (
        UUID id,
        String title,
        String description,
        int releaseYear,
        String coverUrl,
        String author,
        List<Genre> genres
){}

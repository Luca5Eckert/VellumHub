package com.mrs.recommendation_service.application.dto;

import com.mrs.recommendation_service.domain.model.Genre;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MediaFeatureResponse(
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

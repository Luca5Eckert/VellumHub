package com.mrs.catalog_service.module.book.presentation.dto;

import com.mrs.catalog_service.module.book.domain.model.Genre;

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
        Instant createdAt,
        Instant updatedAt
) {
}

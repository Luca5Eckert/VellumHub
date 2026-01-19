package com.mrs.catalog_service.application.dto;

import com.mrs.catalog_service.domain.model.Genre;

import java.util.List;

public record UpdateMediaRequest(
        String title,
        String description,
        int releaseYear,
        String coverUrl,
        List<Genre> genres
) {
}

package com.mrs.catalog_service.application.dto;

import com.mrs.catalog_service.domain.model.Genre;

import java.util.List;

public record CreateMediaRequest(
        String title,
        String description,
        int releaseYear,
        MediaType mediaType,
        String coverUrl,
        List<Genre> genres
) {
}

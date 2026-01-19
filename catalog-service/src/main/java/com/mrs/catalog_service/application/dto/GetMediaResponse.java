package com.mrs.catalog_service.application.dto;

import com.mrs.catalog_service.domain.model.Genre;
import com.mrs.catalog_service.domain.model.MediaType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GetMediaResponse(
        UUID id,
        String title,
        String description,
        int releaseYear,
        MediaType mediaType,
        String coverUrl,
        List<Genre> genres,
        Instant createAt,
        Instant updateAt
) {
}

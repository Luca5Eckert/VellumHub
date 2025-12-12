package com.mrs.catalog_service.dto;

import com.mrs.catalog_service.model.Genre;
import com.mrs.catalog_service.model.MediaType;

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

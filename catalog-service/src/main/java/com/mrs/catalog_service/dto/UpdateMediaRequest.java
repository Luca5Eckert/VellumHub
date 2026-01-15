package com.mrs.catalog_service.dto;

import com.mrs.catalog_service.model.Genre;

import java.util.List;

public record UpdateMediaRequest(
        String title,
        String description,
        int releaseYear,
        String coverUrl,
        List<Genre> genres
) {
}

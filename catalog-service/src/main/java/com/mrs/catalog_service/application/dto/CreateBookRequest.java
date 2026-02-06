package com.mrs.catalog_service.application.dto;

import com.mrs.catalog_service.domain.model.Genre;

import java.util.List;

public record CreateBookRequest(
        String title,
        String description,
        int releaseYear,
        String coverUrl,
        String author,
        String isbn,
        int pageCount,
        String publisher,
        List<Genre> genres
) {
}

package com.mrs.catalog_service.module.book.application.dto;

import com.mrs.catalog_service.module.book.domain.model.Genre;

import java.util.List;

public record UpdateBookRequest(
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

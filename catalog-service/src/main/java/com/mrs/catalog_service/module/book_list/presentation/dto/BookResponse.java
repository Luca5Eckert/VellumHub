package com.mrs.catalog_service.module.book_list.presentation.dto;

import com.mrs.catalog_service.module.book.domain.model.Genre;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BookResponse(
        UUID id,
        String title,
        String description,
        int releaseYear,
        String coverUrl,
        String author,
        String isbn,
        int pageCount,
        String publisher,
        List<Genre> genres,
        Instant createdAt,
        Instant updatedAt
) {
}

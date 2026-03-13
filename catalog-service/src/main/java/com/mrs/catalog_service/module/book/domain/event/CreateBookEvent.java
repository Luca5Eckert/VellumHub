package com.mrs.catalog_service.module.book.domain.event;

import com.mrs.catalog_service.module.book.domain.model.Genre;

import java.util.List;
import java.util.UUID;

public record CreateBookEvent(
        UUID bookId,
        String title,
        String description,
        int releaseYear,
        String coverUrl,
        String author,
        List<Genre> genres
) {
}

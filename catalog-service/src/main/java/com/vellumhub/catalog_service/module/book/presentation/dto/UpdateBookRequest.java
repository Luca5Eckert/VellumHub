package com.mrs.catalog_service.module.book.presentation.dto;

import com.mrs.catalog_service.module.book.domain.model.Genre;

import java.util.List;
import java.util.Set;

public record UpdateBookRequest(
        String title,
        String description,
        int releaseYear,
        String coverUrl,
        String author,
        String isbn,
        int pageCount,
        String publisher,
        Set<String> genres
) {
}

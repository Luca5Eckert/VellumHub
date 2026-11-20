package com.vellumhub.catalog_service.module.book.presentation.dto;


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

package com.vellumhub.catalog_service.module.book.application.command;

import java.util.Set;

public record CreateBookCommand(
        String title,
        String description,
        int releaseYear,
        String author,
        String isbn,
        int pageCount,
        String publisher,
        String coverUrl,
        Set<String> genres
) {}
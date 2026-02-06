package com.mrs.catalog_service.module.book_request.domain.command;

import com.mrs.catalog_service.module.book.domain.model.Genre;

import java.util.List;

public record CreateBookRequestCommand(
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

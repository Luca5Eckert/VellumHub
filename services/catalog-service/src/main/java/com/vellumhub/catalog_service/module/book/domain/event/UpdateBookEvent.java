package com.vellumhub.catalog_service.module.book.domain.event;

import com.vellumhub.catalog_service.module.book.domain.model.Genre;

import java.util.List;
import java.util.UUID;

public record UpdateBookEvent(
        UUID bookId,
        String title,
        String description,
        int releaseYear,
        String coverUrl,
        String author,
        List<String> genres

) {
}

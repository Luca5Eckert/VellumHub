package com.mrs.catalog_service.module.book.domain.event;

import com.mrs.catalog_service.module.book.domain.model.Genre;

import java.util.List;
import java.util.UUID;

public record UpdateBookEvent(
        UUID bookId,
        List<Genre> genres
) {
}

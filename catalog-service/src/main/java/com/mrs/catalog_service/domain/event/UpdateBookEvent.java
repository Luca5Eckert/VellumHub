package com.mrs.catalog_service.domain.event;

import com.mrs.catalog_service.domain.model.Genre;

import java.util.List;
import java.util.UUID;

public record UpdateBookEvent(
        UUID bookId,
        List<Genre> genres
) {
}

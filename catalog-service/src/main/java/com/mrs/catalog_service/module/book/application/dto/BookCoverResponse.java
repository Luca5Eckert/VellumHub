package com.mrs.catalog_service.module.book.application.dto;

import java.util.UUID;

/**
 * DTO representing a book cover with its data encoded in Base64.
 * Used for bulk cover retrieval to avoid N+1 requests.
 */
public record BookCoverResponse(
        UUID bookId,
        String coverData,
        String contentType
) {
    /**
     * Creates an empty response indicating no cover is available.
     */
    public static BookCoverResponse empty(UUID bookId) {
        return new BookCoverResponse(bookId, null, null);
    }
}

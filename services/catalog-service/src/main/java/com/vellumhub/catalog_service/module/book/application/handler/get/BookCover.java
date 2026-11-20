package com.vellumhub.catalog_service.module.book.application.handler.get;

import java.util.UUID;

/**
 * Cover image loaded for a book, Base64 encoded. {@code coverData} and {@code contentType}
 * are {@code null} when the book has no readable cover.
 */
public record BookCover(UUID bookId, String coverData, String contentType) {

    public static BookCover empty(UUID bookId) {
        return new BookCover(bookId, null, null);
    }
}

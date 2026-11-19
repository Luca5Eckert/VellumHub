package com.vellumhub.catalog_service.module.book.application.command;

import java.util.Set;

/**
 * Partial update of a canonical Book.
 *
 * <p>{@code null} or blank text, non-positive numbers and a {@code null} genre set mean
 * "keep the current value"; see {@code Book#update}.</p>
 */
public record UpdateBookCommand(
        String title,
        String description,
        int releaseYear,
        String coverUrl,
        String author,
        String isbn,
        int pageCount,
        String publisher,
        Set<String> genres
) {}

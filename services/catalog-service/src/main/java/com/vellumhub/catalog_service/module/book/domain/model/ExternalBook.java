package com.vellumhub.catalog_service.module.book.domain.model;

/** A provider snapshot suitable for previewing a catalogue import. */
public record ExternalBook(
        String volumeId,
        String title,
        String author,
        String description,
        Integer releaseYear,
        Integer pageCount,
        String isbn,
        String publisher,
        String coverUrl
) {}

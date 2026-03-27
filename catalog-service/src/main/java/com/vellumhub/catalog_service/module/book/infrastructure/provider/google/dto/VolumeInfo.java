package com.mrs.catalog_service.module.book.infrastructure.provider.google.dto;

import java.util.List;

public record VolumeInfo(
        String title,
        List<String> authors,
        String publishedDate,
        String description,
        Integer pageCount,
        String publisher,
        ImageLinks imageLinks,
        List<IndustryIdentifier> industryIdentifiers
) {}

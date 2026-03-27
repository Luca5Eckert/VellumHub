package com.mrs.catalog_service.module.book_progress.application.dto;

import com.mrs.catalog_service.module.book_progress.domain.model.ReadingStatus;

import java.util.UUID;

public record BookProgressResponse(
        Long id,
        UUID bookId,
        ReadingStatus status,
        int currentPage
) {
}

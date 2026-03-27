package com.mrs.catalog_service.module.book_progress.domain.command;

import com.mrs.catalog_service.module.book_progress.domain.model.ReadingStatus;

import java.util.UUID;

public record DefineBookStatusCommand(
        UUID userId,
        UUID bookId,
        ReadingStatus readingStatus,
        int currentPage
) {
}

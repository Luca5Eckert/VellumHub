package com.vellumhub.catalog_service.module.book_progress.domain.event;

import java.util.UUID;

public record CreateBookProgressEvent(
        UUID userId,
        UUID bookId,
        String progress,
        int initPage
) {
}

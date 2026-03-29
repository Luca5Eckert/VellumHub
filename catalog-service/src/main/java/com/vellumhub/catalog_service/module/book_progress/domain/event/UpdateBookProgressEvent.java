package com.vellumhub.catalog_service.module.book_progress.domain.event;

import java.util.UUID;

public record UpdateBookProgressEvent(
        UUID userId,
        UUID bookId,
        String progress,
        int oldPage,
        int newPage
) {
}

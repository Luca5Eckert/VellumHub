package com.mrs.catalog_service.module.book_progress.domain.command;

import java.util.UUID;

public record UpdateBookProgressCommand(
        UUID userId,
        UUID bookId,
        int currentPage
) {
}

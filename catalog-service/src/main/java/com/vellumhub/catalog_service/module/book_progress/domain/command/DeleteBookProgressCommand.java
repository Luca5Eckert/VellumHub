package com.mrs.catalog_service.module.book_progress.domain.command;

import java.util.UUID;

public record DeleteBookProgressCommand(
        UUID userId,
        UUID bookId
) {
}

package com.vellumhub.catalog_service.module.book_progress.domain.command;

import com.vellumhub.catalog_service.module.book_progress.domain.model.ReadingStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DefineBookStatusCommand(
        UUID userId,
        UUID bookId,
        ReadingStatus readingStatus,
        int initialPage,
        OffsetDateTime startedAt,
        OffsetDateTime endAt
) {
}

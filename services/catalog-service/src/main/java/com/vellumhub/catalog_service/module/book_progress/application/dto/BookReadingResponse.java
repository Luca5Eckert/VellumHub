package com.vellumhub.catalog_service.module.book_progress.application.dto;

import com.vellumhub.catalog_service.module.book_progress.domain.model.ReadingStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record BookReadingResponse(
        UUID id,
        int readingNumber,
        ReadingStatus status,
        OffsetDateTime startedAt,
        OffsetDateTime endAt,
        int currentPage,
        int pageCount
) {}

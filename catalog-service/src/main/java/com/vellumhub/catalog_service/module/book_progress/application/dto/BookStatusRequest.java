package com.vellumhub.catalog_service.module.book_progress.application.dto;

import com.vellumhub.catalog_service.module.book_progress.domain.model.ReadingStatus;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public record BookStatusRequest(
        @NotNull ReadingStatus status,
        int currentPage,
        OffsetDateTime startedAt,
        OffsetDateTime endAt
) {
}

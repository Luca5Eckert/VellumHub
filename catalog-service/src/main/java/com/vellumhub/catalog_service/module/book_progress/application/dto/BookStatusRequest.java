package com.vellumhub.catalog_service.module.book_progress.application.dto;

import com.vellumhub.catalog_service.module.book_progress.domain.model.ReadingStatus;
import jakarta.validation.constraints.NotNull;

public record BookStatusRequest(
        @NotNull ReadingStatus status,
        int currentPage
) {
}

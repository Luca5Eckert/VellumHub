package com.vellumhub.catalog_service.module.book.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record GoogleBookImportRequest(
        @NotBlank @Schema(example = "zyTCAlFPjgYC") String volumeId
) {}

package com.mrs.engagement_service.module.rating.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateRatingRequest(
        @NotNull UUID bookId,
        @NotNull @Min(0) @Max(5) Integer stars,
        String review
) {
}

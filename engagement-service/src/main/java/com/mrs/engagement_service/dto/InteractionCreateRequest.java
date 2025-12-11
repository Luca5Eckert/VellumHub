package com.mrs.engagement_service.dto;

import com.mrs.engagement_service.model.InteractionType;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record InteractionCreateRequest(
        @NotBlank UUID userId,
        @NotBlank UUID mediaId,
        @NotBlank InteractionType type
) {
}

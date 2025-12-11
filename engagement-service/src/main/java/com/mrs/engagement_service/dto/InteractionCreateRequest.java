package com.mrs.engagement_service.dto;

import com.mrs.engagement_service.model.InteractionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record InteractionCreateRequest(
        @NotNull UUID userId,
        @NotNull UUID mediaId,
        @NotNull InteractionType type
) {
}

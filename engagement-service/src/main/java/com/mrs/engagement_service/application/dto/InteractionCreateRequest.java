package com.mrs.engagement_service.application.dto;

import com.mrs.engagement_service.domain.model.InteractionType;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record InteractionCreateRequest(
        @NotNull UUID userId,
        @NotNull UUID mediaId,
        @NotNull InteractionType type,
        double interactionValue
) {
}

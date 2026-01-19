package com.mrs.recommendation_service.domain.command;

import com.mrs.recommendation_service.domain.model.InteractionType;

import java.time.LocalDateTime;
import java.util.UUID;

public record UpdateUserProfileCommand(
        UUID userId,
        UUID mediaId,
        InteractionType interactionType,
        double interactionValue
) {
}

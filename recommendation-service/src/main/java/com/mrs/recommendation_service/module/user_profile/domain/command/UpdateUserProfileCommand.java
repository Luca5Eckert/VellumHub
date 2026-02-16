package com.mrs.recommendation_service.module.user_profile.domain.command;

import com.mrs.recommendation_service.module.user_profile.domain.model.InteractionType;

import java.util.UUID;

public record UpdateUserProfileCommand(
        UUID userId,
        UUID mediaId,
        InteractionType interactionType,
        double interactionValue
) {
}

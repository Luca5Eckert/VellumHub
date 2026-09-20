package com.vellumhub.recommendation_service.module.user_profile.application.command;

import java.util.UUID;

public record ReactionChangedCommand(
        UUID userId,
        UUID bookId,
        String oldReactionType,
        String newReactionType
) {
    public static ReactionChangedCommand of(
            UUID userId,
            UUID bookId,
            String oldReactionType,
            String newReactionType
    ) {
        return new ReactionChangedCommand(userId, bookId, oldReactionType, newReactionType);
    }
}

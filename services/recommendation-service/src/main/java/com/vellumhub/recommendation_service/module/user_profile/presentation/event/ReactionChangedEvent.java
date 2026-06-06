package com.vellumhub.recommendation_service.module.user_profile.presentation.event;

import java.util.UUID;

public record ReactionChangedEvent(
        UUID userId,
        UUID bookId,
        String typeReaction
) {

}
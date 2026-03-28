package com.vellumhub.recommendation_service.module.user_profile.presentation.event;

import java.util.UUID;

public record UpdateBookProgressEvent(
        UUID userId,
        UUID bookId,
        String progress
) {
}

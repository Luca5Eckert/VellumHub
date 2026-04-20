package com.vellumhub.recommendation_service.module.user_profile.presentation.event;

import java.util.UUID;

public record CreateBookProgressEvent(
        UUID userId,
        UUID bookId,
        String progress,
        int oldPage,
        int newPage
) {
}

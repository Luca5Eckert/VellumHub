package com.vellumhub.recommendation_service.module.user_profile.presentation.event;

public record UpdateBookProgressEvent(
        String userId,
        String bookId,
        String progress
) {
}

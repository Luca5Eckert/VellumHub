package com.vellumhub.recommendation_service.module.user_profile.application.command;


import java.util.UUID;

public record UpdateBookProgressCommand(
        UUID userId,
        UUID bookId,
        String progress
) {
    public static UpdateBookProgressCommand of(UUID userId, UUID bookId, String progress) {
        return new UpdateBookProgressCommand(userId, bookId, progress);
    }
}

package com.vellumhub.recommendation_service.module.user_profile.application.command;


public record UpdateBookProgressCommand(
        String userId,
        String bookId,
        String progress
) {
    public static UpdateBookProgressCommand of(String userId, String bookId, String progress) {
        return new UpdateBookProgressCommand(userId, bookId, progress);
    }
}

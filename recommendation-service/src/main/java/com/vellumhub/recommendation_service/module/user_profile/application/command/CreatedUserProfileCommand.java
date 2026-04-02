package com.vellumhub.recommendation_service.module.user_profile.application.command;

import java.util.List;
import java.util.UUID;

public record CreatedUserProfileCommand(
        UUID userId,
        List<String> genres,
        String about
) {
    public static CreatedUserProfileCommand of(UUID userId, List<String> genres, String about) {
        return new CreatedUserProfileCommand(userId, genres, about);
    }
}

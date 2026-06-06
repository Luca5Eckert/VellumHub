package com.vellumhub.user_service.module.user_preference.application.command;

import java.util.List;
import java.util.UUID;

public record CreateUserPreferenceCommand(
        UUID userId,
        List<String> genres,
        String about
) {
}

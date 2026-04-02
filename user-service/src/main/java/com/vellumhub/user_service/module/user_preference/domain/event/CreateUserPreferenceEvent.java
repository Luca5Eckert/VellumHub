package com.vellumhub.user_service.module.user_preference.domain.event;


import java.util.List;
import java.util.UUID;

public record CreateUserPreferenceEvent (
        UUID userId,
        List<String> genres,
        String about
) {
}

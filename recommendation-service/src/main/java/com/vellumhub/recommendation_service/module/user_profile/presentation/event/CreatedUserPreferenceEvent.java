package com.vellumhub.recommendation_service.module.user_profile.presentation.event;

import java.util.List;
import java.util.UUID;

public record CreatedUserPreferenceEvent(
        UUID userId,
        List<String> genres,
        String about
) {
}

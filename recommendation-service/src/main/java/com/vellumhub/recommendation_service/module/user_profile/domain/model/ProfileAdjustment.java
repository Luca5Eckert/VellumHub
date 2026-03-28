package com.vellumhub.recommendation_service.module.user_profile.domain.model;

import java.util.UUID;

public record ProfileAdjustment(
        UUID userId,
        float adjustment,
        float[] embedding
) {
}

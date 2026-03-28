package com.vellumhub.recommendation_service.module.user_profile.domain.model;

import java.util.UUID;

public record ProfileAdjustment(
        UUID bookId,
        float adjustment,
        float[] embedding
) {

    public static ProfileAdjustment of(UUID bookId, float adjustment, float[] embedding) {
        return new ProfileAdjustment(bookId, adjustment, embedding);
    }

}

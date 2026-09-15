package com.vellumhub.recommendation_service.module.user_profile.domain.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserProfileZeroAdjustmentTest {

    @Test
    void shouldPreserveProfileVectorExactlyWhenAdjustmentIsZero() {
        UserProfile profile = UserProfile.create(UUID.randomUUID());
        float[] vectorBeforeUpdate = profile.getProfileVector().clone();
        float[] bookEmbedding = new float[384];
        bookEmbedding[0] = 1.0f;

        profile.applyUpdate(new ProfileAdjustment(UUID.randomUUID(), 0.0f, bookEmbedding));

        assertThat(profile.getProfileVector()).containsExactly(vectorBeforeUpdate);
    }
}

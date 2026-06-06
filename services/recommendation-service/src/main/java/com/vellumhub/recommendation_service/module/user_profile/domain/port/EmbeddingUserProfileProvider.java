package com.vellumhub.recommendation_service.module.user_profile.domain.port;

import java.util.List;

public interface EmbeddingUserProfileProvider {
    float[] of(List<String> genres, String about);
}

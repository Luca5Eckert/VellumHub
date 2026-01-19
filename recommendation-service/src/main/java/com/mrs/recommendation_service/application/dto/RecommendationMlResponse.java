package com.mrs.recommendation_service.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mrs.recommendation_service.domain.model.Recommendation;

import java.util.List;
import java.util.UUID;

public record RecommendationMlResponse(
        @JsonProperty("user_id") UUID userId,
        List<Recommendation> recommendations,
        Integer count
) {}
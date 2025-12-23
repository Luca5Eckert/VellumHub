package com.mrs.recommendation_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mrs.recommendation_service.model.Recommendation;

import java.util.List;
import java.util.UUID;

public record RecommendationResponse(
        @JsonProperty("user_id") UUID userId,
        List<Recommendation> recommendations,
        Integer count
) {}
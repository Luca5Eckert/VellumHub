package com.mrs.recommendation_service.domain.model;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record Recommendation(
        @JsonProperty("media_id") UUID mediaId,
        List<String> genres,
        @JsonProperty("popularity_score") Double popularityScore,
        @JsonProperty("recommendation_score") Double recommendationScore,
        @JsonProperty("content_score") Double contentScore
) {}
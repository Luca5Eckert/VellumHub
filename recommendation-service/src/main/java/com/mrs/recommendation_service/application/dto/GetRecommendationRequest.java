package com.mrs.recommendation_service.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mrs.recommendation_service.domain.model.UserProfile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream. Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GetRecommendationRequest {

    @JsonProperty("user_profile")
    private UserProfileDTO userProfile;

    private Integer limit;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserProfileDTO {

        @JsonProperty("user_id")
        private String userId;

        @JsonProperty("genre_scores")
        private Map<String, Double> genreScores;

        @JsonProperty("interacted_media_ids")
        private List<String> interactedMediaIds;

        @JsonProperty("total_engagement_score")
        private Double totalEngagementScore;

        public static UserProfileDTO fromEntity(UserProfile userProfile) {
            return UserProfileDTO.builder()
                    .userId(userProfile.getUserId().toString())
                    .genreScores(userProfile.getGenreScores())
                    .interactedMediaIds(
                            userProfile.getInteractedMediaIds() != null
                                    ? userProfile.getInteractedMediaIds().stream()
                                    .map(UUID::toString)
                                    .collect(Collectors.toList())
                                    : List.of()
                    )
                    . totalEngagementScore(userProfile.getTotalEngagementScore())
                    .build();
        }
    }
}
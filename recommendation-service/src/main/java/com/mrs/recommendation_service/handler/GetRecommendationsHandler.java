package com.mrs.recommendation_service.handler;

import com.mrs.recommendation_service.dto.GetRecommendationRequest;
import com.mrs.recommendation_service.dto.RecommendationResponse;
import com.mrs.recommendation_service.model.Recommendation;
import com.mrs.recommendation_service.model.UserProfile;
import com.mrs.recommendation_service.repository.UserProfileRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

@Component
public class GetRecommendationsHandler {

    private final UserProfileRepository userProfileRepository;
    private final RestClient restClient;

    @Value("${ml. service.url:http://ml-service:5000}")
    private String mlServiceUrl;

    public GetRecommendationsHandler(UserProfileRepository userProfileRepository, RestClient.Builder restClientBuilder) {
        this.userProfileRepository = userProfileRepository;
        this.restClient = restClientBuilder.build();
    }

    public List<Recommendation> execute(UUID userId) {
        UserProfile userProfile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User profile not found"));

        GetRecommendationRequest request = GetRecommendationRequest.builder()
                .userProfile(GetRecommendationRequest.UserProfileDTO.fromEntity(userProfile))
                .limit(10)
                .build();

        RecommendationResponse recommendationResponse = restClient.post()
                .uri(mlServiceUrl + "/api/recommendations")
                .body(request)
                .retrieve()
                .body(RecommendationResponse.class);

        assert recommendationResponse != null;

        return recommendationResponse.recommendations();
    }

}

package com.mrs.recommendation_service.domain.handler;

import com.mrs.recommendation_service.application.dto.GetRecommendationRequest;
import com.mrs.recommendation_service.application.dto.RecommendationMlResponse;
import com.mrs.recommendation_service.domain.exception.user_profile.UserProfileNotFoundException;
import com.mrs.recommendation_service.domain.model.Recommendation;
import com.mrs.recommendation_service.domain.model.UserProfile;
import com.mrs.recommendation_service.infrastructure.repository.UserProfileRepository;
import org.springframework.beans.factory.annotation.Value;
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
                .orElseThrow(() -> new UserProfileNotFoundException(userId.toString()));

        GetRecommendationRequest request = GetRecommendationRequest.builder()
                .userProfile(GetRecommendationRequest.UserProfileDTO.fromEntity(userProfile))
                .limit(10)
                .build();

        RecommendationMlResponse recommendationMlResponse = restClient.post()
                .uri(mlServiceUrl + "/api/recommendations")
                .body(request)
                .retrieve()
                .body(RecommendationMlResponse.class);

        assert recommendationMlResponse != null;

        return recommendationMlResponse.recommendations();
    }

}

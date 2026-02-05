package com.mrs.recommendation_service.domain.handler.media_feature;

import com.mrs.recommendation_service.application.dto.GetRecommendationRequest;
import com.mrs.recommendation_service.application.dto.RecommendationMlResponse;
import com.mrs.recommendation_service.domain.exception.user_profile.UserProfileNotFoundException;
import com.mrs.recommendation_service.domain.model.MediaFeature;
import com.mrs.recommendation_service.domain.model.Recommendation;
import com.mrs.recommendation_service.domain.model.UserProfile;
import com.mrs.recommendation_service.domain.port.MediaFeatureRepository;
import com.mrs.recommendation_service.domain.port.UserProfileRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

@Component
public class GetRecommendationsHandler {

    private final UserProfileRepository userProfileRepository;
    private final MediaFeatureRepository mediaFeatureRepository;

    public GetRecommendationsHandler(UserProfileRepository userProfileRepository, RestClient restClient, MediaFeatureRepository mediaFeatureRepository) {
        this.userProfileRepository = userProfileRepository;
        this.mediaFeatureRepository = mediaFeatureRepository;
    }

    public List<UUID> execute(UUID userId, int limit, int offset) {
        return mediaFeatureRepository.findAllByUserId(userId, limit, offset);
    }

}

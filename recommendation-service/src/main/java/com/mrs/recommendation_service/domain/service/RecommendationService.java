package com.mrs.recommendation_service.domain.service;

import com.mrs.recommendation_service.domain.handler.media_feature.GetRecommendationsHandler;
import com.mrs.recommendation_service.domain.model.Recommendation;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class RecommendationService {

    private final GetRecommendationsHandler getRecommendationsHandler;

    public RecommendationService(GetRecommendationsHandler getRecommendationsHandler) {
        this.getRecommendationsHandler = getRecommendationsHandler;
    }

    public List<Recommendation> get(UUID userId){
        return getRecommendationsHandler.execute(userId);
    }

}

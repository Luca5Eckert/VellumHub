package com.mrs.recommendation_service.domain.handler.media_feature;

import com.mrs.recommendation_service.application.dto.MediaFeatureResponse;
import com.mrs.recommendation_service.domain.port.CatalogClient;
import com.mrs.recommendation_service.domain.port.MediaFeatureRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class GetRecommendationsHandler {

    private final MediaFeatureRepository mediaFeatureRepository;

    private final CatalogClient client;

    public GetRecommendationsHandler(MediaFeatureRepository mediaFeatureRepository, CatalogClient client) {
        this.mediaFeatureRepository = mediaFeatureRepository;
        this.client = client;
    }


    public List<MediaFeatureResponse> execute(UUID userId, int limit, int offset) {
        List<UUID> mediasIds = mediaFeatureRepository.findAllByUserId(userId, limit, offset);

        if(mediasIds == null || mediasIds.isEmpty()){
            mediasIds = mediaFeatureRepository.findMostPopularMedias(limit, offset);
        }

        return client.fetchMediaBatch(mediasIds);
    }

}

package com.mrs.recommendation_service.module.book_feature.domain.handler.book_feature;

import com.mrs.recommendation_service.module.book_feature.application.dto.BookFeatureResponse;
import com.mrs.recommendation_service.module.book_feature.domain.port.BookFeatureRepository;
import com.mrs.recommendation_service.module.book_feature.domain.port.CatalogClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class GetRecommendationsHandler {

    private final BookFeatureRepository bookFeatureRepository;

    private final CatalogClient client;

    public GetRecommendationsHandler(BookFeatureRepository bookFeatureRepository, CatalogClient client) {
        this.bookFeatureRepository = bookFeatureRepository;
        this.client = client;
    }


    public List<BookFeatureResponse> execute(UUID userId, int limit, int offset) {
        List<UUID> mediasIds = bookFeatureRepository.findAllByUserId(userId, limit, offset);

        if(mediasIds == null || mediasIds.isEmpty()){
            mediasIds = bookFeatureRepository.findMostPopularMedias(limit, offset);
        }

        return client.fetchMediaBatch(mediasIds);
    }

}

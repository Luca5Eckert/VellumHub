package com.mrs.recommendation_service.module.recommendation.domain.use_case;

import com.mrs.recommendation_service.module.book_feature.domain.port.BookFeatureRepository;
import com.mrs.recommendation_service.module.book_feature.domain.port.CatalogClient;
import com.mrs.recommendation_service.module.recommendation.domain.command.GetRecommendationsCommand;
import com.mrs.recommendation_service.module.recommendation.domain.model.Recommendation;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class GetRecommendationsUseCase {

    private final BookFeatureRepository bookFeatureRepository;

    private final CatalogClient client;

    public GetRecommendationsUseCase(BookFeatureRepository bookFeatureRepository, CatalogClient client) {
        this.bookFeatureRepository = bookFeatureRepository;
        this.client = client;
    }


    public List<Recommendation> execute(GetRecommendationsCommand command) {
        List<UUID> mediasIds = bookFeatureRepository.findAllByUserId(
                command.userId(),
                command.limit(),
                command.offset()
        );

        if(mediasIds == null || mediasIds.isEmpty()){
            mediasIds = bookFeatureRepository.findMostPopularMedias(command.limit(), command.offset());
        }

        return client.fetchRecommendationsBatch(mediasIds);
    }

}

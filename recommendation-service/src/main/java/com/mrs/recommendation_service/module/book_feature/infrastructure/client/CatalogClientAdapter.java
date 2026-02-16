package com.mrs.recommendation_service.module.book_feature.infrastructure.client;

import com.mrs.recommendation_service.module.book_feature.domain.port.CatalogClient;
import com.mrs.recommendation_service.module.recommendation.domain.model.Recommendation;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "catalog-service")
public interface CatalogClientAdapter extends CatalogClient {

    @PostMapping("/api/book/bulk")
    List<Recommendation> fetchRecommendationsBatch(@RequestBody List<UUID> ids);

}
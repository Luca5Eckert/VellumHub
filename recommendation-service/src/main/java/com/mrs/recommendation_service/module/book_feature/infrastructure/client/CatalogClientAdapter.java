package com.mrs.recommendation_service.module.book_feature.infrastructure.client;

import com.mrs.recommendation_service.module.book_feature.domain.port.CatalogClient;
import com.mrs.recommendation_service.module.recommendation.domain.model.Recommendation;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "catalog-service", url = "${catalog-service.url}")
public interface CatalogClientAdapter extends CatalogClient {

    @GetMapping("/books/bulk")
    List<Recommendation> fetchRecommendationsBatch(@RequestBody List<UUID> ids);

}
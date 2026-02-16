package com.mrs.recommendation_service.module.book_feature.infrastructure.client;

import com.mrs.recommendation_service.module.book_feature.application.dto.BookFeatureResponse;
import com.mrs.recommendation_service.module.book_feature.domain.port.CatalogClient;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "catalog-service")
public interface CatalogClientAdapter extends CatalogClient {

    @PostMapping("/api/book/bulk")
    List<BookFeatureResponse> fetchRecommendationsBatch(@RequestBody List<UUID> ids);

}
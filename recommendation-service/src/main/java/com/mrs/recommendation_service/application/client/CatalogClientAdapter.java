package com.mrs.recommendation_service.application.client;

import com.mrs.recommendation_service.application.dto.MediaFeatureResponse;
import com.mrs.recommendation_service.domain.port.CatalogClient;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "catalog-service")
public interface CatalogClientAdapter extends CatalogClient {

    @PostMapping("/api/media/bulk")
    List<MediaFeatureResponse> fetchMediaBatch(@RequestBody List<UUID> ids);

}
package com.mrs.recommendation_service.domain.port;

import com.mrs.recommendation_service.application.dto.MediaFeatureResponse;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.UUID;

public interface CatalogClient {

    List<MediaFeatureResponse> fetchMediaBatch(@RequestBody List<UUID> ids);

}

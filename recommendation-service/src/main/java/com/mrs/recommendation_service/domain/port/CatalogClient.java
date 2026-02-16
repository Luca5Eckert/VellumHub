package com.mrs.recommendation_service.domain.port;

import com.mrs.recommendation_service.application.dto.BookFeatureResponse;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.UUID;

public interface CatalogClient {

    List<BookFeatureResponse> fetchMediaBatch(@RequestBody List<UUID> ids);

}

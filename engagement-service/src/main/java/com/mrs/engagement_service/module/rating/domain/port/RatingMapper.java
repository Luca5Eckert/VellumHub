package com.mrs.engagement_service.module.rating.domain.port;

import com.mrs.engagement_service.module.rating.application.dto.GetMediaStatusResponse;
import com.mrs.engagement_service.module.rating.application.dto.RatingGetResponse;
import com.mrs.engagement_service.module.rating.domain.model.EngagementStats;
import com.mrs.engagement_service.module.book_progress.domain.model.Rating;

import java.util.UUID;

public interface RatingMapper {

    RatingGetResponse toGetResponse(Rating rating);

    GetMediaStatusResponse toMediaStatusResponse(EngagementStats engagementStats, UUID mediaId);

}

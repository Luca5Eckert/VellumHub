package com.mrs.engagement_service.domain.port;

import com.mrs.engagement_service.application.dto.GetMediaStatusResponse;
import com.mrs.engagement_service.application.dto.RatingGetResponse;
import com.mrs.engagement_service.domain.model.EngagementStats;
import com.mrs.engagement_service.domain.model.Rating;

import java.util.UUID;

public interface RatingMapper {

    RatingGetResponse toGetResponse(Rating rating);

    GetMediaStatusResponse toMediaStatusResponse(EngagementStats engagementStats, UUID mediaId);

}
